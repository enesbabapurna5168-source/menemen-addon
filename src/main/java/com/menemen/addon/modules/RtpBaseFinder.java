package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import com.menemen.addon.utils.BaseBlocks;
import com.menemen.addon.utils.BlockScan;
import com.menemen.addon.utils.ScanLoop;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * RTP Base Finder: sunucunun /rtp komutunu belirli aralikla kullanir, her inista cevredeki
 * yuklu chunklari tarar. Esigi gecen puanli bir chunk bulursa durur ve koordinati yazar.
 *
 * Komut araligi sabittir, rastgelelestirme veya algilama atlatma icermez. Sunucunun komut
 * bekleme suresinden (cooldown) kisa bir aralik ayarlamayin.
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
        .description("Iki komut arasindaki sure (tick, 20 tick = 1 saniye).")
        .defaultValue(600)
        .range(100, 12000)
        .sliderRange(100, 3000)
        .build()
    );

    private final Setting<Integer> scanWait = sgGeneral.add(new IntSetting.Builder()
        .name("tarama-suresi")
        .description("Isinlanmadan sonra chunklarin yuklenip taranmasi icin beklenecek sure (tick).")
        .defaultValue(160)
        .range(40, 2000)
        .sliderRange(40, 600)
        .build()
    );

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("puan-esigi")
        .description("Bir chunkta bu puan gecilirse durur.")
        .defaultValue(10)
        .range(1, 500)
        .sliderRange(1, 100)
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

    private final Setting<Integer> maxRtps = sgGeneral.add(new IntSetting.Builder()
        .name("maks-rtp")
        .description("Bu kadar isinlanmadan sonra otomatik durur.")
        .defaultValue(100)
        .range(1, 1000)
        .sliderRange(1, 300)
        .build()
    );

    private final ScanLoop loop = new ScanLoop();
    private final Map<Block, Integer> weights = new HashMap<>();

    private int ticks;
    private int rtpCount;
    private int bestScore;
    private int bestX;
    private int bestZ;
    private boolean evaluated;

    public RtpBaseFinder() {
        super(MenemenAddon.CATEGORY, "rtp-base-finder",
            "/rtp ile gezip her inista cevredeki chunklari base icin tarar, bulunca durur.");

        for (Block b : BaseBlocks.STORAGE) weights.put(b, 3);
        for (Block b : BaseBlocks.UTILITY) weights.put(b, 3);
        for (Block b : BaseBlocks.REDSTONE) weights.put(b, 2);
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

    private void resetRound() {
        loop.reset();
        ticks = 0;
        bestScore = 0;
        bestX = 0;
        bestZ = 0;
        evaluated = false;
    }

    private void sendRtp() {
        resetRound();
        rtpCount++;
        ChatUtils.sendPlayerMsg(command.get());
        info("RTP %d/%d gonderildi.", rtpCount, maxRtps.get());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        ticks++;

        // Isinlandiktan sonra tarama suresi dolana kadar chunklari tara.
        if (ticks <= scanWait.get()) {
            loop.tick(mc, chunkRadius.get(), chunksPerTick.get(), 20,
                key -> {},
                (cp, chunk) -> {
                    BlockScan.Result r = BlockScan.scan(chunk, cp, weights, -64, 320);
                    if (r.score > bestScore) {
                        bestScore = r.score;
                        bestX = cp.getStartX();
                        bestZ = cp.getStartZ();
                    }
                }
            );
            return;
        }

        if (!evaluated) {
            evaluated = true;
            if (bestScore >= threshold.get()) {
                info("Base bulundu: chunk (%d, %d), puan %d. Modul durduruldu.", bestX, bestZ, bestScore);
                toggle();
                return;
            }
        }

        if (ticks >= rtpDelay.get()) {
            if (rtpCount >= maxRtps.get()) {
                info("Maksimum RTP sayisina ulasildi (%d). Modul durduruldu.", maxRtps.get());
                toggle();
                return;
            }
            sendRtp();
        }
    }

    @Override
    public String getInfoString() {
        return rtpCount + "/" + maxRtps.get();
    }
}
