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
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Base bulmak icin ESP. Yuklu chunklardaki oyuncu yapimi bloklari secilen Y araliginda
 * (varsayilan -64 .. 320) vurgular. En yakin chunklardan baslayip disa dogru tarar.
 *
 * Tamamen istemci tarafinda calisir: sunucuya hicbir paket gondermez, sadece
 * zaten yuklenmis chunk verisini okuyup cizer.
 */
public class BaseFinderESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgAlert = settings.createGroup("Uyari");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("bloklar")
        .description("Vurgulanacak bloklar.")
        .defaultValue(BaseBlocks.all())
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Taranacak en dusuk Y seviyesi.")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Taranacak en yuksek Y seviyesi.")
        .defaultValue(320)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-yaricapi")
        .description("Oyuncu etrafinda taranacak chunk yaricapi. 32, yuklenebilecek her yeri kapsar.")
        .defaultValue(32)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("tick-basina-chunk")
        .description("Her tickte en fazla taranacak chunk sayisi. Sure siniri (4 ms) ayrica FPS'i korur.")
        .defaultValue(16)
        .range(1, 64)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> budgetMs = sgGeneral.add(new IntSetting.Builder()
        .name("tarama-butcesi-ms")
        .description("Her tickte taramaya ayrilan en fazla sure (ms). Yuksek deger hizli tarar ama FPS dusurebilir.")
        .defaultValue(4)
        .range(1, 20)
        .sliderRange(1, 12)
        .build()
    );

    private final Setting<Integer> rescanTicks = sgGeneral.add(new IntSetting.Builder()
        .name("yeniden-tarama")
        .description("Bir chunk kac tickte bir tekrar taransin (blok degisiklikleri icin).")
        .defaultValue(200)
        .range(20, 2400)
        .sliderRange(20, 1200)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("sekil")
        .description("Kutularin cizim sekli.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("yuzey-rengi")
        .defaultValue(new SettingColor(255, 170, 0, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi")
        .defaultValue(new SettingColor(255, 170, 0, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracer")
        .description("Bulunan bloklara cizgi cek.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxRender = sgRender.add(new IntSetting.Builder()
        .name("maks-cizim")
        .description("Ayni anda cizilecek en fazla blok sayisi.")
        .defaultValue(3000)
        .range(100, 20000)
        .sliderRange(100, 10000)
        .build()
    );

    private final Setting<Boolean> alert = sgAlert.add(new BoolSetting.Builder()
        .name("kume-uyarisi")
        .description("Bir chunkta cok sayida blok bulununca sohbette haber ver.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> alertThreshold = sgAlert.add(new IntSetting.Builder()
        .name("esik")
        .description("Uyari icin bir chunktaki en az blok sayisi.")
        .defaultValue(10)
        .range(2, 200)
        .sliderRange(2, 100)
        .visible(alert::get)
        .build()
    );

    private final Map<Long, List<BlockPos>> found = new HashMap<>();
    private final Set<Long> alerted = new HashSet<>();
    private final ScanLoop loop = new ScanLoop();

    public BaseFinderESP() {
        super(MenemenAddon.CATEGORY, "base-finder-esp",
            "Yuklu chunklardaki oyuncu yapimi bloklari secilen Y araliginda (varsayilan -64'e kadar) vurgular.");
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
        found.clear();
        alerted.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Map<Block, Integer> weights = new HashMap<>();
        for (Block b : blocks.get()) weights.put(b, 1);

        int lo = Math.min(minY.get(), maxY.get());
        int hi = Math.max(minY.get(), maxY.get());

        loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), rescanTicks.get(), budgetMs.get(),
            key -> {
                found.remove(key);
                alerted.remove(key);
            },
            (cp, chunk) -> {
                BlockScan.Result r = BlockScan.scan(chunk, cp, weights, lo, hi);

                long key = cp.toLong();
                if (r.positions.isEmpty()) found.remove(key);
                else found.put(key, r.positions);

                if (alert.get() && r.count >= alertThreshold.get() && alerted.add(key)) {
                    info("Olasi base: chunk (%d, %d) icinde %d blok.", cp.x, cp.z, r.count);
                }
            }
        );
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (found.isEmpty()) return;

        Color side = sideColor.get();
        Color line = lineColor.get();
        int drawn = 0;
        int limit = maxRender.get();

        for (List<BlockPos> list : found.values()) {
            for (BlockPos pos : list) {
                if (drawn++ >= limit) return;
                event.renderer.box(pos, side, line, shapeMode.get(), 0);
                if (tracers.get()) {
                    event.renderer.line(
                        RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        line
                    );
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        int total = 0;
        for (List<BlockPos> list : found.values()) total += list.size();
        return String.valueOf(total);
    }
}
