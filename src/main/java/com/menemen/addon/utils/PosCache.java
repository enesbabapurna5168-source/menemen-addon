package com.menemen.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Cizilecek bloklari oyuncuya yakindan uzaga siralar. Cizim limiti dolunca rastgele bloklar degil,
 * en yakin bloklar cizilir (eskiden limit, HashMap sirasina gore rastgele bloklari kesiyordu).
 */
public class PosCache {
    private static final long REBUILD_MS = 400;

    private final List<BlockPos> sorted = new ArrayList<>();
    private long lastBuild;
    private boolean lastDeep;

    public void clear() {
        sorted.clear();
        lastBuild = 0;
    }

    public List<BlockPos> nearest(MinecraftClient mc, Collection<List<BlockPos>> source, int limit) {
        return nearest(mc, source, limit, false);
    }

    /**
     * @param deepFirst true ise en dusuk Y (bedrocka yakin) bloklar once cizilir, limit dolarsa
     *                  yuzeye yakin bloklar kesilir.
     */
    public List<BlockPos> nearest(MinecraftClient mc, Collection<List<BlockPos>> source, int limit, boolean deepFirst) {
        long now = System.currentTimeMillis();
        if (now - lastBuild > REBUILD_MS || deepFirst != lastDeep) {
            lastBuild = now;
            lastDeep = deepFirst;
            sorted.clear();
            for (List<BlockPos> list : source) sorted.addAll(list);

            if (deepFirst) {
                final int ppx = mc.player != null ? mc.player.getBlockX() : 0;
                final int ppz = mc.player != null ? mc.player.getBlockZ() : 0;
                sorted.sort(Comparator.comparingInt((BlockPos p) -> p.getY())
                    .thenComparingLong(p -> {
                        long dx = p.getX() - ppx;
                        long dz = p.getZ() - ppz;
                        return dx * dx + dz * dz;
                    }));
            } else if (mc.player != null) {
                final int px = mc.player.getBlockX();
                final int py = mc.player.getBlockY();
                final int pz = mc.player.getBlockZ();
                sorted.sort(Comparator.comparingLong((BlockPos p) -> {
                    long dx = p.getX() - px;
                    long dy = p.getY() - py;
                    long dz = p.getZ() - pz;
                    return dx * dx + dy * dy + dz * dz;
                }));
            }
        }
        return sorted.size() <= limit ? sorted : sorted.subList(0, limit);
    }
}
