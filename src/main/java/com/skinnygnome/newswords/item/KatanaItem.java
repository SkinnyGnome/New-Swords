package com.skinnygnome.newswords.item;

import com.skinnygnome.newswords.NewSwordsMod;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
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
    private static final int SPECTATOR_COOLDOWN_TICKS = 20 * 20;

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
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!selected || world.isClient || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        float progress = player.getItemCooldownManager().getCooldownProgress(this, 0.0f);
        if (progress <= 0.0f) {
            return;
        }

        int remainingTicks = Math.max(1, Math.round(progress * SPECTATOR_COOLDOWN_TICKS));
        if (remainingTicks % 4 == 0) {
            double remainingSeconds = remainingTicks / 20.0;
            String message = String.format(Locale.ROOT, "Spectate cooldown: %.1fs", remainingSeconds);
            player.sendMessage(Text.literal(message), true);
        }
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
