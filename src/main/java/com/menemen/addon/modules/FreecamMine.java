package com.menemen.addon.modules;

import com.menemen.addon.MenemenAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Freecam Mine: Meteor'un Freecam modulu acikken sol tik basili tutunca, KAMERANIN baktigi blogu kirar.
 *
 * Erisim mesafesi oyuncunun gozunden olculur ve vanilla sinirini (4.5 blok) asamaz; yani kamerayi
 * uzaga goturup uzak bloklari kiramazsiniz, sadece oyuncunun normalde ulasabilecegi bloklari
 * kamera acisindan secebilirsiniz. Sunucuya normal kirma paketlerinden baska bir sey gonderilmez.
 *
 * Kullanim: once Render sekmesinden Freecam'i, sonra bu modulu acin.
 */
public class FreecamMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> cameraRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("kamera-menzili")
        .description("Kameradan ne kadar uzaktaki bloga bakilabilir (blok).")
        .defaultValue(12)
        .min(2)
        .sliderRange(2, 32)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("erisim-mesafesi")
        .description("Oyuncunun gozunden bloga en fazla mesafe. Vanilla siniri 4.5, daha yuksek ayarlanamaz.")
        .defaultValue(4.5)
        .min(1)
        .max(4.5)
        .sliderRange(1, 4.5)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("el-sallama")
        .description("Kirarken el sallama animasyonunu goster.")
        .defaultValue(true)
        .build()
    );

    private BlockPos current;

    public FreecamMine() {
        super(MenemenAddon.CATEGORY, "freecam-mine",
            "Freecam acikken kameranin baktigi blogu, normal erisim mesafesi icinde kirar.");
    }

    @Override
    public void onDeactivate() {
        stop();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (!Modules.get().isActive(Freecam.class)) {
            stop();
            return;
        }
        if (mc.currentScreen != null || !mc.options.attackKey.isPressed()) {
            stop();
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d start = camera.getPos();
        Vec3d dir = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d end = start.add(dir.multiply(cameraRange.get()));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
            start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            stop();
            return;
        }

        BlockPos pos = hit.getBlockPos();
        double max = reach.get();
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > max * max) {
            stop();
            return;
        }
        if (mc.world.getBlockState(pos).isAir()) {
            stop();
            return;
        }

        if (current != null && !current.equals(pos)) mc.interactionManager.cancelBlockBreaking();
        current = pos;

        if (mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide()) && swing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void stop() {
        if (current != null && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        current = null;
    }
}
