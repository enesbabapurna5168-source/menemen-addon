package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BaseBlocks;
import com.menemen.addon.utils.BlockScan;
import com.menemen.addon.utils.ScanLoop;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/**
 * Chunk Finder: icinde secilen bloklardan yeterince cok bulunan chunklari kutu ile isaretler.
 * Hangi chunkta yapi oldugunu tek bakista gormek icin.
 */
public class ChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("bloklar")
        .description("Sayilacak bloklar.")
        .defaultValue(BaseBlocks.all())
        .build()
    );

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("esik")
        .description("Bir chunkun isaretlenmesi icin en az blok sayisi.")
        .defaultValue(5)
        .range(1, 500)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .defaultValue(320)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-yaricapi")
        .defaultValue(10)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("tick-basina-chunk")
        .defaultValue(2)
        .range(1, 16)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> rescanTicks = sgGeneral.add(new IntSetting.Builder()
        .name("yeniden-tarama")
        .defaultValue(300)
        .range(20, 2400)
        .sliderRange(20, 1200)
        .build()
    );

    private final Setting<Boolean> alert = sgGeneral.add(new BoolSetting.Builder()
        .name("sohbet-uyarisi")
        .description("Yeni isaretlenen chunk icin sohbette koordinat yaz.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("sekil")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("yuzey-rengi")
        .defaultValue(new SettingColor(50, 170, 255, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi")
        .defaultValue(new SettingColor(50, 170, 255, 255))
        .build()
    );

    private record Hit(int x0, int z0, int minY, int maxY, int count) {}

    private final Map<Long, Hit> hits = new HashMap<>();
    private final Set<Long> alerted = new HashSet<>();
    private final ScanLoop loop = new ScanLoop();

    public ChunkFinder() {
        super(MenemenAddon.CATEGORY, "chunk-finder",
            "Secilen bloklardan cok iceren chunklari kutu ile isaretler.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        loop.reset();
        hits.clear();
        alerted.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), rescanTicks.get(),
            key -> {
                hits.remove(key);
                alerted.remove(key);
            },
            (cp, chunk) -> {
                Map<Block, Integer> weights = new HashMap<>();
                for (Block b : blocks.get()) weights.put(b, 1);

                int lo = Math.min(minY.get(), maxY.get());
                int hi = Math.max(minY.get(), maxY.get());
                BlockScan.Result r = BlockScan.scan(chunk, cp, weights, lo, hi);

                long key = cp.toLong();
                if (r.count >= threshold.get()) {
                    hits.put(key, new Hit(cp.getStartX(), cp.getStartZ(), r.minY, r.maxY, r.count));
                    if (alert.get() && alerted.add(key)) {
                        info("Chunk (%d, %d): %d blok, Y %d..%d", cp.getStartX(), cp.getStartZ(), r.count, r.minY, r.maxY);
                    }
                } else {
                    hits.remove(key);
                }
            }
        );
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Hit h : hits.values()) {
            event.renderer.box(
                h.x0(), h.minY(), h.z0(),
                h.x0() + 16, h.maxY() + 1, h.z0() + 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(hits.size());
    }
}
