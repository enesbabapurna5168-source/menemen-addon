package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.*;

/**
 * Base bulmak icin ESP. Yuklu chunklardaki oyuncu yapimi bloklari (sandik, shulker,
 * firin, hopper, yatak vb.) secilen Y araliginda (varsayilan -64 .. 320) vurgular.
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
        .defaultValue(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST,
            Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX,
            Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX,
            Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
            Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX,
            Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX,
            Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
            Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER,
            Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.BEACON,
            Blocks.BREWING_STAND, Blocks.JUKEBOX, Blocks.LECTERN,
            Blocks.WHITE_BED, Blocks.RED_BED, Blocks.BLACK_BED, Blocks.BLUE_BED,
            Blocks.TORCH, Blocks.LANTERN, Blocks.END_PORTAL_FRAME
        )
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
        .description("Oyuncu etrafinda taranacak chunk yaricapi.")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("tick-basina-chunk")
        .description("Her tickte taranacak chunk sayisi. Dusuk deger FPS'i korur.")
        .defaultValue(2)
        .range(1, 16)
        .sliderRange(1, 8)
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
        .defaultValue(2000)
        .range(100, 20000)
        .sliderRange(100, 10000)
        .build()
    );

    private final Setting<Boolean> alert = sgAlert.add(new BoolSetting.Builder()
        .name("kume-uyarisi")
        .description("Bir chunkta cok sayida depolama blogu bulununca sohbette haber ver.")
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
    private final Map<Long, Integer> lastScan = new HashMap<>();
    private final Set<Long> alerted = new HashSet<>();
    private int tickCounter;

    public BaseFinderESP() {
        super(MenemenAddon.CATEGORY, "base-finder-esp",
            "Yuklu chunklardaki oyuncu yapimi bloklari secilen Y araliginda (varsayilan -64'e kadar) vurgular.");
    }

    @Override
    public void onActivate() {
        clear();
    }

    @Override
    public void onDeactivate() {
        clear();
    }

    private void clear() {
        found.clear();
        lastScan.clear();
        alerted.clear();
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        tickCounter++;

        ChunkPos center = mc.player.getChunkPos();
        int radius = chunkRadius.get();

        // Menzil disina cikan chunklari at.
        found.keySet().removeIf(key -> {
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            boolean outside = Math.abs(cx - center.x) > radius || Math.abs(cz - center.z) > radius;
            if (outside) {
                lastScan.remove(key);
                alerted.remove(key);
            }
            return outside;
        });

        // En eski taranmis chunklari onceliklendirerek butce kadar tara.
        List<ChunkPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                long key = ChunkPos.toLong(cx, cz);
                Integer last = lastScan.get(key);
                if (last == null || tickCounter - last >= rescanTicks.get()) {
                    candidates.add(new ChunkPos(cx, cz));
                }
            }
        }

        candidates.sort(Comparator.comparingInt(c -> {
            Integer last = lastScan.get(c.toLong());
            return last == null ? Integer.MIN_VALUE : last;
        }));

        int budget = chunksPerTick.get();
        for (ChunkPos cp : candidates) {
            if (budget-- <= 0) break;
            scanChunk(cp);
        }
    }

    private void scanChunk(ChunkPos cp) {
        long key = cp.toLong();
        lastScan.put(key, tickCounter);

        Chunk chunk = mc.world.getChunk(cp.x, cp.z);
        if (chunk == null) return;

        int lo = Math.min(minY.get(), maxY.get());
        int hi = Math.max(minY.get(), maxY.get());
        Set<Block> wanted = new HashSet<>(blocks.get());

        List<BlockPos> result = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            int baseY = (chunk.getBottomSectionCoord() + i) << 4;
            if (baseY + 15 < lo || baseY > hi) continue;
            if (!section.hasAny(state -> wanted.contains(state.getBlock()))) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = baseY + y;
                    if (worldY < lo || worldY > hi) continue;
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!wanted.contains(state.getBlock())) continue;
                        result.add(new BlockPos(cp.getStartX() + x, worldY, cp.getStartZ() + z));
                    }
                }
            }
        }

        if (result.isEmpty()) found.remove(key);
        else found.put(key, result);

        if (alert.get() && result.size() >= alertThreshold.get() && alerted.add(key)) {
            info("Olasi base: chunk (%d, %d) icinde %d blok.", cp.x, cp.z, result.size());
        }
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
