package com.betteresp;

import autismclient.api.AutismAddon;
import autismclient.api.ApiVersion;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.Setting;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.ModuleRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class BetterEspAddon extends AutismAddon {
    public static final String ID = "better-esp";
    private static final Minecraft MC = Minecraft.getInstance();

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        injectBoxMode();
        LevelRenderEvents.COLLECT_SUBMITS.register(this::onCollectSubmits);
    }

    @Override
    public String getPackage() {
        return "com.betteresp";
    }

    private void injectBoxMode() {
        Field visibleField = null;
        try {
            visibleField = Setting.class.getDeclaredField("visible");
            visibleField.setAccessible(true);
        } catch (Exception ignored) {}

        injectBoxIntoModule("esp", visibleField);
        injectBoxIntoModule("item-esp", visibleField);
    }

    private void injectBoxIntoModule(String moduleId, Field visibleField) {
        Module module = ModuleRegistry.get(moduleId);
        if (module == null) return;

        for (Setting<?, ?> setting : module.settings()) {
            if ("mode".equals(setting.id()) && setting instanceof ChoiceSetting choiceSetting) {
                try {
                    Field choicesField = Setting.class.getDeclaredField("choices");
                    choicesField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<String> current = (List<String>) choicesField.get(choiceSetting);
                    if (!current.contains("Box")) {
                        List<String> updated = new ArrayList<>(current);
                        updated.add("Box");
                        choicesField.set(choiceSetting, List.copyOf(updated));
                    }
                } catch (Exception e) {
                    autismclient.AutismClientAddon.LOG.debug("Better ESP: Failed to inject Box mode into " + moduleId, e);
                }
            } else if ("fill".equals(setting.id()) && visibleField != null) {
                try {
                    visibleField.set(setting, (java.util.function.BooleanSupplier) () -> true);
                } catch (Exception ignored) {}
            }
        }
    }

    private void onCollectSubmits(LevelRenderContext context) {
        if (MC.level == null || MC.player == null) return;

        Module esp = ModuleRegistry.get("esp");
        Module itemEsp = ModuleRegistry.get("item-esp");

        boolean espBox = esp != null && esp.isEnabled() && "Box".equals(esp.value("mode"));
        boolean itemBox = itemEsp != null && itemEsp.isEnabled() && "Box".equals(itemEsp.value("mode"));

        if (!espBox && !itemBox) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        float pt = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        List<Entity> entities = new ArrayList<>();
        List<AABB> boxes = new ArrayList<>();
        List<Boolean> isEsp = new ArrayList<>();

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity == MC.player) continue;
            if (espBox && ModuleRenderUtil.shouldEsp(entity)) {
                entities.add(entity);
                boxes.add(interpolatedBox(entity, pt).move(-camera.x, -camera.y, -camera.z));
                isEsp.add(true);
            } else if (itemBox && ModuleRenderUtil.shouldItemEsp(entity)) {
                entities.add(entity);
                boxes.add(interpolatedBox(entity, pt).move(-camera.x, -camera.y, -camera.z));
                isEsp.add(false);
            }
        }

        if (entities.isEmpty()) return;

        boolean doEspFill = espBox && "true".equals(esp.value("fill"));
        boolean doItemFill = itemBox && "true".equals(itemEsp.value("fill"));

        context.submitNodeCollector().submitCustomGeometry(poseStack,
            AutismRenderTypes.storageEspLinesSeeThrough(), (pose, buffer) -> {
                for (int i = 0, size = entities.size(); i < size; i++) {
                    int color = isEsp.get(i)
                        ? ModuleRenderUtil.espColor(entities.get(i)) | 0xFF000000
                        : ModuleRenderUtil.itemEspColor(entities.get(i)) | 0xFF000000;
                    renderBox(pose, buffer, boxes.get(i), color, 2.0f);
                }
            });

        context.submitNodeCollector().submitCustomGeometry(poseStack,
            AutismRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> {
                for (int i = 0, size = entities.size(); i < size; i++) {
                    boolean isEspEntity = isEsp.get(i);
                    if (isEspEntity && !doEspFill) continue;
                    if (!isEspEntity && !doItemFill) continue;
                    int color = isEspEntity
                        ? ModuleRenderUtil.espColor(entities.get(i))
                        : ModuleRenderUtil.itemEspColor(entities.get(i));
                    int fc = 0x40000000 | (color & 0x00FFFFFF);
                    fillBox(pose, buffer, boxes.get(i), fc);
                }
            });
    }

    private static AABB interpolatedBox(Entity entity, float pt) {
        double dx = entity.getX() - entity.xOld;
        double dy = entity.getY() - entity.yOld;
        double dz = entity.getZ() - entity.zOld;
        double x, y, z;
        if (entity.tickCount <= 1 || dx * dx + dy * dy + dz * dz > 100.0) {
            x = entity.getX();
            y = entity.getY();
            z = entity.getZ();
        } else {
            x = Mth.lerp(pt, entity.xOld, entity.getX());
            y = Mth.lerp(pt, entity.yOld, entity.getY());
            z = Mth.lerp(pt, entity.zOld, entity.getZ());
        }
        double halfW = entity.getBbWidth() / 2.0;
        return new AABB(x - halfW, y, z - halfW, x + halfW, y + entity.getBbHeight(), z + halfW);
    }

    private static final org.joml.Vector3f LINE_NORMAL = new org.joml.Vector3f();

    private static void renderBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        LINE_NORMAL.set(1, 0, 0);
        line(pose, buffer, x1,y1,z1, x2,y1,z1, color, width, LINE_NORMAL);
        line(pose, buffer, x2,y1,z1, x2,y1,z2, color, width, LINE_NORMAL);
        line(pose, buffer, x2,y1,z2, x1,y1,z2, color, width, LINE_NORMAL);
        line(pose, buffer, x1,y1,z2, x1,y1,z1, color, width, LINE_NORMAL);

        line(pose, buffer, x1,y2,z1, x2,y2,z1, color, width, LINE_NORMAL);
        line(pose, buffer, x2,y2,z1, x2,y2,z2, color, width, LINE_NORMAL);
        line(pose, buffer, x2,y2,z2, x1,y2,z2, color, width, LINE_NORMAL);
        line(pose, buffer, x1,y2,z2, x1,y2,z1, color, width, LINE_NORMAL);

        line(pose, buffer, x1,y1,z1, x1,y2,z1, color, width, LINE_NORMAL);
        line(pose, buffer, x2,y1,z1, x2,y2,z1, color, width, LINE_NORMAL);
        line(pose, buffer, x2,y1,z2, x2,y2,z2, color, width, LINE_NORMAL);
        line(pose, buffer, x1,y1,z2, x1,y2,z2, color, width, LINE_NORMAL);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buffer,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             int color, float width, org.joml.Vector3f normal) {
        normal.set((float)(x2 - x1), (float)(y2 - y1), (float)(z2 - z1)).normalize();
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, normal).setLineWidth(width);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, normal).setLineWidth(width);
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        float a = (float) ((color >> 24) & 0xFF) / 255.0f;
        if (a <= 0.0f) return;
        float r = (float) ((color >> 16) & 0xFF) / 255.0f;
        float g = (float) ((color >> 8) & 0xFF) / 255.0f;
        float b = (float) (color & 0xFF) / 255.0f;

        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        addQuad(pose, buffer, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, r,g,b,a);
        addQuad(pose, buffer, x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, r,g,b,a);
        addQuad(pose, buffer, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, r,g,b,a);
        addQuad(pose, buffer, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, r,g,b,a);
        addQuad(pose, buffer, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, r,g,b,a);
        addQuad(pose, buffer, x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, r,g,b,a);
    }

    private static void addQuad(PoseStack.Pose pose, VertexConsumer buffer,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                double x4, double y4, double z4,
                                float r, float g, float b, float a) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(r, g, b, a);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(r, g, b, a);
    }

}
