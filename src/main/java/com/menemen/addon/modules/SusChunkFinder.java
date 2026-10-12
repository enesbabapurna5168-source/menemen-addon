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
import net.minecraft.block.Blocks;

import java.util.*;

/**
 * Sus Chunk Finder: dogal olmayan yapilarin izlerine puan verir. Magaralarda dogal olarak
 * bulunmayan meshale/fener, depolama ve uretim bloklari, piston/observer gibi redstone parcalari
 * agirlikli puanlanir. Puani esigi gecen chunk kirmizi kutu ile isaretlenir.
 */
public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("puan-esigi")
        .description("Bir chunkun supheli sayilmasi icin en az puan.")
        .defaultValue(8)
        .range(1, 500)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> undergroundMaxY = sgGeneral.add(new IntSetting.Builder()
        .name("yeralti-max-y")
        .description("Meshale ve fener sadece bu Y seviyesinin altinda sayilir (magara icinde dogal olmadiklari icin).")
        .defaultValue(40)
        .range(-64, 320)
        .sliderRange(-64, 128)
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
        .defaultValue(32)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("tick-basina-chunk")
        .defaultValue(16)
        .range(1, 64)
        .sliderRange(1, 32)
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
        .description("Yeni supheli chunk icin sohbette koordinat ve puan yaz.")
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
        .defaultValue(new SettingColor(255, 50, 50, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi")
        .defaultValue(new SettingColor(255, 50, 50, 255))
        .build()
    );

    private record Hit(int x0, int z0, int minY, int maxY, int score) {}

    private final Map<Long, Hit> hits = new HashMap<>();
    private final Set<Long> alerted = new HashSet<>();
    private final ScanLoop loop = new ScanLoop();

    private final Map<Block, Integer> lightWeights = new HashMap<>();
    private final Map<Block, Integer> otherWeights = new HashMap<>();

    public SusChunkFinder() {
        super(MenemenAddon.CATEGORY, "sus-chunk-finder",
            "Dogal olmayan yapi izleri tasiyan supheli chunklari puanlayip isaretler.");

        for (Block b : BaseBlocks.LIGHT) lightWeights.put(b, 2);
        for (Block b : BaseBlocks.STORAGE) otherWeights.put(b, 3);
        for (Block b : BaseBlocks.UTILITY) otherWeights.put(b, 3);
        for (Block b : BaseBlocks.REDSTONE) otherWeights.put(b, 2);
        otherWeights.put(Blocks.GLASS, 1);
        otherWeights.put(Blocks.GLASS_PANE, 1);
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
                int lo = Math.min(minY.get(), maxY.get());
                int hi = Math.max(minY.get(), maxY.get());

                BlockScan.Result r = BlockScan.scan(chunk, cp, otherWeights, lo, hi);
                r.add(BlockScan.scan(chunk, cp, lightWeights, lo, Math.min(hi, undergroundMaxY.get())));

                long key = cp.toLong();
                if (r.score >= threshold.get()) {
                    hits.put(key, new Hit(cp.getStartX(), cp.getStartZ(), r.minY, r.maxY, r.score));
                    if (alert.get() && alerted.add(key)) {
                        info("Supheli chunk (%d, %d): puan %d, Y %d..%d", cp.getStartX(), cp.getStartZ(), r.score, r.minY, r.maxY);
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
