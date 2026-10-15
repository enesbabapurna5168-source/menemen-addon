package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BlockScan;
import com.menemen.addon.utils.Findings;
import com.menemen.addon.utils.ScanLoop;
import com.menemen.addon.utils.Unnatural;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/**
 * Prime Chunk Finder: en guclu base adaylarini siralar. Bir base genelde birden cok chunka yayilir,
 * bu yuzden her chunkun puani yanindaki 8 chunkla birlestirilir (3x3 kume). Kume puani, kumedeki
 * FARKLI blok kategorisi sayisiyla carpilir (depo + firin + redstone gibi cesitlilik bir base'e
 * isaret eder). En iyi adaylar birbirine yakin olmayacak sekilde secilir ve en dipten (-64) tepeye
 * kadar tum yukseklikte taranir.
 */
public class PrimeChunkFinder extends Module implements Findings.Source {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCats = settings.createGroup("Kategoriler");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> depo = sgCats.add(new BoolSetting.Builder()
        .name("depolama").defaultValue(true).build());
    private final Setting<Boolean> islev = sgCats.add(new BoolSetting.Builder()
        .name("islevsel").defaultValue(true).build());
    private final Setting<Boolean> redstone = sgCats.add(new BoolSetting.Builder()
        .name("redstone").defaultValue(true).build());
    private final Setting<Boolean> degerli = sgCats.add(new BoolSetting.Builder()
        .name("degerli-bloklar").defaultValue(true).build());
    private final Setting<Boolean> yapi = sgCats.add(new BoolSetting.Builder()
        .name("yapi-bloklari").defaultValue(true).build());
    private final Setting<Boolean> isik = sgCats.add(new BoolSetting.Builder()
        .name("isik").description("Yeralti mesale ve fener.").defaultValue(true).build());

    private final Setting<Integer> minScore = sgGeneral.add(new IntSetting.Builder()
        .name("min-puan")
        .description("Bir adayin listeye girmesi icin en az kume puani.")
        .defaultValue(20)
        .range(1, 2000)
        .sliderRange(1, 300)
        .build()
    );

    private final Setting<Integer> minCategories = sgGeneral.add(new IntSetting.Builder()
        .name("min-kategori")
        .description("Kumede en az kac farkli kategori gorulmeli.")
        .defaultValue(2)
        .range(1, 6)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Integer> topCount = sgGeneral.add(new IntSetting.Builder()
        .name("aday-sayisi")
        .description("Ayni anda gosterilecek en iyi aday sayisi.")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

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

    private final Setting<Integer> undergroundMaxY = sgGeneral.add(new IntSetting.Builder()
        .name("yeralti-max-y")
        .description("Mesale ve fener sadece bu Y'nin altinda sayilir.")
        .defaultValue(40)
        .range(-64, 320)
        .sliderRange(-64, 128)
        .build()
    );

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-yaricapi").defaultValue(32).range(1, 32).sliderRange(1, 32).build());

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("tick-basina-chunk").defaultValue(16).range(1, 64).sliderRange(1, 32).build());

    private final Setting<Integer> budgetMs = sgGeneral.add(new IntSetting.Builder()
        .name("tarama-butcesi-ms").defaultValue(6).range(1, 20).sliderRange(1, 12).build());

    private final Setting<Integer> rescanTicks = sgGeneral.add(new IntSetting.Builder()
        .name("yeniden-tarama").defaultValue(300).range(20, 2400).sliderRange(20, 1200).build());

    private final Setting<Boolean> alert = sgGeneral.add(new BoolSetting.Builder()
        .name("sohbet-uyarisi")
        .description("Listeye yeni giren aday icin sohbette koordinat yaz.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("sekil").defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("yuzey-rengi").defaultValue(new SettingColor(255, 215, 0, 25)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("cizgi-rengi").defaultValue(new SettingColor(255, 215, 0, 255)).build());

    private final Setting<Boolean> tracer = sgRender.add(new BoolSetting.Builder()
        .name("tracer")
        .description("En iyi adaya cizgi cek.")
        .defaultValue(true)
        .build()
    );

    private record Cell(int score, int minY, int maxY, int mask) {}

    private record Prime(int cx, int cz, int score, int minY, int maxY, int cats) {}

    private final Map<Long, Cell> cells = new HashMap<>();
    private final List<Prime> top = new ArrayList<>();
    private final Set<Long> alerted = new HashSet<>();
    private final ScanLoop loop = new ScanLoop();
    private int rankTicks;

    public PrimeChunkFinder() {
        super(MenemenAddon.CATEGORY, "prime-chunk-finder",
            "En guclu base adaylarini (3x3 chunk kumesi, kategori cesitliligi) siralar, -64'ten tepeye tarar.");
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
        cells.clear();
        top.clear();
        alerted.clear();
        rankTicks = 0;
    }

    private EnumSet<Unnatural.Cat> mainCats() {
        EnumSet<Unnatural.Cat> s = EnumSet.noneOf(Unnatural.Cat.class);
        if (depo.get()) s.add(Unnatural.Cat.DEPO);
        if (islev.get()) s.add(Unnatural.Cat.ISLEV);
        if (redstone.get()) s.add(Unnatural.Cat.REDSTONE);
        if (degerli.get()) s.add(Unnatural.Cat.DEGERLI);
        if (yapi.get()) s.add(Unnatural.Cat.YAPI);
        return s;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Map<Block, Integer> mainWeights = Unnatural.weights(mainCats());
        Map<Block, Integer> lightWeights = isik.get()
            ? Unnatural.weights(EnumSet.of(Unnatural.Cat.ISIK))
            : Collections.<Block, Integer>emptyMap();

        int lo = fullHeight.get() ? -4096 : Math.min(minY.get(), maxY.get());
        int hi = fullHeight.get() ? 4096 : Math.max(minY.get(), maxY.get());
        int lightHi = Math.min(hi, undergroundMaxY.get());

        loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), rescanTicks.get(), budgetMs.get(),
            cells::remove,
            (cp, chunk) -> {
                BlockScan.Result r = BlockScan.scan(chunk, cp, mainWeights, lo, hi, false);
                r.add(BlockScan.scan(chunk, cp, lightWeights, lo, lightHi, false));

                int mask = 0;
                for (Block b : r.perBlock.keySet()) {
                    Unnatural.Cat c = Unnatural.categoryOf(b);
                    if (c != null) mask |= 1 << c.ordinal();
                }

                long key = cp.toLong();
                if (r.score > 0) cells.put(key, new Cell(r.score, r.minY, r.maxY, mask));
                else cells.remove(key);
            }
        );

        if (++rankTicks >= 20) {
            rankTicks = 0;
            rank();
        }
    }

    private void rank() {
        List<Prime> all = new ArrayList<>();

        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            int cx = ChunkPos.getPackedX(e.getKey());
            int cz = ChunkPos.getPackedZ(e.getKey());

            int sum = 0;
            int mask = 0;
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Cell c = cells.get(ChunkPos.toLong(cx + dx, cz + dz));
                    if (c == null) continue;
                    sum += c.score();
                    mask |= c.mask();
                    lo = Math.min(lo, c.minY());
                    hi = Math.max(hi, c.maxY());
                }
            }

            int cats = Integer.bitCount(mask);
            int prime = (int) (sum * (1.0 + 0.25 * Math.max(0, cats - 1)));
            if (prime >= minScore.get() && cats >= minCategories.get()) {
                all.add(new Prime(cx, cz, prime, lo, hi, cats));
            }
        }

        all.sort((a, b) -> Integer.compare(b.score(), a.score()));

        // Birbirine cok yakin (ayni base'e ait) adaylari ele.
        top.clear();
        for (Prime p : all) {
            boolean near = false;
            for (Prime t : top) {
                if (Math.abs(t.cx() - p.cx()) <= 2 && Math.abs(t.cz() - p.cz()) <= 2) {
                    near = true;
                    break;
                }
            }
            if (near) continue;
            top.add(p);
            if (top.size() >= topCount.get()) break;
        }

        Set<Long> current = new HashSet<>();
        int rank = 0;
        for (Prime p : top) {
            rank++;
            long key = ChunkPos.toLong(p.cx(), p.cz());
            current.add(key);
            if (alert.get() && alerted.add(key)) {
                info("Prime chunk #%d: (%d, %d) puan %d, %d kategori, Y %d..%d",
                    rank, p.cx() * 16, p.cz() * 16, p.score(), p.cats(), p.minY(), p.maxY());
            }
        }
        alerted.retainAll(current);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        boolean first = true;
        for (Prime p : top) {
            event.renderer.box(
                (p.cx() - 1) * 16, p.minY(), (p.cz() - 1) * 16,
                (p.cx() + 2) * 16, p.maxY() + 1, (p.cz() + 2) * 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );

            if (first && tracer.get()) {
                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    p.cx() * 16 + 8, (p.minY() + p.maxY()) / 2.0, p.cz() * 16 + 8,
                    lineColor.get()
                );
            }
            first = false;
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
        return top.size() + " (" + loop.scannedCount() + "/" + loop.loadedCount() + ")";
    }
}
