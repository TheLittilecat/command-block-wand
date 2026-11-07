package command.wand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Overlay;

/**
 * 按住 R 时的技能轮盘。
 *
 * <p>槽位文字沿圆周排布，但每个标签都保持水平（不随角度旋转）——
 * 12 个扇区只有 30°，若文字跟着角度转，顶部和底部的标签会互相压住。
 * 标签与中心之间画一条引导线，指明它属于哪个扇区。
 */
final class WandWheelOverlay extends Overlay {
    private static final double SECTOR_ANGLE = 360.0 / WandConfig.SLOTS;
    /** 标签最大宽度，超出按省略号截断。 */
    private static final int MAX_LABEL_WIDTH = 46;

    private final Minecraft mc = Minecraft.getInstance();
    private int hovered;

    WandWheelOverlay() {
        hovered = CommandWandClient.config.activeSlot;
        mc.mouseHandler.releaseMouse();
    }

    /**
     * 轮盘里鼠标当前指向的槽位。
     *
     * <p>指令框在轮盘打开时展示这个槽位，鼠标划过就能预览对应命令，
     * 不用等到松手选中之后才看到。
     */
    SkillSlot previewSlot() {
        int index = Math.floorMod(hovered, WandConfig.SLOTS);
        return CommandWandClient.config.slots[index];
    }

    @Override
    public boolean isPausing() {
        return false;
    }

    @Override
    public void tick() {
        if (!CommandWandClient.wheelKey.isDown()) {
            CommandWandClient.config.activeSlot = hovered;
            CommandWandClient.config.save();
            mc.gui.setOverlay(null);
            mc.mouseHandler.grabMouse();
        } else if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int radius = Math.max(55, Math.min(120, Math.min(cx, cy) - 55));
        int sector = sectorAt(cx, cy, mouseX, mouseY);
        if (sector >= 0) hovered = sector;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x50000000);
        drawSectorGuides(g, cx, cy, radius);
        // 指令框画在遮罩之上，跟随鼠标指向的槽位
        WandOverlay.drawCommandBox(g, mc, previewSlot());

        for (int i = 0; i < WandConfig.SLOTS; i++) {
            double angle = Math.toRadians(-90.0 + i * SECTOR_ANGLE);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int sx = (int) Math.round(cx + radius * cos);
            int sy = (int) Math.round(cy + radius * sin);

            SkillSlot s = CommandWandClient.config.slots[i];
            boolean selected = i == hovered;
            String label = s.armed() ? ColorText.plain(s.name.isBlank() ? s.firstCommand() : s.name) : "空";
            label = mc.font.plainSubstrByWidth(label, MAX_LABEL_WIDTH);
            int halfWidth = mc.font.width(label) / 2;

            // 文字保持水平：左右两侧的标签向内收，避免超出屏幕边缘
            int textX = sx;
            int clampMargin = 6;
            if (textX - halfWidth < clampMargin) textX = clampMargin + halfWidth;
            if (textX + halfWidth > g.guiWidth() - clampMargin) {
                textX = g.guiWidth() - clampMargin - halfWidth;
            }
            int textY = clampY(sy, g.guiHeight());

            // 引导线：从标签中心连回圆周，说明它属于哪个扇区
            drawLine(g, textX, textY, sx, sy, selected ? 0x60FFFFFF : 0x20FFFFFF);

            g.text(mc.font, label, textX - halfWidth, textY - 5,
                    selected ? 0xFFFFFFFF : 0xFF808080, false);
            g.centeredText(mc.font, Integer.toString(i + 1), textX, textY + 6,
                    selected ? 0xFFFFFFFF : 0xFF606060);
        }
    }

    private static int clampY(int y, int height) {
        return Math.max(12, Math.min(height - 14, y));
    }

    private static int sectorAt(int cx, int cy, int mouseX, int mouseY) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double distance = Math.hypot(dx, dy);
        // 中心安全区：太靠近中心时不切换技能
        if (distance < Math.max(16.0, Math.min(cx, cy) * 0.12)) return -1;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double normalized = Math.floorMod(Math.round((angle + 90.0) / SECTOR_ANGLE), WandConfig.SLOTS);
        return (int) normalized;
    }

    private static void drawSectorGuides(GuiGraphicsExtractor g, int cx, int cy, int outer) {
        int inner = Math.max(30, outer - 55);
        for (int i = 0; i < WandConfig.SLOTS; i++) {
            double angle = Math.toRadians(-90.0 - SECTOR_ANGLE / 2.0 + i * SECTOR_ANGLE);
            int x1 = (int) Math.round(cx + inner * Math.cos(angle));
            int y1 = (int) Math.round(cy + inner * Math.sin(angle));
            int x2 = (int) Math.round(cx + outer * Math.cos(angle));
            int y2 = (int) Math.round(cy + outer * Math.sin(angle));
            drawLine(g, x1, y1, x2, y2, 0x30FFFFFF);
        }
    }

    private static void drawLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + Math.round((x2 - x1) * (float) i / steps);
            int y = y1 + Math.round((y2 - y1) * (float) i / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
