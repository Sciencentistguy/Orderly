package xyx.quigley.orderly.ui;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import xyx.quigley.orderly.api.config.OrderlyConfig;

import javax.annotation.Nullable;

public class SaoUIStyle extends SimpleUIStyle {

    @Override
    protected void render(MatrixStack matrices,
                          VertexConsumerProvider.Immediate immediate,
                          Camera camera,
                          OrderlyConfig config,
                          LivingEntity entity,
                          int light,
                          @Nullable ItemStack icon,
                          boolean boss) {
        // TODO this appears to be unimplemented
    }
}
