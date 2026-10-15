package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BaseBlocks;
import com.menemen.addon.utils.BlockScan;
import com.menemen.addon.utils.PosCache;
import com.menemen.addon.utils.ScanLoop;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Menemen Debug: base bulma ESP'si + tarama istatistikleri. Kac chunk tarandi, kac blok bulundu,
 * son taramanin suresi gibi bilgileri sohbete yazar. ESP'nin neden bir sey gostermedigini
 * anlamak icin kullanisli.
 */
public class MenemenDebug extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("bloklar")
        .description("Vurgulanacak bloklar.")
        .defaultValue(BaseBlocks.all())
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

    private final Setting<Integer> budgetMs = sgGeneral.add(new IntSetting.Builder()
        .name("tarama-butcesi-ms")
        .defaultValue(6)
        .range(1, 20)
        .sliderRange(1, 12)
        .build()
    );

    private final Setting<Integer> rescanTicks = sgGeneral.add(new IntSetting.Builder()
        .name("yeniden-tarama")
        .defaultValue(200)
        .range(20, 2400)
        .sliderRange(20, 1200)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("sekil")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("yuzey-rengi")
        .defaultValue(new SettingColor(0, 255, 120, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi")
        .defaultValue(new SettingColor(0, 255, 120, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracer")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxRender = sgRender.add(new IntSetting.Builder()
        .name("maks-cizim")
        .defaultValue(3000)
        .range(100, 20000)
        .sliderRange(100, 10000)
        .build()
    );

    private final Setting<Boolean> chatDebug = sgDebug.add(new BoolSetting.Builder()
        .name("sohbet-istatistigi")
        .description("Tarama istatistiklerini sohbete yaz.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> debugInterval = sgDebug.add(new IntSetting.Builder()
        .name("istatistik-araligi")
        .description("Kac tickte bir istatistik yazilsin (20 tick = 1 saniye).")
        .defaultValue(200)
        .range(20, 2400)
        .sliderRange(20, 1200)
        .visible(chatDebug::get)
        .build()
    );

    private final Map<Long, List<BlockPos>> found = new HashMap<>();
    private final ScanLoop loop = new ScanLoop();
    private final PosCache cache = new PosCache();

    private long totalScanned;
    private long lastScanNanos;
    private int ticksSinceReport;

    public MenemenDebug() {
        super(MenemenAddon.CATEGORY, "menemen-debug",
            "Base bulma ESP'si ve tarama istatistikleri (debug).");
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
        cache.clear();
        found.clear();
        totalScanned = 0;
        lastScanNanos = 0;
        ticksSinceReport = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long start = System.nanoTime();

        Map<Block, Integer> weights = new HashMap<>();
        for (Block b : blocks.get()) weights.put(b, 1);

        int lo = Math.min(minY.get(), maxY.get());
        int hi = Math.max(minY.get(), maxY.get());

        int scanned = loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), rescanTicks.get(), budgetMs.get(),
            found::remove,
            (cp, chunk) -> {
                BlockScan.Result r = BlockScan.scan(chunk, cp, weights, lo, hi);

                long key = cp.toLong();
                if (r.positions.isEmpty()) found.remove(key);
                else found.put(key, r.positions);
            }
        );

        if (scanned > 0) {
            totalScanned += scanned;
            lastScanNanos = System.nanoTime() - start;
        }

        if (chatDebug.get() && ++ticksSinceReport >= debugInterval.get()) {
            ticksSinceReport = 0;
            info("[debug] chunk: %d | blok: %d | toplam tarama: %d | son tarama: %.2f ms | Y %d..%d",
                found.size(), countBlocks(), totalScanned, lastScanNanos / 1_000_000.0, lo, hi);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (found.isEmpty()) return;

        for (BlockPos pos : cache.nearest(mc, found.values(), maxRender.get())) {
            if (mc.world == null || mc.world.getBlockState(pos).isAir()) continue; // kirilan blogun kutusu kalmasin
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            if (tracers.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    lineColor.get()
                );
            }
        }
    }

    private int countBlocks() {
        int total = 0;
        for (List<BlockPos> list : found.values()) total += list.size();
        return total;
    }

    @Override
    public String getInfoString() {
        return String.valueOf(countBlocks());
    }
}
