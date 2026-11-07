package command.wand;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 全局设置页：权杖物品 ID 与 @f 方块坐标模式。 */
public final class SettingsScreen extends Screen {
    private static final int LABEL_W = 140;
    private static final int CONTROL_W = 240;
    private static final int GAP = 14;
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 16;

    private final WandConfig cfg = CommandWandClient.config;

    private EditBox itemBox;
    private Button modeButton;
    private Button airButton;
    private int leftX;
    private int controlX;
    private int firstRowY;

    public SettingsScreen() {
        super(Component.literal("全局设置"));
    }

    @Override
    protected void init() {
        int totalW = LABEL_W + GAP + CONTROL_W;
        leftX = (width - totalW) / 2;
        controlX = leftX + LABEL_W + GAP;
        firstRowY = Math.max(50, height / 2 - 40);

        itemBox = addRenderableWidget(new EditBox(font, controlX, firstRowY, CONTROL_W, ROW_H, Component.literal("")));
        itemBox.setMaxLength(128);
        itemBox.setValue(cfg.wandItem);
        itemBox.setResponder(value -> cfg.wandItem = value.trim());

        modeButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            cfg.placeOnFace = !cfg.placeOnFace;
            updateModeButton();
            cfg.save();
        }).bounds(controlX, firstRowY + ROW_H + ROW_GAP, CONTROL_W, ROW_H).build());
        updateModeButton();

        airButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            cfg.airCoordinates = !cfg.airCoordinates;
            updateAirButton();
            cfg.save();
        }).bounds(controlX, firstRowY + (ROW_H + ROW_GAP) * 2, CONTROL_W, ROW_H).build());
        updateAirButton();

        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(width / 2 - 60, firstRowY + (ROW_H + ROW_GAP) * 3 + 18, 120, 20).build());
    }

    private void updateModeButton() {
        modeButton.setMessage(Component.literal(cfg.placeOnFace ? "相邻方块" : "方块自身"));
    }

    private void updateAirButton() {
        airButton.setMessage(Component.literal(cfg.airCoordinates ? "选取" : "不选取"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawRow(g, firstRowY, "权杖物品设置");
        drawRow(g, firstRowY + ROW_H + ROW_GAP, "@f 方块模式");
        drawRow(g, firstRowY + (ROW_H + ROW_GAP) * 2, "@f 空气坐标");
    }

    /** 左侧名称，右侧控件，中间画一条分隔线。 */
    private void drawRow(GuiGraphicsExtractor g, int y, String label) {
        g.text(font, label, leftX, y + (ROW_H - 8) / 2, 0xFFE0E0E0);
        int lineStart = leftX + font.width(label) + 8;
        int lineEnd = controlX - 8;
        if (lineEnd > lineStart) {
            g.fill(lineStart, y + ROW_H / 2, lineEnd, y + ROW_H / 2 + 1, 0xFF606060);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xE0101010);
    }

    @Override
    public void onClose() {
        cfg.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
