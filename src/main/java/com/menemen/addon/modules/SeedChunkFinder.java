package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seed Chunk Finder: dunya seed'ine gore slime chunklarini hesaplayip gosterir (Java Edition,
 * sadece Overworld). Bu, seed'den guvenilir sekilde hesaplanabilen chunk bilgisidir.
 * Seed'i bilmeniz gerekir; Ayarlar'dan girin.
 */
public class SeedChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<String> seed = sgGeneral.add(new StringSetting.Builder()
        .name("seed")
        .description("Dunya seed'i (sayi).")
        .defaultValue("0")
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-yaricapi")
        .defaultValue(6)
        .range(1, 16)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("sekil")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("yuzey-rengi")
        .defaultValue(new SettingColor(120, 255, 90, 30))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi")
        .defaultValue(new SettingColor(120, 255, 90, 255))
        .build()
    );

    private final List<int[]> slime = new ArrayList<>();
    private int lastX = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;
    private int lastRadius = -1;
    private long lastSeed = Long.MIN_VALUE;

    public SeedChunkFinder() {
        super(MenemenAddon.CATEGORY, "seed-chunk-finder",
            "Girilen seed'e gore slime chunklarini gosterir (Overworld, Java).");
    }

    @Override
    public void onActivate() {
        slime.clear();
        lastX = Integer.MIN_VALUE;
        lastZ = Integer.MIN_VALUE;
        lastRadius = -1;
        lastSeed = Long.MIN_VALUE;
        if (parseSeed() == 0) info("Seed 0 olarak ayarli. Gercek seed'i ayarlardan girin.");
    }

    @Override
    public void onDeactivate() {
        slime.clear();
    }

    private long parseSeed() {
        try {
            return Long.parseLong(seed.get().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean isSlimeChunk(long worldSeed, int x, int z) {
        Random rnd = new Random(
            worldSeed
                + (long) (x * x * 4987142)
                + (long) (x * 5947611)
                + (long) (z * z) * 4392871L
                + (long) (z * 389711)
                ^ 987234911L
        );
        return rnd.nextInt(10) == 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        ChunkPos center = mc.player.getChunkPos();
        long s = parseSeed();
        int r = radius.get();

        if (center.x == lastX && center.z == lastZ && r == lastRadius && s == lastSeed) return;
        lastX = center.x;
        lastZ = center.z;
        lastRadius = r;
        lastSeed = s;

        slime.clear();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (isSlimeChunk(s, cx, cz)) slime.add(new int[]{cx * 16, cz * 16});
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        int y = mc.player.getBlockY();
        for (int[] c : slime) {
            event.renderer.box(
                c[0], y, c[1],
                c[0] + 16, y + 1, c[1] + 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(slime.size());
    }
}
