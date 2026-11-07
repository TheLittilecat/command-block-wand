package command.wand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

final class WandGameMode extends MultiPlayerGameMode {
    private final Minecraft mc;

    WandGameMode(Minecraft mc, ClientPacketListener connection) {
        super(mc, connection);
        this.mc = mc;
    }

    private boolean wand(InteractionHand hand) {
        LocalPlayer player = mc.player;
        return player != null && WandConfig.isWand(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useItem(Player player, InteractionHand hand) {
        if (wand(hand)) {
            return InteractionResult.FAIL;
        }
        return super.useItem(player, hand);
    }

    @Override
    public InteractionResult useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult result) {
        if (wand(hand)) {
            return InteractionResult.FAIL;
        }
        return super.useItemOn(player, hand, result);
    }

    @Override
    public InteractionResult interact(Player player, Entity entity, EntityHitResult hit, InteractionHand hand) {
        if (wand(hand)) {
            return InteractionResult.FAIL;
        }
        return super.interact(player, entity, hit, hand);
    }

    @Override
    public void releaseUsingItem(Player player) {
        if (player.isUsingItem() && wand(player.getUsedItemHand())) {
            return;
        }
        super.releaseUsingItem(player);
    }
}
