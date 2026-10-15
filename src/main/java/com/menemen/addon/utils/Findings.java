package com.menemen.addon.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulucu modullerin bulgularini ortak bir yerde toplar. Region Map bu bilgiyi okuyup
 * bolge (512x512) bazinda ozet cikarir. Her modul chunk -> puan haritasi verir.
 */
public final class Findings {
    private Findings() {}

    public interface Source {
        Map<Long, Integer> chunkScores();
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    public static void register(Source source) {
        if (!SOURCES.contains(source)) SOURCES.add(source);
    }

    /** Tum kaynaklarin birlesimi; ayni chunk icin en yuksek puan alinir. */
    public static Map<Long, Integer> merged() {
        Map<Long, Integer> out = new HashMap<>();
        for (Source source : SOURCES) {
            for (Map.Entry<Long, Integer> e : source.chunkScores().entrySet()) {
                out.merge(e.getKey(), e.getValue(), Integer::max);
            }
        }
        return out;
    }
}
