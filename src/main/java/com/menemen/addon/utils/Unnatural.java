package com.menemen.addon.utils;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Oyuncu yapimi base'lerde gorulen bloklari blok kayit defterinden isim kaliplariyla bulur
 * (planks, stairs, concrete, shulker box, bed vb.). Elle liste yazmaktan cok daha kapsamlidir.
 *
 * Not: koy, magara ve yapilar (mineshaft, stronghold, ancient city, bastion) da bu bloklardan
 * dogal olarak uretir; bu yuzden tek basina bir blok kesin kanit degildir.
 */
public final class Unnatural {
    private Unnatural() {}

    public enum Cat { DEPO, ISLEV, REDSTONE, ISIK, DEGERLI, YAPI }

    private static final Set<String> ISLEV_NAMES = Set.of(
        "furnace", "blast_furnace", "smoker", "hopper", "dispenser", "dropper", "crafting_table",
        "enchanting_table", "anvil", "chipped_anvil", "damaged_anvil", "beacon", "brewing_stand",
        "jukebox", "lectern", "grindstone", "smithing_table", "cartography_table", "fletching_table",
        "loom", "stonecutter", "respawn_anchor", "lodestone", "conduit"
    );

    private static final Set<String> REDSTONE_NAMES = Set.of(
        "observer", "piston", "sticky_piston", "redstone_lamp", "repeater", "comparator",
        "daylight_detector", "target", "note_block", "tnt", "crafter", "slime_block", "honey_block"
    );

    private static final Set<String> LIGHT_NAMES = Set.of(
        "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch",
        "lantern", "soul_lantern"
    );

    private static final Set<String> VALUABLE_NAMES = Set.of(
        "netherite_block", "diamond_block", "emerald_block", "gold_block", "iron_block",
        "lapis_block", "redstone_block", "coal_block"
    );

    private static final String[] BUILD_SUFFIXES = {
        "_planks", "_stairs", "_slab", "_fence", "_fence_gate", "_door", "_trapdoor", "_wool", "_carpet",
        "_concrete", "_concrete_powder", "_glazed_terracotta", "_stained_glass", "_stained_glass_pane",
        "_sign", "_wall_sign", "_hanging_sign", "_banner", "_wall_banner"
    };

    private static final Set<String> BUILD_NAMES = Set.of(
        "glass", "glass_pane", "bookshelf", "chiseled_bookshelf", "scaffolding", "ladder"
    );

    private static final Map<Block, Cat> CATS = new HashMap<>();
    private static final Map<Block, Integer> WEIGHTS = new HashMap<>();

    static {
        for (Block block : Registries.BLOCK) {
            String name = Registries.BLOCK.getId(block).getPath();
            Cat cat = classify(name);
            if (cat == null) continue;
            CATS.put(block, cat);
            WEIGHTS.put(block, weight(cat, name));
        }
    }

    private static Cat classify(String n) {
        if (n.equals("chest") || n.equals("trapped_chest") || n.equals("barrel")
            || n.equals("ender_chest") || n.endsWith("shulker_box")) return Cat.DEPO;
        if (ISLEV_NAMES.contains(n) || n.endsWith("_bed")) return Cat.ISLEV;
        if (REDSTONE_NAMES.contains(n)) return Cat.REDSTONE;
        if (LIGHT_NAMES.contains(n)) return Cat.ISIK;
        if (VALUABLE_NAMES.contains(n)) return Cat.DEGERLI;

        if (BUILD_NAMES.contains(n)) return Cat.YAPI;
        for (String suffix : BUILD_SUFFIXES) {
            if (n.endsWith(suffix)) return Cat.YAPI;
        }
        if (n.contains("stripped_") || n.contains("polished_") || n.contains("bricks")) return Cat.YAPI;
        return null;
    }

    private static int weight(Cat cat, String n) {
        return switch (cat) {
            case DEPO, ISLEV -> 3;
            case REDSTONE, DEGERLI -> 2;
            case ISIK -> 1;
            case YAPI -> (n.contains("concrete") || n.contains("glazed_terracotta")) ? 2 : 1;
        };
    }

    private static final Map<Set<Cat>, Map<Block, Integer>> CACHE = new HashMap<>();

    /**
     * Verilen kategorilerdeki tum bloklarin agirlik haritasi. Sonuc onbellege alinir ve degistirilemez
     * (modullerin her tickte yeniden olusturmasi gerekmez).
     */
    public static Map<Block, Integer> weights(Set<Cat> enabled) {
        EnumSet<Cat> key = EnumSet.noneOf(Cat.class);
        key.addAll(enabled);

        Map<Block, Integer> cached = CACHE.get(key);
        if (cached != null) return cached;

        Map<Block, Integer> out = new HashMap<>();
        for (Map.Entry<Block, Cat> e : CATS.entrySet()) {
            if (key.contains(e.getValue())) out.put(e.getKey(), WEIGHTS.get(e.getKey()));
        }
        Map<Block, Integer> result = Collections.unmodifiableMap(out);
        CACHE.put(key, result);
        return result;
    }

    public static Cat categoryOf(Block block) {
        return CATS.get(block);
    }
}
