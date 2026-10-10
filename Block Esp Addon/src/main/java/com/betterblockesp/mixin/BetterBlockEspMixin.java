package com.betterblockesp.mixin;

import autismclient.modules.Module;
import com.betterblockesp.BetterBlockEspSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BiConsumer;

/**
 * Better Block ESP.
 *
 * <p>{@code ModuleBlockEsp} is package private, so the targets are given by name. Two
 * redirects do the work:
 *
 * <ul>
 *   <li>{@code collectBoth} decides which blocks are scanned by reading the module's
 *       {@code blocks} setting. While per-block colors are on, that value is replaced with
 *       the Colored Blocks list, so a block only has to be picked there.</li>
 *   <li>{@code collect} hands each target to the box/tracer emitters. The color is swapped
 *       on the way out of the chunk cache, so cached scans still store the module's base
 *       color and nothing is invalidated by a color change.</li>
 * </ul>
 */
@Mixin(targets = "autismclient.modules.ModuleBlockEsp", remap = false)
public abstract class BetterBlockEspMixin {

    /** Keeps the module's own scan list unless the toggle is on and blocks were picked. */
    @Redirect(
        method = "collectBoth",
        at = @At(value = "INVOKE", target = "Lautismclient/modules/Module;value(Ljava/lang/String;)Ljava/lang/String;")
    )
    private static String betterBlockEsp$scanTargets(Module module, String key) {
        String original = module.value(key);
        if (!"blocks".equals(key)) return original;
        return BetterBlockEspSettings.overrideBlocks(original);
    }

    @Redirect(
        method = "collect",
        at = @At(value = "INVOKE", target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V")
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void betterBlockEsp$emit(BiConsumer consumer, Object target, Object color) {
        int base = color instanceof Integer value ? value : 0;

        Integer resolved;
        if (target instanceof AABB box) {
            resolved = BetterBlockEspSettings.colorFor(box.minX, box.minY, box.minZ, base);
        } else if (target instanceof Vec3 trace) {
            resolved = BetterBlockEspSettings.colorFor(trace.x, trace.y, trace.z, base);
        } else {
            resolved = base;
        }

        if (resolved == null) return; // not in Colored Blocks -> do not render this block
        consumer.accept(target, resolved);
    }
}
