package com.menemen.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/**
 * Chunk tarama dongusu: her tickte butce kadar chunk tarar, en eski taranani once secer,
 * menzil disina cikan chunklari unutturur.
 */
public class ScanLoop {
    private final Map<Long, Integer> lastScan = new HashMap<>();
    private int tickCounter;

    public void reset() {
        lastScan.clear();
        tickCounter = 0;
    }

    /** Bu tickte taranan chunk sayisini dondurur. */
    public int tick(MinecraftClient mc, int radius, int perTick, int rescan,
                    LongConsumer forget, BiConsumer<ChunkPos, Chunk> process) {
        if (mc.world == null || mc.player == null) return 0;
        tickCounter++;

        ChunkPos center = mc.player.getChunkPos();

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

        List<ChunkPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                Integer last = lastScan.get(ChunkPos.toLong(cx, cz));
                if (last == null || tickCounter - last >= rescan) {
                    candidates.add(new ChunkPos(cx, cz));
                }
            }
        }

        candidates.sort(Comparator.comparingInt(c -> lastScan.getOrDefault(c.toLong(), Integer.MIN_VALUE)));

        int budget = perTick;
        int done = 0;
        for (ChunkPos cp : candidates) {
            if (budget-- <= 0) break;
            lastScan.put(cp.toLong(), tickCounter);
            Chunk chunk = mc.world.getChunk(cp.x, cp.z);
            if (chunk == null) continue;
            process.accept(cp, chunk);
            done++;
        }
        return done;
    }
}
