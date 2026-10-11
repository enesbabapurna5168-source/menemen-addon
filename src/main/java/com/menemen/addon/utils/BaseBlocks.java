package com.menemen.addon.utils;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/** Oyuncu yapimi base'lerde sik gorulen bloklar. */
public final class BaseBlocks {
    private BaseBlocks() {}

    public static final Block[] STORAGE = {
        Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST,
        Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
        Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX,
        Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX,
        Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX,
        Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX
    };

    public static final Block[] UTILITY = {
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
        Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER,
        Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.BEACON,
        Blocks.BREWING_STAND, Blocks.JUKEBOX, Blocks.LECTERN,
        Blocks.WHITE_BED, Blocks.RED_BED, Blocks.BLACK_BED, Blocks.BLUE_BED
    };

    public static final Block[] LIGHT = {
        Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
        Blocks.LANTERN, Blocks.SOUL_LANTERN
    };

    public static final Block[] REDSTONE = {
        Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.REDSTONE_LAMP
    };

    public static Block[] all() {
        Block[][] groups = {STORAGE, UTILITY, LIGHT, REDSTONE};
        int total = 0;
        for (Block[] g : groups) total += g.length;
        Block[] out = new Block[total];
        int i = 0;
        for (Block[] g : groups) {
            for (Block b : g) out[i++] = b;
        }
        return out;
    }
}
