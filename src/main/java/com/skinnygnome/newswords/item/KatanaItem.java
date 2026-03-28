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

public class KatanaItem extends SwordItem {
    private static final int SPECTATOR_COOLDOWN_TICKS = 10 * 20;

    public KatanaItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            boolean activated = NewSwordsMod.activateKatanaSpectator(serverPlayer);
            if (activated) {
                user.getItemCooldownManager().set(this, SPECTATOR_COOLDOWN_TICKS);
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
        tooltip.add(Text.translatable("item.newswords.katana.tooltip.left"));
        tooltip.add(Text.translatable("item.newswords.katana.tooltip.right"));
    }
}
