package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BlockScan;
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
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Debris ESP: yuklu chunklardaki ancient debris bloklarini kutu ile isaretler.
 * Nether'de ise yarar (debris cogunlukla Y 8..22 arasinda cikar).
 */
public class DebrisESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

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
        .defaultValue(new SettingColor(190, 90, 255, 60))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi")
        .defaultValue(new SettingColor(190, 90, 255, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracer")
        .defaultValue(false)
        .build()
    );

    private final Map<Long, List<BlockPos>> found = new HashMap<>();
    private final ScanLoop loop = new ScanLoop();
    private final Map<Block, Integer> weights = new HashMap<>();

    public DebrisESP() {
        super(MenemenAddon.CATEGORY, "debris-esp",
            "Yuklu chunklardaki ancient debris bloklarini isaretler.");
        weights.put(Blocks.ANCIENT_DEBRIS, 1);
    }

    @Override
    public void onActivate() {
        loop.reset();
        found.clear();
    }

    @Override
    public void onDeactivate() {
        loop.reset();
        found.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), rescanTicks.get(),
            found::remove,
            (cp, chunk) -> {
                int lo = Math.min(minY.get(), maxY.get());
                int hi = Math.max(minY.get(), maxY.get());
                BlockScan.Result r = BlockScan.scan(chunk, cp, weights, lo, hi);

                long key = cp.toLong();
                if (r.positions.isEmpty()) found.remove(key);
                else found.put(key, r.positions);
            }
        );
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (List<BlockPos> list : found.values()) {
            for (BlockPos pos : list) {
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
    }

    @Override
    public String getInfoString() {
        int total = 0;
        for (List<BlockPos> list : found.values()) total += list.size();
        return String.valueOf(total);
    }
}
