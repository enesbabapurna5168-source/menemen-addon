package com.menemen.addon.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Yuklu bir chunk icinde agirlikli blok taramasi yapar. Sadece okur, paket gondermez. */
public final class BlockScan {
    private BlockScan() {}

    public static final class Result {
        public int score;
        public int count;
        public int minY = Integer.MAX_VALUE;
        public int maxY = Integer.MIN_VALUE;
        public final List<BlockPos> positions = new ArrayList<>();

        public void add(Result other) {
            score += other.score;
            count += other.count;
            minY = Math.min(minY, other.minY);
            maxY = Math.max(maxY, other.maxY);
            positions.addAll(other.positions);
        }
    }

    public static Result scan(Chunk chunk, ChunkPos cp, Map<Block, Integer> weights, int lo, int hi) {
        Result result = new Result();
        if (weights.isEmpty() || lo > hi) return result;

        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            int baseY = (chunk.getBottomSectionCoord() + i) << 4;
            if (baseY + 15 < lo || baseY > hi) continue;
            if (!section.hasAny(state -> weights.containsKey(state.getBlock()))) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = baseY + y;
                    if (worldY < lo || worldY > hi) continue;
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Integer weight = weights.get(state.getBlock());
                        if (weight == null) continue;

                        result.score += weight;
                        result.count++;
                        result.minY = Math.min(result.minY, worldY);
                        result.maxY = Math.max(result.maxY, worldY);
                        result.positions.add(new BlockPos(cp.getStartX() + x, worldY, cp.getStartZ() + z));
                    }
                }
            }
        }
        return result;
    }
}
