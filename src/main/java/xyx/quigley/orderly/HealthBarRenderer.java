package xyx.quigley.orderly;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.RaycastContext;
import xyx.quigley.orderly.api.UIManager;
import xyx.quigley.orderly.api.config.OrderlyConfig;
import xyx.quigley.orderly.config.OrderlyConfigManager;
import xyx.quigley.orderly.util.RenderUtil;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.StreamSupport;

public class HealthBarRenderer {

    public static void render(MatrixStack matrices, float partialTicks, Camera camera,
                              GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager,
                              Matrix4f projection, Frustum capturedFrustum) {
        var mc = MinecraftClient.getInstance();
        var config = OrderlyConfigManager.getConfig();
        if (mc.world == null
                || (!config.canRenderInF1() && !MinecraftClient.isHudEnabled())
                || !config.canDraw()) {
            return;
        }
        final var cameraEntity = Optional.ofNullable(camera.getFocusedEntity()).orElse(mc.player);
        assert cameraEntity != null : "Camera Entity must not be null!";
        if (config.showingOnlyFocused()) {
            Entity focused = getEntityLookedAt(cameraEntity);
            if (focused instanceof LivingEntity && focused.isAlive()) {
                renderHealthBar((LivingEntity) focused,
                        matrices,
                        partialTicks,
                        camera,
                        cameraEntity
                );
            }
        } else {
            var cameraPos = camera.getPos();
            final Frustum frustum;
            if (capturedFrustum != null) {
                frustum = capturedFrustum;
            } else {
                frustum = new Frustum(matrices.peek().getModel(), projection);
                frustum.setPosition(cameraPos.getX(), cameraPos.getY(), cameraPos.getZ());
            }
            StreamSupport.stream(mc.world.getEntities().spliterator(), false).filter(
                    entity -> entity instanceof LivingEntity
                            && entity != cameraEntity
                            && entity.isAlive()
                            && (Iterables.size(entity.getPassengersDeep()) == 0)
                            && entity.shouldRender(
                            cameraPos.getX(),
                            cameraPos.getY(),
                            cameraPos.getZ())
                            && (entity.ignoreCameraFrustum
                            || frustum.isVisible(entity.getBoundingBox()))
            ).map(LivingEntity.class::cast).forEach(
                    entity -> renderHealthBar(entity, matrices, partialTicks, camera, cameraEntity)
            );
        }
    }

    private static Entity getEntityLookedAt(Entity e) {
        Entity foundEntity = null;
        final double finalDistance = 32;
        var distance = finalDistance;
        var pos = raycast(e, finalDistance);
        var positionVector = e.getPos();
        if (e instanceof PlayerEntity) {
            positionVector = positionVector.add(0, e.getEyeHeight(e.getPose()), 0);
        }
        if (pos != null) {
            distance = pos.getPos().distanceTo(positionVector);
        }
        var lookVector = e.getRotationVector();
        var reachVector = positionVector.add(
                lookVector.x * finalDistance,
                lookVector.y * finalDistance,
                lookVector.z * finalDistance
        );
        Entity lookedEntity = null;
        var entitiesInBoundingBox = e.getEntityWorld().getOtherEntities(
                e,
                e.getBoundingBox()
                        .stretch(
                                lookVector.x * finalDistance,
                                lookVector.y * finalDistance,
                                lookVector.z * finalDistance)
                        .expand(1.0F));
        var minDistance = distance;
        for (var entity : entitiesInBoundingBox) {
            if (entity.collides()) {
                var collisionBox = entity.getVisibilityBoundingBox();
                var interceptPosition = collisionBox.raycast(positionVector, reachVector);
                if (collisionBox.contains(positionVector)) {
                    if (0.0D < minDistance || minDistance == 0.0D) {
                        lookedEntity = entity;
                        minDistance = 0.0D;
                    }
                } else if (interceptPosition.isPresent()) {
                    double distanceToEntity = positionVector.distanceTo(interceptPosition.get());
                    if (distanceToEntity < minDistance || minDistance == 0.0D) {
                        lookedEntity = entity;
                        minDistance = distanceToEntity;
                    }
                }
            }
            if (lookedEntity != null && (minDistance < distance || pos == null)) {
                foundEntity = lookedEntity;
            }
        }
        return foundEntity;
    }

    private static void renderHealthBar(LivingEntity passedEntity,
                                        MatrixStack matrices,
                                        float partialTicks,
                                        Camera camera,
                                        Entity viewPoint) {
        Preconditions.checkNotNull(
                passedEntity,
                "tried to render health bar for null entity"
        );
        OrderlyConfig config = OrderlyConfigManager.getConfig();
        var style = UIManager.getCurrentStyle();

        var mc = MinecraftClient.getInstance();
        var passengerStack = new Stack<LivingEntity>();
        var entity = passedEntity;
        passengerStack.push(entity);
        while (entity.getPrimaryPassenger() instanceof LivingEntity) {
            entity = (LivingEntity) entity.getPrimaryPassenger();
            passengerStack.push(entity);
        }
        matrices.push();
        while (!passengerStack.isEmpty()) {
            entity = passengerStack.pop();
            if (!entity.isAlive()) continue;
            var idString = String.valueOf(Registry.ENTITY_TYPE.getId(entity.getType()));
            boolean boss = config.getBosses().contains(idString);
            if (config.getBlacklist().contains(idString)) {
                continue;
            }
            processing:
            {
                float distance = passedEntity.distanceTo(viewPoint);
                if (distance > config.getMaxDistance()
                        || !passedEntity.canSee(viewPoint)
                        || entity.isInvisible()) {
                    break processing;
                }
                if (boss && !config.canShowOnBosses()) {
                    break processing;
                }
                if (!config.canShowOnPlayers() && entity instanceof PlayerEntity) {
                    break processing;
                }
                if (entity.getMaxHealth() <= 0.0F) {
                    break processing;
                }
                var x = passedEntity.prevX
                        + (passedEntity.getX() - passedEntity.prevX)
                        * partialTicks;
                var y = passedEntity.prevY
                        + (passedEntity.getY() - passedEntity.prevY)
                        * partialTicks;
                var z = passedEntity.prevZ
                        + (passedEntity.getZ() - passedEntity.prevZ)
                        * partialTicks;

                var renderManager = MinecraftClient.getInstance()
                        .getEntityRenderDispatcher();
                matrices.push();
                {
                    matrices.translate(
                            x - renderManager.camera.getPos().x,
                            y - renderManager.camera.getPos().y
                                    + passedEntity.getHeight()
                                    + config.getHeightAbove(),
                            z - renderManager.camera.getPos().z);
                    var normalMatrix = matrices.peek().getNormal();
                    var translateMatrix = new Matrix3f();
                    translateMatrix.set(0, 0, 1.0F);
                    translateMatrix.set(1, 1, 1.0F);
                    translateMatrix.set(2, 2, 1.0F);
                    translateMatrix.set(1, 2, 1.0F);
                    normalMatrix.multiply(translateMatrix);
                    DiffuseLighting.disableGuiDepthLighting();
                    var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
                    var icon = RenderUtil.getIcon(entity, boss);
                    final int light = 0xF000F0;
                    if (boss) {
                        style.renderBossEntity(matrices, immediate, camera, config, entity, light
                                , icon);
                    } else {
                        style.renderEntity(matrices, immediate, camera, config, entity, light, icon);
                    }
                }
                matrices.pop();
                matrices.translate(
                        0.0D,
                        -(config.getBackgroundHeight()
                                + config.getBarHeight()
                                + config.getBackgroundPadding()),
                        0.0D
                );
            }
        }
        matrices.pop();
    }

    @Nullable
    private static HitResult raycast(Entity entity, double len) {
        var vec = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        if (entity instanceof PlayerEntity) {
            vec = vec.add(new Vec3d(0, entity.getEyeHeight(entity.getPose()), 0));
        }
        var look = entity.getRotationVector();
        if (look == null) {
            return null;
        }
        return raycast(entity, vec, look, len);
    }

    private static HitResult raycast(Entity entity, Vec3d origin, Vec3d ray, double len) {
        var next = origin.add(ray.normalize().multiply(len));
        return entity.getEntityWorld().raycast(new RaycastContext(origin, next,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
    }
}
