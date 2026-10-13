package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BlockScan;
import com.menemen.addon.utils.ScanLoop;
import com.menemen.addon.utils.Unnatural;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;

import java.util.*;

/**
 * Chunk Finder: secilen kategorilerdeki bloklardan yeterince cok bulunan chunklari kutu ile
 * isaretler. Kategoriler blok kayit defterinden otomatik cikarilir (depolama, islevsel, redstone,
 * degerli bloklar, yapi bloklari, isik).
 */
public class ChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCats = settings.createGroup("Kategoriler");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> depo = sgCats.add(new BoolSetting.Builder()
        .name("depolama").description("Sandik, barrel, shulker.").defaultValue(true).build());
    private final Setting<Boolean> islev = sgCats.add(new BoolSetting.Builder()
        .name("islevsel").description("Firin, hopper, crafting table, yatak, anvil, beacon...").defaultValue(true).build());
    private final Setting<Boolean> redstone = sgCats.add(new BoolSetting.Builder()
        .name("redstone").description("Piston, observer, lamba, tnt...").defaultValue(true).build());
    private final Setting<Boolean> degerli = sgCats.add(new BoolSetting.Builder()
        .name("degerli-bloklar").description("Elmas, netherite, altin, demir blogu...").defaultValue(true).build());
    private final Setting<Boolean> yapi = sgCats.add(new BoolSetting.Builder()
        .name("yapi-bloklari").description("Tahta, tugla, cam, beton, yun, kapi, merdiven...").defaultValue(false).build());
    private final Setting<Boolean> isik = sgCats.add(new BoolSetting.Builder()
        .name("isik").description("Mesale ve fener.").defaultValue(false).build());

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("puan-esigi")
        .description("Bir chunkun isaretlenmesi icin en az puan (depolama ve islevsel 3, redstone ve degerli 2, digerleri 1).")
        .defaultValue(8)
        .range(1, 1000)
        .sliderRange(1, 200)
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

    private record Hit(int x0, int z0, int minY, int maxY, int score) {}

    private final Map<Long, Hit> hits = new HashMap<>();
    private final Set<Long> alerted = new HashSet<>();
    private final ScanLoop loop = new ScanLoop();

    public ChunkFinder() {
        super(MenemenAddon.CATEGORY, "chunk-finder",
            "Secilen blok kategorilerinden cok iceren chunklari kutu ile isaretler.");
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

    private EnumSet<Unnatural.Cat> enabledCats() {
        EnumSet<Unnatural.Cat> s = EnumSet.noneOf(Unnatural.Cat.class);
        if (depo.get()) s.add(Unnatural.Cat.DEPO);
        if (islev.get()) s.add(Unnatural.Cat.ISLEV);
        if (redstone.get()) s.add(Unnatural.Cat.REDSTONE);
        if (degerli.get()) s.add(Unnatural.Cat.DEGERLI);
        if (yapi.get()) s.add(Unnatural.Cat.YAPI);
        if (isik.get()) s.add(Unnatural.Cat.ISIK);
        return s;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Map<Block, Integer> weights = Unnatural.weights(enabledCats());
        int lo = Math.min(minY.get(), maxY.get());
        int hi = Math.max(minY.get(), maxY.get());

        loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), rescanTicks.get(), budgetMs.get(),
            key -> {
                hits.remove(key);
                alerted.remove(key);
            },
            (cp, chunk) -> {
                BlockScan.Result r = BlockScan.scan(chunk, cp, weights, lo, hi, false);

                long key = cp.toLong();
                if (r.score >= threshold.get()) {
                    hits.put(key, new Hit(cp.getStartX(), cp.getStartZ(), r.minY, r.maxY, r.score));
                    if (alert.get() && alerted.add(key)) {
                        info("Chunk (%d, %d): puan %d (%d blok), Y %d..%d",
                            cp.getStartX(), cp.getStartZ(), r.score, r.count, r.minY, r.maxY);
                    }
                } else {
                    hits.remove(key);
                    alerted.remove(key);
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
