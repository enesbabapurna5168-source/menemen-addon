package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BlockScan;
import com.menemen.addon.utils.ScanLoop;
import com.menemen.addon.utils.Unnatural;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;

import java.util.EnumSet;
import java.util.Map;

/**
 * RTP Base Finder: sunucunun /rtp komutunu sabit aralikla kullanir. Isinlanmanin gercekten
 * gerceklestigini (oyuncu uzaga tasindi mi) algilar, SONRA cevredeki yuklu chunklari tum yukseklikte
 * (-64'ten tepeye) tarar. Yeterince puanli ve birden cok kategoriden iz tasiyan bir chunk bulursa
 * durur ve koordinati yazar.
 *
 * Komut araligi sabittir, rastgelelestirme veya algilama atlatma icermez. Sunucunun komut bekleme
 * suresinden (cooldown) kisa bir aralik ayarlamayin.
 */
public class RtpBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("komut")
        .description("Isinlanma komutu.")
        .defaultValue("/rtp")
        .build()
    );

    private final Setting<Integer> rtpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("rtp-araligi")
        .description("Iki komut arasindaki en kisa sure (tick, 20 tick = 1 saniye).")
        .defaultValue(600)
        .range(100, 12000)
        .sliderRange(100, 3000)
        .build()
    );

    private final Setting<Integer> teleportDistance = sgGeneral.add(new IntSetting.Builder()
        .name("isinlanma-mesafesi")
        .description("Oyuncu komuttan sonra bu kadar blok uzaklasirsa isinlanma gerceklesmis sayilir.")
        .defaultValue(100)
        .range(16, 5000)
        .sliderRange(16, 1000)
        .build()
    );

    private final Setting<Integer> teleportTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("isinlanma-zaman-asimi")
        .description("Isinlanma bu surede olmazsa komut tekrar gonderilir (tick).")
        .defaultValue(600)
        .range(100, 6000)
        .sliderRange(100, 2400)
        .build()
    );

    private final Setting<Integer> scanWait = sgGeneral.add(new IntSetting.Builder()
        .name("tarama-suresi")
        .description("Isinlandiktan sonra chunklarin yuklenip taranmasi icin beklenecek sure (tick).")
        .defaultValue(200)
        .range(40, 2000)
        .sliderRange(40, 600)
        .build()
    );

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("puan-esigi")
        .description("Bir chunkta bu puan gecilirse durur.")
        .defaultValue(12)
        .range(1, 500)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> minCategories = sgGeneral.add(new IntSetting.Builder()
        .name("min-kategori")
        .description("Chunkta en az kac farkli kategoriden blok gorulmeli.")
        .defaultValue(2)
        .range(1, 5)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-yaricapi").defaultValue(32).range(1, 32).sliderRange(1, 32).build());

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("tick-basina-chunk").defaultValue(16).range(1, 64).sliderRange(1, 32).build());

    private final Setting<Integer> budgetMs = sgGeneral.add(new IntSetting.Builder()
        .name("tarama-butcesi-ms").defaultValue(8).range(1, 20).sliderRange(1, 12).build());

    private final Setting<Integer> maxRtps = sgGeneral.add(new IntSetting.Builder()
        .name("maks-rtp")
        .description("Bu kadar isinlanmadan sonra otomatik durur.")
        .defaultValue(100)
        .range(1, 1000)
        .sliderRange(1, 300)
        .build()
    );

    private enum State { WAIT_TELEPORT, SCANNING, COOLDOWN }

    private final ScanLoop loop = new ScanLoop();

    private State state = State.WAIT_TELEPORT;
    private int stateTicks;
    private int sinceSend;
    private int rtpCount;
    private double startX;
    private double startZ;

    private int bestScore;
    private int bestX;
    private int bestZ;
    private int bestMinY;
    private int bestMaxY;

    public RtpBaseFinder() {
        super(MenemenAddon.CATEGORY, "rtp-base-finder",
            "/rtp ile gezip her inista cevredeki chunklari -64'ten tepeye base icin tarar, bulunca durur.");
    }

    @Override
    public void onActivate() {
        rtpCount = 0;
        sendRtp();
    }

    @Override
    public void onDeactivate() {
        loop.reset();
    }

    private void sendRtp() {
        if (mc.player == null) return;

        loop.reset();
        bestScore = 0;
        startX = mc.player.getX();
        startZ = mc.player.getZ();
        state = State.WAIT_TELEPORT;
        stateTicks = 0;
        sinceSend = 0;
        rtpCount++;

        ChatUtils.sendPlayerMsg(command.get());
        info("RTP %d/%d gonderildi, isinlanma bekleniyor.", rtpCount, maxRtps.get());
    }

    private void nextRtp() {
        if (rtpCount >= maxRtps.get()) {
            info("Maksimum RTP sayisina ulasildi (%d). Modul durduruldu.", maxRtps.get());
            toggle();
            return;
        }
        sendRtp();
    }

    private static int categoryCount(Map<Block, Integer> perBlock) {
        int mask = 0;
        for (Block b : perBlock.keySet()) {
            Unnatural.Cat c = Unnatural.categoryOf(b);
            if (c != null) mask |= 1 << c.ordinal();
        }
        return Integer.bitCount(mask);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        stateTicks++;
        sinceSend++;

        switch (state) {
            case WAIT_TELEPORT -> {
                double dx = mc.player.getX() - startX;
                double dz = mc.player.getZ() - startZ;
                double need = teleportDistance.get();

                if (dx * dx + dz * dz >= need * need) {
                    state = State.SCANNING;
                    stateTicks = 0;
                    loop.reset();
                    bestScore = 0;
                    info("Isinlandi, cevre taraniyor.");
                } else if (stateTicks >= teleportTimeout.get() && sinceSend >= rtpDelay.get()) {
                    info("Isinlanma algilanmadi, komut tekrar gonderiliyor.");
                    nextRtp();
                }
            }

            case SCANNING -> {
                Map<Block, Integer> weights = Unnatural.weights(EnumSet.of(
                    Unnatural.Cat.DEPO, Unnatural.Cat.ISLEV, Unnatural.Cat.REDSTONE, Unnatural.Cat.DEGERLI));

                loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), 20, budgetMs.get(),
                    key -> {},
                    (cp, chunk) -> {
                        BlockScan.Result r = BlockScan.scan(chunk, cp, weights, -4096, 4096, false);
                        if (r.score > bestScore && categoryCount(r.perBlock) >= minCategories.get()) {
                            bestScore = r.score;
                            bestX = cp.getStartX();
                            bestZ = cp.getStartZ();
                            bestMinY = r.minY;
                            bestMaxY = r.maxY;
                        }
                    }
                );

                if (stateTicks >= scanWait.get()) {
                    if (bestScore >= threshold.get()) {
                        info("Base bulundu: chunk (%d, %d), puan %d, Y %d..%d. Modul durduruldu.",
                            bestX, bestZ, bestScore, bestMinY, bestMaxY);
                        toggle();
                    } else {
                        state = State.COOLDOWN;
                    }
                }
            }

            case COOLDOWN -> {
                if (sinceSend >= rtpDelay.get()) nextRtp();
            }
        }
    }

    @Override
    public String getInfoString() {
        return rtpCount + "/" + maxRtps.get();
    }
}
