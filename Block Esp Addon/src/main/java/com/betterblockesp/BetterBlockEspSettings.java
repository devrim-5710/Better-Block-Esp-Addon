package com.betterblockesp;

import autismclient.AutismClientAddon;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.ColorSetting;
import autismclient.api.module.RegistryListSetting;
import autismclient.api.module.Setting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismRegistryLabels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Better Block ESP.
 *
 * <p>Attaches extra settings to AUTISM Client's own {@code block-esp} module instead of
 * registering a separate module, and hands back the per-block color while the module
 * collects its targets.
 *
 * <p>Everything here lives outside the {@code autismclient} packages on purpose: Mixin
 * treats a declared mixin package as owned territory and refuses to load any other class
 * from it, so the addon must never place its own classes in the client's packages.
 */
public final class BetterBlockEspSettings {

    public static final String MODULE_ID = "block-esp";

    static final String TOGGLE_ID = "bbesp-colors";
    static final String LIST_ID = "bbesp-color-blocks";

    private static final String COLOR_PREFIX = "bbesp-color-";
    private static final String GROUP = "Colors";
    private static final int DEFAULT_COLOR = 0xCCFF3B3B;
    private static final int FORCED_ALPHA = 0xCC000000;

    private static final Map<Block, Integer> ACTIVE_COLORS = new HashMap<>();
    private static final Method ADD_SETTING = resolveAddMethod();

    private static boolean active;

    private BetterBlockEspSettings() {
    }

    /** Adds the fixed settings (toggle + block list) to the built in BlockESP module. */
    public static void install(Module module) {
        if (module == null) return;
        if (module.setting(TOGGLE_ID) == null) {
            addSetting(module, new BoolSetting(TOGGLE_ID, "Per-Block Colors", false)
                .group(GROUP)
                .description("While on, only the blocks picked below are scanned and drawn, each in its own color."));
        }
        if (module.setting(LIST_ID) == null) {
            addSetting(module, RegistryListSetting.blocks(LIST_ID, "Colored Blocks", "")
                .group(GROUP)
                .description("The blocks to scan and color. Nothing else is shown while Per-Block Colors is on.")
                .visibleWhen(() -> enabled(module)));
        }
        active = module.setting(TOGGLE_ID) != null && module.setting(LIST_ID) != null;
    }

    /** Called every client tick: creates the per block color rows and refreshes the map. */
    public static void tick() {
        guardedSync();
    }

    /**
     * Decides which blocks the module scans.
     *
     * <p>{@code collectBoth} reads the module's own {@code blocks} setting to build its scan
     * list. While per-block colors are on and at least one block is picked, that list is
     * replaced by Colored Blocks, so the user only has to choose blocks in one place.
     *
     * @param original the value the module read from its {@code blocks} setting
     * @return the list of blocks that should actually be scanned
     */
    public static String overrideBlocks(String original) {
        if (!active) return original;
        Module module = ModuleRegistry.get(MODULE_ID);
        if (module == null || !enabled(module)) return original;

        List<String> ids = new ArrayList<>();
        for (String raw : coloredBlocks(module)) {
            String id = normalize(raw);
            if (!id.isEmpty()) ids.add(id);
        }
        return ids.isEmpty() ? original : String.join("|", ids);
    }

    /**
     * Resolves the color of a single target block.
     *
     * <p>Blocks outside {@code Colored Blocks} get no color at all while the toggle is on:
     * returning {@code null} makes the mixin drop that target instead of drawing it in the
     * module's base color.
     *
     * @param x/y/z    any point inside the block (its min corner or its center)
     * @param fallback the module's own color, used when the feature is off or nothing is selected
     * @return the block's own color, or {@code null} when the block must not be rendered
     */
    public static Integer colorFor(double x, double y, double z, int fallback) {
        if (!active) return fallback;
        if (ACTIVE_COLORS.isEmpty()) return fallback;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return fallback;
        int blockY = Mth.floor(y);
        if (level.isOutsideBuildHeight(blockY)) return fallback;
        try {
            BlockPos pos = new BlockPos(Mth.floor(x), blockY, Mth.floor(z));
            // null = the block is not in Colored Blocks, so it must not be drawn at all
            return ACTIVE_COLORS.get(level.getBlockState(pos).getBlock());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    static void sync() {
        Module module = ModuleRegistry.get(MODULE_ID);
        if (module == null) return;
        if (module.setting(TOGGLE_ID) == null || module.setting(LIST_ID) == null) install(module);
        if (!active) return;

        for (String raw : coloredBlocks(module)) {
            String id = normalize(raw);
            if (id.isEmpty() || module.setting(COLOR_PREFIX + id) != null) continue;
            String label = AutismRegistryLabels.block(id);
            if (label == null || label.isBlank()) label = id;
            addSetting(module, new ColorSetting(COLOR_PREFIX + id, label, DEFAULT_COLOR)
                .group(GROUP)
                .description("Own color for " + label + ".")
                .visibleWhen(() -> enabled(module) && selected(module, id)));
        }
        rebuildColors(module);
    }

    private static void rebuildColors(Module module) {
        ACTIVE_COLORS.clear();
        if (!enabled(module)) return;
        for (String raw : coloredBlocks(module)) {
            String id = normalize(raw);
            if (id.isEmpty()) continue;
            Setting<?, ?> setting = module.setting(COLOR_PREFIX + id);
            if (!(setting instanceof ColorSetting colorSetting)) continue;
            Integer color = colorSetting.get();
            if (color == null) continue;
            int argb = color;
            if ((argb >>> 24) == 0) argb |= FORCED_ALPHA;
            Block block = resolve(id);
            if (block != null && block != Blocks.AIR && block != Blocks.VOID_AIR) {
                ACTIVE_COLORS.put(block, argb);
            }
        }
    }

    private static void guardedSync() {
        try {
            sync();
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Better Block ESP] settings sync failed", t);
        }
    }

    private static Method resolveAddMethod() {
        try {
            Method method = Module.class.getDeclaredMethod("add", Setting.class);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Better Block ESP] cannot reach Module.add", t);
            return null;
        }
    }

    private static void addSetting(Module module, Setting<?, ?> setting) {
        if (ADD_SETTING == null) return;
        try {
            ADD_SETTING.invoke(module, setting);
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[Better Block ESP] could not add setting '" + setting.id() + "'", t);
        }
    }

    private static List<String> coloredBlocks(Module module) {
        String raw = module.value(LIST_ID);
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String item = part.trim();
            if (!item.isEmpty()) out.add(item);
        }
        return out;
    }

    private static boolean enabled(Module module) {
        return Boolean.parseBoolean(module.value(TOGGLE_ID));
    }

    private static boolean selected(Module module, String id) {
        for (String raw : coloredBlocks(module)) {
            if (id.equals(normalize(raw))) return true;
        }
        return false;
    }

    private static Block resolve(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return null;
        return BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";
        return value.contains(":") ? value : "minecraft:" + value;
    }
}
