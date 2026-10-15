package com.menemen.addon.utils;

import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.EnumMap;
import java.util.Map;

/** Blok kategorisine gore ESP renkleri. */
public final class CatColors {
    private CatColors() {}

    private static final Map<Unnatural.Cat, Color> SIDE = new EnumMap<>(Unnatural.Cat.class);
    private static final Map<Unnatural.Cat, Color> LINE = new EnumMap<>(Unnatural.Cat.class);

    static {
        put(Unnatural.Cat.DEPO, 255, 170, 0);        // turuncu
        put(Unnatural.Cat.ISLEV, 0, 200, 255);       // acik mavi
        put(Unnatural.Cat.REDSTONE, 255, 60, 60);    // kirmizi
        put(Unnatural.Cat.DEGERLI, 255, 0, 200);     // pembe
        put(Unnatural.Cat.ISIK, 255, 255, 150);      // sari
        put(Unnatural.Cat.YAPI, 170, 170, 170);      // gri
    }

    private static void put(Unnatural.Cat cat, int r, int g, int b) {
        SIDE.put(cat, new Color(r, g, b, 45));
        LINE.put(cat, new Color(r, g, b, 255));
    }

    public static Color side(Unnatural.Cat cat) {
        return SIDE.get(cat);
    }

    public static Color line(Unnatural.Cat cat) {
        return LINE.get(cat);
    }
}
