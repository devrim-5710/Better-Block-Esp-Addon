package com.betterblockesp;

import autismclient.api.AutismAddon;
import autismclient.api.AutismAddons;
import autismclient.api.ApiVersion;
import autismclient.modules.ModuleRegistry;

public final class BetterBlockEspAddon extends AutismAddon {
    public static final String ID = "better-block-esp";

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public String getPackage() {
        return "com.betterblockesp";
    }

    @Override
    public void onInitialize() {
        BetterBlockEspSettings.install(ModuleRegistry.get(BetterBlockEspSettings.MODULE_ID));
        AutismAddons.events().onTick(client -> BetterBlockEspSettings.tick());
    }
}
