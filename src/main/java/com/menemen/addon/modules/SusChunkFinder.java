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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;

import java.util.*;

/**
 * Sus Chunk Finder: dogal olmayan yapi izlerine puan verir ve en az birkac FARKLI kategoriden
 * iz gormeyi sart kosar (ornegin sadece tahta degil, tahta + sandik + firin). Boylece tek turlu
 * dogal yapilar (mineshaft tahtalari gibi) daha az yanlis alarm uretir.
 *
 * Mesale ve fener sadece "yeralti-max-y" altinda sayilir; yuzeyde dogal olarak da bulunurlar.
 */
public class SusChunkFinder extends Module implements Findings.Source {
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
        .name("yapi-bloklari").description("Tahta, tugla, cam, beton, yun, kapi, merdiven...").defaultValue(true).build());
    private final Setting<Boolean> isik = sgCats.add(new BoolSetting.Builder()
        .name("isik").description("Yeralti mesale ve fener.").defaultValue(true).build());

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("puan-esigi")
        .description("Bir chunkun supheli sayilmasi icin en az puan.")
        .defaultValue(12)
        .range(1, 1000)
        .sliderRange(1, 200)
        .build()
    );

    private final Setting<Integer> minCategories = sgGeneral.add(new IntSetting.Builder()
        .name("min-kategori")
        .description("Puana ek olarak en az kac FARKLI kategoriden blok gorulmeli. Yanlis alarmi azaltir.")
        .defaultValue(2)
        .range(1, 6)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Integer> undergroundMaxY = sgGeneral.add(new IntSetting.Builder()
        .name("yeralti-max-y")
        .description("Mesale ve fener sadece bu Y seviyesinin altinda sayilir.")
        .defaultValue(40)
        .range(-64, 320)
        .sliderRange(-64, 128)
        .build()
    );

    private final Setting<Boolean> fullHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("tum-yukseklik")
        .description("Min/max Y ayarlarini yok sayip dunyanin en dibinden (-64 bedrock) en tepesine kadar tum katmanlari tara.")
        .defaultValue(true)
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
    private int lastSig;

    public SusChunkFinder() {
        super(MenemenAddon.CATEGORY, "sus-chunk-finder",
            "Dogal olmayan yapi izleri tasiyan supheli chunklari puanlayip isaretler.");
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
        lastSig = 0;
        loop.reset();
        hits.clear();
        alerted.clear();
    }

    /** Mesale/fener haric, her yerde sayilan kategoriler. */
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
        // Ayar degisince eski sonuclari atip hemen yeniden tara.
        int sig = Objects.hash(mainCats(), isik.get(), threshold.get(), minCategories.get(),
            undergroundMaxY.get(), fullHeight.get(), minY.get(), maxY.get());
        if (sig != lastSig) {
            if (lastSig != 0) {
                loop.reset();
                hits.clear();
            }
            lastSig = sig;
        }

        Map<Block, Integer> mainWeights = Unnatural.weights(mainCats());
        Map<Block, Integer> lightWeights = isik.get()
            ? Unnatural.weights(EnumSet.of(Unnatural.Cat.ISIK))
            : Collections.<Block, Integer>emptyMap();

        int lo = fullHeight.get() ? -4096 : Math.min(minY.get(), maxY.get());
        int hi = fullHeight.get() ? 4096 : Math.max(minY.get(), maxY.get());
        int lightHi = Math.min(hi, undergroundMaxY.get());

        loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), rescanTicks.get(), budgetMs.get(),
            key -> {
                hits.remove(key);
                alerted.remove(key);
            },
            (cp, chunk) -> {
                BlockScan.Result r = BlockScan.scan(chunk, cp, mainWeights, lo, hi, false);
                r.add(BlockScan.scan(chunk, cp, lightWeights, lo, lightHi, false));

                EnumSet<Unnatural.Cat> seen = EnumSet.noneOf(Unnatural.Cat.class);
                for (Block b : r.perBlock.keySet()) {
                    Unnatural.Cat c = Unnatural.categoryOf(b);
                    if (c != null) seen.add(c);
                }

                long key = cp.toLong();
                if (r.score >= threshold.get() && seen.size() >= minCategories.get()) {
                    hits.put(key, new Hit(cp.getStartX(), cp.getStartZ(), r.minY, r.maxY, r.score));
                    if (alert.get() && alerted.add(key)) {
                        info("Supheli chunk (%d, %d): puan %d, %d kategori, Y %d..%d",
                            cp.getStartX(), cp.getStartZ(), r.score, seen.size(), r.minY, r.maxY);
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
    public Map<Long, Integer> chunkScores() {
        Map<Long, Integer> out = new HashMap<>();
        for (Map.Entry<Long, Hit> e : hits.entrySet()) out.put(e.getKey(), e.getValue().score());
        return out;
    }

    @Override
    public String getInfoString() {
        return hits.size() + " (" + loop.scannedCount() + "/" + loop.loadedCount() + ")";
    }
}
