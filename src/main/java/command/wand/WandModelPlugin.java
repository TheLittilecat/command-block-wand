package command.wand;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 让配置里的权杖物品实时使用命令方块权杖模型。
 *
 * <p>不直接覆盖某个物品的 models/item JSON，而是给所有物品模型包一层：
 * 渲染时按当前 wandItem 判断，是权杖就走 command_wand 的模型，否则走原模型。
 * 所以 B 页改完物品 ID 后下一帧就生效，不需要重载资源。
 */
public final class WandModelPlugin implements ModelLoadingPlugin {
    private static final Identifier WAND_MODEL_ID =
            Identifier.fromNamespaceAndPath(CommandWandClient.MOD_ID, "command_wand");

    private static volatile ItemModel wandModel;

    @Override
    public void initialize(Context context) {
        context.modifyItemModelAfterBake().register((model, ctx) -> {
            if (WAND_MODEL_ID.equals(ctx.itemId())) {
                wandModel = model;
                return model;
            }
            return new WandItemModel(model);
        });
    }

    private static final class WandItemModel implements ItemModel {
        private final ItemModel original;

        private WandItemModel(ItemModel original) {
            this.original = original;
        }

        @Override
        public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                           ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
            ItemModel target = wandModel;
            // 防御：若权杖模型本身也被包了一层，避免 update 递归。
            if (target != null && !(target instanceof WandItemModel) && WandConfig.isWand(stack)) {
                target.update(state, stack, resolver, context, level, owner, seed);
            } else {
                original.update(state, stack, resolver, context, level, owner, seed);
            }
        }
    }
}
