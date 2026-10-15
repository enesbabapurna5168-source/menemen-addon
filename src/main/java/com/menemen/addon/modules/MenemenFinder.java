package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BlockScan;
import com.menemen.addon.utils.CatColors;
import com.menemen.addon.utils.Findings;
import com.menemen.addon.utils.PosCache;
import com.menemen.addon.utils.ScanLoop;
import com.menemen.addon.utils.Unnatural;
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
 * Menemen Finder: hepsi bir arada base bulucu. En dipten (-64) tepeye tum katmanlari tarar,
 * en yakin chunktan baslar. Her blogu kategorisine gore renklendirir (depo turuncu, islevsel mavi,
 * redstone kirmizi, degerli pembe, isik sari, yapi gri), kirilan bloklarin kutusunu otomatik kaldirir
 * ve en yuksek puanli chunklari buyuk kutuyla + tracer ile gosterir.
 */
public class MenemenFinder extends Module implements Findings.Source {
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
        .name("yapi-bloklari").description("Tahta, tugla, cam, beton, yun, kapi... Cok blok cikabilir.").defaultValue(false).build());
    private final Setting<Boolean> isik = sgCats.add(new BoolSetting.Builder()
        .name("isik").description("Mesale ve fener.").defaultValue(false).build());

    private final Setting<Boolean> fullHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("tum-yukseklik")
        .description("Y sinirini yok sayip -64'ten tepeye tum katmanlari tara.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").defaultValue(-64).range(-64, 320).sliderRange(-64, 320).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").defaultValue(320).range(-64, 320).sliderRange(-64, 320).build());

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-yaricapi").defaultValue(32).range(1, 32).sliderRange(1, 32).build());

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("tick-basina-chunk").defaultValue(16).range(1, 64).sliderRange(1, 32).build());

    private final Setting<Integer> budgetMs = sgGeneral.add(new IntSetting.Builder()
        .name("tarama-butcesi-ms")
        .description("Her tickte taramaya ayrilan en fazla sure (ms).")
        .defaultValue(6).range(1, 20).sliderRange(1, 12).build());

    private final Setting<Integer> rescanTicks = sgGeneral.add(new IntSetting.Builder()
        .name("yeniden-tarama").defaultValue(200).range(20, 2400).sliderRange(20, 1200).build());

    private final Setting<Integer> clusterThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("kume-esigi")
        .description("Bir chunkun en iyi aday sayilmasi icin en az puan (depo/islevsel 3, redstone/degerli 2, digerleri 1).")
        .defaultValue(12)
        .range(1, 1000)
        .sliderRange(1, 200)
        .build()
    );

    private final Setting<Integer> topCount = sgGeneral.add(new IntSetting.Builder()
        .name("en-iyi-adet")
        .description("Buyuk kutuyla gosterilecek en iyi chunk sayisi.")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> alert = sgGeneral.add(new BoolSetting.Builder()
        .name("sohbet-uyarisi")
        .description("En iyi listesine yeni giren chunk icin sohbette koordinat yaz.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("sekil").defaultValue(ShapeMode.Both).build());

    private final Setting<Boolean> categoryColors = sgRender.add(new BoolSetting.Builder()
        .name("kategori-renkleri")
        .description("Bloklari kategorisine gore renklendir.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("yuzey-rengi").description("Kategori renkleri kapaliyken.")
        .defaultValue(new SettingColor(255, 170, 0, 50)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi").description("Kategori renkleri kapaliyken.")
        .defaultValue(new SettingColor(255, 170, 0, 255)).build());

    private final Setting<SettingColor> bestSide = sgRender.add(new ColorSetting.Builder()
        .name("en-iyi-yuzey-rengi").defaultValue(new SettingColor(0, 255, 120, 25)).build());

    private final Setting<SettingColor> bestLine = sgRender.add(new ColorSetting.Builder()
        .name("en-iyi-cizgi-rengi").defaultValue(new SettingColor(0, 255, 120, 255)).build());

    private final Setting<Boolean> tracer = sgRender.add(new BoolSetting.Builder()
        .name("tracer").description("En iyi chunka cizgi cek.").defaultValue(true).build());

    private final Setting<Integer> maxRender = sgRender.add(new IntSetting.Builder()
        .name("maks-cizim")
        .description("Ayni anda cizilecek en fazla blok. Limit dolunca en yakin bloklar cizilir.")
        .defaultValue(3000).range(100, 20000).sliderRange(100, 10000).build());

    private record Cell(int score, int minY, int maxY) {}

    private record Best(int cx, int cz, Cell cell) {}

    private final Map<Long, List<BlockPos>> found = new HashMap<>();
    private final Map<Long, Cell> cells = new HashMap<>();
    private final List<Best> best = new ArrayList<>();
    private final Set<Long> alerted = new HashSet<>();
    private final ScanLoop loop = new ScanLoop();
    private final PosCache cache = new PosCache();
    private int rankTicks;

    public MenemenFinder() {
        super(MenemenAddon.CATEGORY, "menemen-finder",
            "Hepsi bir arada base bulucu: -64'ten tepeye tarar, kategori renkleri, en iyi chunk kutusu.");
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
        cells.clear();
        best.clear();
        alerted.clear();
        rankTicks = 0;
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
        int lo = fullHeight.get() ? -4096 : Math.min(minY.get(), maxY.get());
        int hi = fullHeight.get() ? 4096 : Math.max(minY.get(), maxY.get());

        loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), rescanTicks.get(), budgetMs.get(),
            key -> {
                found.remove(key);
                cells.remove(key);
            },
            (cp, chunk) -> {
                BlockScan.Result r = BlockScan.scan(chunk, cp, weights, lo, hi);

                long key = cp.toLong();
                if (r.positions.isEmpty()) {
                    found.remove(key);
                    cells.remove(key);
                } else {
                    found.put(key, r.positions);
                    cells.put(key, new Cell(r.score, r.minY, r.maxY));
                }
            }
        );

        if (++rankTicks >= 20) {
            rankTicks = 0;
            rank();
        }
    }

    private void rank() {
        List<Best> all = new ArrayList<>();
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            if (e.getValue().score() < clusterThreshold.get()) continue;
            all.add(new Best(
                net.minecraft.util.math.ChunkPos.getPackedX(e.getKey()),
                net.minecraft.util.math.ChunkPos.getPackedZ(e.getKey()),
                e.getValue()
            ));
        }
        all.sort((a, b) -> Integer.compare(b.cell().score(), a.cell().score()));

        best.clear();
        for (Best b : all) {
            best.add(b);
            if (best.size() >= topCount.get()) break;
        }

        Set<Long> current = new HashSet<>();
        int rank = 0;
        for (Best b : best) {
            rank++;
            long key = net.minecraft.util.math.ChunkPos.toLong(b.cx(), b.cz());
            current.add(key);
            if (alert.get() && alerted.add(key)) {
                info("Menemen #%d: chunk (%d, %d) puan %d, Y %d..%d",
                    rank, b.cx() * 16, b.cz() * 16, b.cell().score(), b.cell().minY(), b.cell().maxY());
            }
        }
        alerted.retainAll(current);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        // En iyi chunklar.
        boolean first = true;
        for (Best b : best) {
            event.renderer.box(
                b.cx() * 16, b.cell().minY(), b.cz() * 16,
                b.cx() * 16 + 16, b.cell().maxY() + 1, b.cz() * 16 + 16,
                bestSide.get(), bestLine.get(), shapeMode.get(), 0
            );
            if (first && tracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    b.cx() * 16 + 8, (b.cell().minY() + b.cell().maxY()) / 2.0, b.cz() * 16 + 8,
                    bestLine.get()
                );
            }
            first = false;
        }

        // Tek tek bloklar: en yakindan uzaga, kirilanlar atlanir, kategori rengiyle.
        if (found.isEmpty()) return;
        for (BlockPos pos : cache.nearest(mc, found.values(), maxRender.get())) {
            var state = mc.world.getBlockState(pos);
            if (state.isAir()) continue;

            Color side = sideColor.get();
            Color line = lineColor.get();
            if (categoryColors.get()) {
                Unnatural.Cat cat = Unnatural.categoryOf(state.getBlock());
                if (cat != null) {
                    side = CatColors.side(cat);
                    line = CatColors.line(cat);
                }
            }
            event.renderer.box(pos, side, line, shapeMode.get(), 0);
        }
    }

    @Override
    public Map<Long, Integer> chunkScores() {
        Map<Long, Integer> out = new HashMap<>();
        for (Map.Entry<Long, Cell> e : cells.entrySet()) out.put(e.getKey(), e.getValue().score());
        return out;
    }

    @Override
    public String getInfoString() {
        int total = 0;
        for (List<BlockPos> list : found.values()) total += list.size();
        return total + " (" + loop.scannedCount() + "/" + loop.loadedCount() + ")";
    }
}
