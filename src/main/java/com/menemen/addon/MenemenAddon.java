package com.menemen.addon;

import com.menemen.addon.modules.BaseFinderESP;
import com.menemen.addon.modules.ChunkFinder;
import com.menemen.addon.modules.MenemenDebug;
import com.menemen.addon.modules.SusChunkFinder;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class MenemenAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Menemen");

    @Override
    public void onInitialize() {
        LOG.info("Menemen Addon yukleniyor");
        Modules.get().add(new BaseFinderESP());
        Modules.get().add(new ChunkFinder());
        Modules.get().add(new SusChunkFinder());
        Modules.get().add(new MenemenDebug());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.menemen.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("menemen", "menemen-addon");
    }
}
