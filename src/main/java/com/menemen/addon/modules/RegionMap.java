package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.Findings;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/**
 * Region Map: dunyayi 512x512 bloklUk bolgelere (region, r.x.z dosyalari) ayirir.
 * - Oyuncunun cevresindeki bolgelerin sinirlarini cizer; bulgu olan bolgeler farkli renkte.
 * - Diger bulucu modullerin (Chunk Finder, Sus Chunk Finder, Prime, Menemen Finder, Netherite)
 *   bulgularini bolge bazinda toplayip sohbete ozet yazar: hangi bolgede ne kadar iz var.
 */
public class RegionMap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> ring = sgGeneral.add(new IntSetting.Builder()
        .name("halka")
        .description("Oyuncunun bolgesinin etrafinda kac halka bolge cizilsin (1 = 3x3 bolge).")
        .defaultValue(1)
        .range(0, 4)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Boolean> summary = sgGeneral.add(new BoolSetting.Builder()
        .name("ozet-yaz")
        .description("Bolge bazli bulgu ozetini sohbete yaz (sadece degisince).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> summaryInterval = sgGeneral.add(new IntSetting.Builder()
        .name("ozet-araligi")
        .description("Ozet kac tickte bir kontrol edilsin (20 tick = 1 saniye).")
        .defaultValue(400)
        .range(40, 6000)
        .sliderRange(40, 1200)
        .visible(summary::get)
        .build()
    );

    private final Setting<Integer> summaryCount = sgGeneral.add(new IntSetting.Builder()
        .name("ozet-satiri")
        .description("Ozette gosterilecek en iyi bolge sayisi.")
        .defaultValue(5)
        .range(1, 15)
        .sliderRange(1, 10)
        .visible(summary::get)
        .build()
    );

    private final Setting<Boolean> borders = sgRender.add(new BoolSetting.Builder()
        .name("bolge-sinirlari")
        .description("Bolge sinirlarini dunyada ciz.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> emptyColor = sgRender.add(new ColorSetting.Builder()
        .name("bos-bolge-rengi")
        .defaultValue(new SettingColor(130, 130, 130, 160))
        .build()
    );

    private final Setting<SettingColor> hitColor = sgRender.add(new ColorSetting.Builder()
        .name("bulgulu-bolge-rengi")
        .defaultValue(new SettingColor(255, 80, 80, 255))
        .build()
    );

    private Map<Long, int[]> totals = new HashMap<>();
    private int ticks;
    private String lastSignature = "";

    public RegionMap() {
        super(MenemenAddon.CATEGORY, "region-map",
            "512x512 bolge sinirlarini cizer ve bulucu modullerin bulgularini bolge bazinda ozetler.");
    }

    @Override
    public void onActivate() {
        totals = new HashMap<>();
        ticks = 0;
        lastSignature = "";
    }

    private static long regionKey(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    private static int regionX(long key) {
        return (int) (key >> 32);
    }

    private static int regionZ(long key) {
        return (int) key;
    }

    private Map<Long, int[]> computeTotals() {
        Map<Long, int[]> out = new HashMap<>();
        for (Map.Entry<Long, Integer> e : Findings.merged().entrySet()) {
            int cx = ChunkPos.getPackedX(e.getKey());
            int cz = ChunkPos.getPackedZ(e.getKey());
            int[] t = out.computeIfAbsent(regionKey(cx >> 5, cz >> 5), k -> new int[2]);
            t[0] += e.getValue();
            t[1]++;
        }
        return out;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if (++ticks % 20 != 0) return;

        totals = computeTotals();

        if (!summary.get() || ticks < summaryInterval.get()) return;
        ticks = 0;

        List<Map.Entry<Long, int[]>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));

        StringBuilder sig = new StringBuilder();
        int shown = Math.min(summaryCount.get(), sorted.size());
        for (int i = 0; i < shown; i++) {
            Map.Entry<Long, int[]> e = sorted.get(i);
            sig.append(e.getKey()).append(':').append(e.getValue()[0]).append(':').append(e.getValue()[1]).append(';');
        }
        if (shown == 0 || sig.toString().equals(lastSignature)) return;
        lastSignature = sig.toString();

        info("Bolge ozeti (en iyi %d):", shown);
        for (int i = 0; i < shown; i++) {
            Map.Entry<Long, int[]> e = sorted.get(i);
            int rx = regionX(e.getKey());
            int rz = regionZ(e.getKey());
            info("r.%d.%d (X %d..%d, Z %d..%d): puan %d, %d chunk",
                rx, rz, rx * 512, rx * 512 + 511, rz * 512, rz * 512 + 511,
                e.getValue()[0], e.getValue()[1]);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!borders.get() || mc.player == null) return;

        int prx = Math.floorDiv(mc.player.getBlockX(), 512);
        int prz = Math.floorDiv(mc.player.getBlockZ(), 512);
        double y = mc.player.getY() + 0.1;
        int r = ring.get();

        for (int rx = prx - r; rx <= prx + r; rx++) {
            for (int rz = prz - r; rz <= prz + r; rz++) {
                double x0 = rx * 512.0;
                double x1 = x0 + 512.0;
                double z0 = rz * 512.0;
                double z1 = z0 + 512.0;

                Color c = totals.containsKey(regionKey(rx, rz)) ? hitColor.get() : emptyColor.get();
                event.renderer.line(x0, y, z0, x1, y, z0, c);
                event.renderer.line(x1, y, z0, x1, y, z1, c);
                event.renderer.line(x1, y, z1, x0, y, z1, c);
                event.renderer.line(x0, y, z1, x0, y, z0, c);

                // Oyuncunun kendi bolgesinin koselerine dikey direkler.
                if (rx == prx && rz == prz) {
                    event.renderer.line(x0, y - 40, z0, x0, y + 40, z0, c);
                    event.renderer.line(x1, y - 40, z0, x1, y + 40, z0, c);
                    event.renderer.line(x1, y - 40, z1, x1, y + 40, z1, c);
                    event.renderer.line(x0, y - 40, z1, x0, y + 40, z1, c);
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;
        int rx = Math.floorDiv(mc.player.getBlockX(), 512);
        int rz = Math.floorDiv(mc.player.getBlockZ(), 512);
        int[] t = totals.get(regionKey(rx, rz));
        return "r." + rx + "." + rz + (t == null ? "" : " (" + t[1] + ")");
    }
}
