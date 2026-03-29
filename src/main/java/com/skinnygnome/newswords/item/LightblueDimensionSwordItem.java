package com.skinnygnome.newswords.item;

import com.skinnygnome.newswords.NewSwordsMod;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class LightblueDimensionSwordItem extends SwordItem {
    public LightblueDimensionSwordItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            boolean teleported = NewSwordsMod.teleportLightblueSwordUser(serverPlayer);
            if (teleported) {
                return TypedActionResult.success(stack, false);
            }
            return TypedActionResult.fail(stack);
        }

        return TypedActionResult.success(stack, true);
    }

    @Override
    public void appendTooltip(
            ItemStack stack,
            @Nullable World world,
            List<Text> tooltip,
            TooltipContext context
    ) {
        tooltip.add(Text.translatable("item.newswords.lightblue_dimension_sword.tooltip.left"));
        tooltip.add(Text.translatable("item.newswords.lightblue_dimension_sword.tooltip.right"));
        tooltip.add(Text.translatable("item.newswords.lightblue_dimension_sword.tooltip.r"));
    }
}
