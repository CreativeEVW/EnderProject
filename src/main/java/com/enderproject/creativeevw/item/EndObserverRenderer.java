package com.enderproject.creativeevw.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class EndObserverRenderer implements ICurioRenderer {

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
                                                                           PoseStack poseStack, RenderLayerParent<T, M> renderLayerParent,
                                                                           MultiBufferSource buffer, int packedLight,
                                                                           float limbSwing, float limbSwingAmount,
                                                                           float partialTicks, float ageInTicks,
                                                                           float netHeadYaw, float headPitch) {
        if (!(renderLayerParent.getModel() instanceof HeadedModel headedModel)) return;

        poseStack.pushPose();
        // 绑定头部骨骼旋转和平移
        headedModel.getHead().translateAndRotate(poseStack);
        // 头部 pivot 居中，8px 头高 → pivot 上方 4px 到顶 + 2px 偏移 = 6px = 0.375 格
        poseStack.translate(0.0, -0.5, 0.0);
        poseStack.scale(0.65F, 0.65F, 0.65F);

        LivingEntity entity = slotContext.entity();
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.HEAD,
                0xF000F0, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), 0);
        poseStack.popPose();
    }
}
