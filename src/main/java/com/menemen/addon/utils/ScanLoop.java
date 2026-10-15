package com.menemen.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/**
 * Chunk tarama dongusu.
 *
 * - Taranacak chunklar bir kuyrukta tutulur: once hic taranmamislar, hepsi icinde en yakin olan basta.
 * - Her tickte hem chunk sayisi hem sure (ms) siniri vardir, boylece FPS dusmez.
 * - Menzil disina cikan chunklar unutulur.
 *
 * Istemci sadece sunucunun gonderdigi (yuklu) chunklari bilir; yukleme mesafesini asan bolge taranamaz.
 */
public class ScanLoop {
    private static final int DEFAULT_BUDGET_MS = 4;
    private static final int REBUILD_INTERVAL = 10;

    /**
     * Tum modullerin bir tickte taramaya harcayabilecegi toplam sure (ms). Birden cok modul acikken
     * her biri bu sureyi paylasir, boylece FPS dusmez.
     */
    private static final int GLOBAL_BUDGET_MS = 12;
    private static long windowId = -1;
    private static int callsThisWindow;
    private static int callsPrevWindow;

    private final Map<Long, Integer> lastScan = new HashMap<>();
    private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
    private int tickCounter;
    private int loadedCount;

    public void reset() {
        lastScan.clear();
        queue.clear();
        tickCounter = 0;
        loadedCount = 0;
    }

    /** Menzil icinde su ana kadar en az bir kez taranmis chunk sayisi. */
    public int scannedCount() {
        return lastScan.size();
    }

    /** Menzil icinde sunucudan yuklenmis chunk sayisi (taranabilecek en fazla chunk). */
    public int loadedCount() {
        return loadedCount;
    }

    public int tick(MinecraftClient mc, int radius, int perTick, int rescan,
                    LongConsumer forget, BiConsumer<ChunkPos, Chunk> process) {
        return tick(mc, radius, perTick, rescan, DEFAULT_BUDGET_MS, forget, process);
    }

    /** Bu tickte taranan chunk sayisini dondurur. */
    public int tick(MinecraftClient mc, int radius, int perTick, int rescan, int budgetMs,
                    LongConsumer forget, BiConsumer<ChunkPos, Chunk> process) {
        if (mc.world == null || mc.player == null) return 0;
        tickCounter++;

        if (tickCounter % REBUILD_INTERVAL == 1) {
            rebuild(mc, mc.player.getChunkPos(), radius, rescan, forget);
        }

        // ~50 ms'lik pencerede kac modul tarama yapiyorsa butceyi onlara bol.
        long window = System.nanoTime() / 50_000_000L;
        if (window != windowId) {
            windowId = window;
            callsPrevWindow = callsThisWindow;
            callsThisWindow = 0;
        }
        callsThisWindow++;
        int active = Math.max(1, callsPrevWindow);
        int effectiveBudget = Math.max(1, Math.min(budgetMs, GLOBAL_BUDGET_MS / active));

        long deadline = System.nanoTime() + effectiveBudget * 1_000_000L;
        int done = 0;

        while (!queue.isEmpty() && done < perTick) {
            ChunkPos cp = queue.pollFirst();
            if (!mc.world.getChunkManager().isChunkLoaded(cp.x, cp.z)) continue;

            lastScan.put(cp.toLong(), tickCounter);
            Chunk chunk = mc.world.getChunk(cp.x, cp.z);
            if (chunk == null) continue;

            process.accept(cp, chunk);
            done++;

            if (System.nanoTime() >= deadline) break;
        }
        return done;
    }

    private void rebuild(MinecraftClient mc, ChunkPos center, int radius, int rescan, LongConsumer forget) {
        // Menzil disina cikanlari unut.
        Iterator<Long> keys = lastScan.keySet().iterator();
        while (keys.hasNext()) {
            long key = keys.next();
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (Math.abs(cx - center.x) > radius || Math.abs(cz - center.z) > radius) {
                keys.remove();
                forget.accept(key);
            }
        }

        // Taranmasi gerekenleri topla: hic taranmamis veya suresi dolmus.
        List<ChunkPos> candidates = new ArrayList<>();
        int loaded = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                loaded++;
                Integer last = lastScan.get(ChunkPos.toLong(cx, cz));
                if (last == null || tickCounter - last >= rescan) {
                    candidates.add(new ChunkPos(cx, cz));
                }
            }
        }

        loadedCount = loaded;

        // Once hic taranmamislar, sonra en yakin olanlar.
        candidates.sort(
            Comparator.comparingInt((ChunkPos c) -> lastScan.containsKey(c.toLong()) ? 1 : 0)
                .thenComparingInt(c -> {
                    int dx = c.x - center.x;
                    int dz = c.z - center.z;
                    return dx * dx + dz * dz;
                })
        );

        queue.clear();
        queue.addAll(candidates);
    }
}
