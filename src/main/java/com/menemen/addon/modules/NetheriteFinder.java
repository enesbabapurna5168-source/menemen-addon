package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BlockScan;
import com.menemen.addon.utils.Findings;
import com.menemen.addon.utils.PosCache;
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
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/**
 * Netherite Finder: yuklu chunklardaki ancient debris (ve istenirse netherite blogu) bulur.
 * Bir chunkta "damar esigi" kadar veya daha fazla varsa buyuk damar sayilir: farkli renkte cizilir
 * ve sohbette koordinati yazilir. Tum yuksekligi (en dipten tepeye) tarar.
 */
public class NetheriteFinder extends Module implements Findings.Source {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> includeBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("netherite-blogu")
        .description("Yerlestirilmis netherite bloklarini da bul (oyuncu depolari).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> veinThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("damar-esigi")
        .description("Bir chunkta bu kadar veya daha fazla bulunursa buyuk damar sayilir.")
        .defaultValue(3)
        .range(1, 64)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> fullHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("tum-yukseklik")
        .description("Y sinirini yok sayip tum katmanlari tara.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .defaultValue(0)
        .range(-64, 320)
        .sliderRange(0, 128)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .defaultValue(128)
        .range(-64, 320)
        .sliderRange(0, 128)
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

    private final Setting<Boolean> alert = sgGeneral.add(new BoolSetting.Builder()
        .name("sohbet-uyarisi")
        .description("Buyuk damar bulununca sohbete koordinat yaz.")
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
        .defaultValue(new SettingColor(190, 90, 255, 60))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi")
        .defaultValue(new SettingColor(190, 90, 255, 255))
        .build()
    );

    private final Setting<SettingColor> veinSideColor = sgRender.add(new ColorSetting.Builder()
        .name("damar-yuzey-rengi")
        .defaultValue(new SettingColor(255, 60, 200, 90))
        .build()
    );

    private final Setting<SettingColor> veinLineColor = sgRender.add(new ColorSetting.Builder()
        .name("damar-cizgi-rengi")
        .defaultValue(new SettingColor(255, 60, 200, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracer")
        .description("Buyuk damarlara cizgi cek.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxRender = sgRender.add(new IntSetting.Builder()
        .name("maks-cizim")
        .defaultValue(3000)
        .range(100, 20000)
        .sliderRange(100, 10000)
        .build()
    );

    private final Map<Long, List<BlockPos>> found = new HashMap<>();
    private final Set<Long> alerted = new HashSet<>();
    private final ScanLoop loop = new ScanLoop();
    private final PosCache cache = new PosCache();

    public NetheriteFinder() {
        super(MenemenAddon.CATEGORY, "netherite-finder",
            "Ancient debris ve netherite bloklarini bulur, buyuk damarlari ayri renkle gosterir.");
        Findings.register(this);
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
        alerted.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Map<Block, Integer> weights = new HashMap<>();
        weights.put(Blocks.ANCIENT_DEBRIS, 1);
        if (includeBlocks.get()) weights.put(Blocks.NETHERITE_BLOCK, 1);

        int lo = fullHeight.get() ? -4096 : Math.min(minY.get(), maxY.get());
        int hi = fullHeight.get() ? 4096 : Math.max(minY.get(), maxY.get());

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

                if (r.count >= veinThreshold.get()) {
                    if (alert.get() && alerted.add(key)) {
                        info("Buyuk damar: chunk (%d, %d) icinde %d blok, Y %d..%d",
                            cp.getStartX(), cp.getStartZ(), r.count, r.minY, r.maxY);
                    }
                } else {
                    alerted.remove(key);
                }
            }
        );
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (found.isEmpty() || mc.world == null) return;

        int threshold = veinThreshold.get();

        for (BlockPos pos : cache.nearest(mc, found.values(), maxRender.get())) {
            if (mc.world.getBlockState(pos).isAir()) continue;

            List<BlockPos> chunkList = found.get(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
            boolean vein = chunkList != null && chunkList.size() >= threshold;

            Color side = vein ? veinSideColor.get() : sideColor.get();
            Color line = vein ? veinLineColor.get() : lineColor.get();
            event.renderer.box(pos, side, line, shapeMode.get(), 0);

            if (vein && tracers.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    line
                );
            }
        }
    }

    @Override
    public Map<Long, Integer> chunkScores() {
        Map<Long, Integer> out = new HashMap<>();
        for (Map.Entry<Long, List<BlockPos>> e : found.entrySet()) out.put(e.getKey(), e.getValue().size() * 4);
        return out;
    }

    @Override
    public String getInfoString() {
        int total = 0;
        for (List<BlockPos> list : found.values()) total += list.size();
        return total + " (" + loop.scannedCount() + "/" + loop.loadedCount() + ")";
    }
}
