package command.wand;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/**
 * 右上角的命令框与距离读数。
 *
 * <p>显示时机与屏幕白点一致：只在有活跃白点时出现，没有白点就连同指令框一起消失。
 * 轮盘界面（R）下同样显示，并跟随轮盘里指向的那个槽位而不是当前生效的槽位。
 */
public final class WandOverlay implements HudElement {
    private static final float COMMAND_SCALE = 1.5F;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null || mc.gui.overlay() != null) return;
        if (!WandConfig.isWand(player.getInventory().getSelectedItem())) return;

        float partialTick = dt.getGameTimeDeltaPartialTick(false);
        SkillSlot slot = CommandWandClient.config.active();

        // 距离数字只看是否在调距，不受白点影响
        if (mc.hasAltDown() && slot.hasForwardSelector()) {
            drawCrosshairDistance(g, mc, slot);
        }

        // 指令框与白点同进同出：没有白点就不画，指令框也跟着消失
        if (CommandWandClient.controller.renderScreenDots(g, mc, partialTick)) {
            drawCommandBox(g, mc, slot);
        }
    }

    /** 准星上方的距离数字，仅在按住 Alt 调距时出现。 */
    private static void drawCrosshairDistance(GuiGraphicsExtractor g, Minecraft mc, SkillSlot slot) {
        String text = CommandSyntaxColor.formatDistance(slot.distance);
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int w = mc.font.width(text) + 8;
        int h = mc.font.lineHeight + 4;
        int x = cx - w / 2;
        int y = cy - 26;
        g.fill(x, y, x + w, y + h, 0xC0000000);
        g.text(mc.font, text, x + 4, y + 2, 0xFFFFFF55, true);
    }

    /**
     * 画右上角的命令框。
     *
     * <p>轮盘界面在遮罩之上单独调用此方法——HUD 层渲染在 overlay 之前，
     * 靠 HUD 画会被轮盘的半透明遮罩压住。
     */
    static void drawCommandBox(GuiGraphicsExtractor g, Minecraft mc, SkillSlot slot) {
        List<ColorText.Seg> segments = segments(mc, slot);
        int textWidth = ColorText.width(mc.font, segments);
        if (textWidth <= 0) return;

        int localWidth = textWidth + 12;
        int localHeight = mc.font.lineHeight + 10;
        int boxWidth = (int) Math.ceil(localWidth * COMMAND_SCALE);
        int boxHeight = (int) Math.ceil(localHeight * COMMAND_SCALE);
        int left = g.guiWidth() - boxWidth - 10;
        int top = 10;

        g.pose().pushMatrix();
        g.pose().translate((float) left, (float) top);
        g.pose().scale(COMMAND_SCALE, COMMAND_SCALE);
        g.fill(0, 0, localWidth, localHeight, 0xE0000000);
        ColorText.draw(g, mc.font, segments, 6, 5, true);
        g.pose().popMatrix();

        float flash = CommandWandClient.controller.flashStrength();
        if (flash > 0.0F) {
            int alpha = (int) (255 * Math.min(1.0F, flash));
            int argb = (alpha << 24) | 0xFFFFFF;
            g.outline(left - 1, top - 1, left + boxWidth + 1, top + boxHeight + 1, argb);
        }
    }

    private static List<ColorText.Seg> segments(Minecraft mc, SkillSlot slot) {
        return CommandSyntaxColor.parse(mc, slot.armed() ? slot.firstCommand() : "");
    }
}
