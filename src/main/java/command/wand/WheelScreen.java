package command.wand;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class WheelScreen extends Screen {
    /** 右栏控件行数。每行位置固定，隐藏时留空，切换类型不会引起跳动。 */
    private static final int ROW_COUNT = 7;
    private static final int ROW_NAME = 0;
    private static final int ROW_TYPE = 1;
    private static final int ROW_COMMAND = 2;
    private static final int ROW_PARAM = 3;
    private static final int ROW_TRUNCATE = 4;
    private static final int ROW_AT_TARGET = 5;
    private static final int ROW_ACTION = 6;
    /** 哪些行上方要画说明文字。 */
    private static final boolean[] ROW_LABEL = {true, false, true, true, false, false, false};

    /** 屏幕四周最小留白。 */
    private static final int MARGIN_MAX = 10;
    private static final int MARGIN_MIN = 2;
    /** 两栏之间的水平间隙。 */
    private static final int COL_GAP = 18;

    private static final int GAP_MAX = 8;
    private static final int GAP_MIN = 1;
    private static final int CTRL_MAX = 20;
    private static final int CTRL_MIN = 14;
    private static final int LABEL_MAX = 12;
    private static final int LABEL_MIN = 9;
    /** 标题文字本身的高度。 */
    private static final int TITLE_H = 9;

    private final WandConfig cfg = CommandWandClient.config;
    private int editing;

    private int listX;
    private int listW;
    private int panelX;
    private int panelW;
    private int top;
    /** 每行控件的顶边；说明文字画在 rowY[i] - labelH。 */
    private int[] rowY = new int[ROW_COUNT];
    private int rowH;
    private int labelH;
    private int listTop;
    private int slotHeight;
    private int slotGap;

    private EditBox nameBox;
    private FakeCommandBox commandBox;
    private EditBox frequencyBox;

    private Button typeButton;
    private Button chainButton;
    private Button truncateButton;
    private Button atTargetButton;
    private Button clearButton;
    private Button closeButton;

    public WheelScreen() {
        super(Component.literal("命令方块权杖 · 技能配置"));
        editing = cfg.activeSlot;
    }

    @Override
    protected void init() {
        layoutHorizontal();
        layoutVertical();

        nameBox = addRenderableWidget(new EditBox(font, panelX, rowY[ROW_NAME], panelW, rowH, Component.literal("名称")));
        nameBox.setMaxLength(32);
        nameBox.setResponder(s -> slot().name = s);

        typeButton = addRenderableWidget(Button.builder(Component.literal("类型"), b -> {
            slot().type = slot().type.next();
            if (slot().type == SkillType.PULSE) {
                slot().executionSteps();
            }
            syncFields();
            cfg.save();
        }).bounds(panelX, rowY[ROW_TYPE], panelW, rowH).build());

        commandBox = addRenderableWidget(new FakeCommandBox(font, panelX, rowY[ROW_COMMAND], panelW, rowH,
                Component.literal("指令"), this::openCommandEditor));

        chainButton = addRenderableWidget(Button.builder(Component.literal("编辑连锁指令"), b ->
                minecraft.setScreenAndShow(new ChainScreen(slot(), this)))
                .bounds(panelX, rowY[ROW_COMMAND], panelW, rowH).build());

        frequencyBox = addRenderableWidget(new EditBox(font, panelX, rowY[ROW_PARAM], panelW, rowH, Component.literal("频率")));
        frequencyBox.setMaxLength(5);
        frequencyBox.setResponder(s -> slot().interval = parseInt(s, 1, 72000));

        truncateButton = addRenderableWidget(Button.builder(Component.literal("保留小数"), b -> {
            slot().truncateCoordinates = !slot().truncateCoordinates;
            updateTruncateButton();
            cfg.save();
        }).bounds(panelX, rowY[ROW_TRUNCATE], panelW, rowH).build());

        atTargetButton = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            slot().atPlayerName = !slot().atPlayerName;
            updateAtTargetButton();
            cfg.save();
        }).bounds(panelX, rowY[ROW_AT_TARGET], panelW, rowH).build());

        int halfW = (panelW - 6) / 2;
        clearButton = addRenderableWidget(Button.builder(Component.literal("清空"), b -> {
            cfg.slots[editing] = new SkillSlot();
            syncFields();
            cfg.save();
        }).bounds(panelX, rowY[ROW_ACTION], halfW, rowH).build());
        closeButton = addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(panelX + panelW - halfW, rowY[ROW_ACTION], halfW, rowH).build());

        syncFields();
    }

    /**
     * 水平方向：左栏技能列表 + 右栏设置，两栏整体居中。
     * 两栏宽度之和始终不超过 屏宽 - 2*COL_GAP，任何屏宽都不会重叠或溢出。
     */
    private void layoutHorizontal() {
        int avail = width - COL_GAP * 3;
        int panel;
        int list;
        if (avail >= 250) {
            panel = Math.min(300, Math.max(150, avail * 60 / 100));
            list = Math.min(190, Math.max(100, avail - panel));
        } else {
            panel = Math.max(40, avail * 60 / 100);
            list = Math.max(40, avail - panel);
        }
        panelW = panel;
        listW = list;
        // 两栏整体居中，左右留白相等
        int content = listW + COL_GAP + panelW;
        listX = Math.max(MARGIN_MIN, (width - content) / 2);
        panelX = listX + listW + COL_GAP;
    }

    /**
     * 垂直方向：先按理想间距算总高，装不下就依次压缩「行距 → 控件高 → 说明文字高 → 上下留白」，
     * 直到能完整放进屏幕；再让上下留白相等。
     */
    private void layoutVertical() {
        int margin = MARGIN_MAX;
        int gap = GAP_MAX;
        int ctrl = CTRL_MAX;
        int lab = LABEL_MAX;
        int avail = height - margin * 2;
        int need = contentHeight(gap, ctrl, lab);
        while (need > avail) {
            if (gap > GAP_MIN) {
                gap--;
            } else if (ctrl > CTRL_MIN) {
                ctrl--;
            } else if (lab > LABEL_MIN) {
                lab--;
            } else if (margin > MARGIN_MIN) {
                margin--;
                avail = height - margin * 2;
            } else {
                break;
            }
            need = contentHeight(gap, ctrl, lab);
        }
        rowH = ctrl;
        labelH = lab;
        // 上下留白相等。剩余像素为奇数时会差 1 像素，把它垫在最后一行下面
        int slack = Math.max(0, height - need);
        top = Math.max(margin, slack / 2);
        int tailPad = Math.max(0, slack - top * 2);
        need += tailPad;

        int y = top + TITLE_H;
        for (int i = 0; i < ROW_COUNT; i++) {
            y += gap;
            if (i == ROW_COUNT - 1) {
                y += tailPad;
            }
            if (ROW_LABEL[i]) {
                y += lab;
            }
            rowY[i] = y;
            y += ctrl;
        }
        // 技能列表与右栏共用同一段竖直区间，12 个槽位平分，绝不超出
        listTop = top + TITLE_H + gap;
        int listArea = Math.max(WandConfig.SLOTS, top + need - listTop);
        int perSlot = Math.max(1, listArea / WandConfig.SLOTS);
        slotGap = Math.max(1, Math.min(4, perSlot / 8));
        slotHeight = Math.max(11, Math.min(22, perSlot - slotGap));
    }

    /** 右栏内容总高：标题 + 每行前的间距 + 说明文字 + 控件。 */
    private static int contentHeight(int gap, int ctrl, int lab) {
        int labeled = 0;
        for (boolean has : ROW_LABEL) {
            if (has) {
                labeled++;
            }
        }
        return TITLE_H + ROW_COUNT * gap + labeled * lab + ROW_COUNT * ctrl;
    }

    @Override
    protected void setInitialFocus() {
        setFocused(null);
    }

    private SkillSlot slot() { return cfg.slots[editing]; }

    private void openCommandEditor() {
        minecraft.setScreenAndShow(new CommandEditScreen(this, slot().firstCommand(), value -> {
            slot().executionSteps().get(0).command = value;
            commandBox.setDisplay(value);
            refreshSelectorUi();
            cfg.save();
        }));
    }

    private void syncFields() {
        SkillSlot s = slot();
        nameBox.setValue(s.name);
        commandBox.setDisplay(s.firstCommand());
        frequencyBox.setValue(Integer.toString(s.interval));
        updateTypeButton();
        updateVisibility();
        updateTruncateButton();
        updateAtTargetButton();
        updateChainButton();
        moveFocusToVisibleBox();
    }

    private void updateTypeButton() {
        typeButton.setMessage(Component.literal("类型：" + slot().type.label));
    }

    /**
     * 指令文本变化时立即刷新依赖选择器的控件。
     */
    private void refreshSelectorUi() {
        updateVisibility();
        updateTruncateButton();
        updateAtTargetButton();
    }

    private void updateVisibility() {
        boolean single = slot().type == SkillType.SINGLE;
        boolean pulse = slot().type == SkillType.PULSE;
        boolean repeat = slot().type == SkillType.REPEAT;

        boolean commandVisible = single || repeat;
        commandBox.visible = commandBox.active = commandVisible;
        chainButton.visible = chainButton.active = pulse;
        frequencyBox.visible = frequencyBox.active = repeat;
        truncateButton.visible = truncateButton.active = slot().hasForwardSelector();
        atTargetButton.visible = atTargetButton.active = slot().usesSelector('t');
    }

    private void moveFocusToVisibleBox() {
        setFocused(null);
    }

    private void updateTruncateButton() {
        truncateButton.setMessage(Component.literal(slot().truncateCoordinates
                ? "@f坐标取值:保留整数" : "@f坐标取值:保留小数"));
    }

    private void updateAtTargetButton() {
        atTargetButton.setMessage(Component.literal("@t 玩家目标：" + (slot().atPlayerName ? "玩家名" : "UUID")));
    }

    private void updateChainButton() {
        chainButton.setMessage(Component.literal("编辑连锁指令（" + slot().steps.size() + " 条）"));
    }

    private int labelY(int row) {
        return rowY[row] - labelH;
    }

    private int slotY(int index) {
        return listTop + index * (slotHeight + slotGap);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < WandConfig.SLOTS; i++) {
            int y = slotY(i);
            if (event.x() >= listX && event.x() <= listX + listW
                    && event.y() >= y && event.y() <= y + slotHeight) {
                editing = i;
                cfg.activeSlot = i;
                cfg.save();
                syncFields();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 从连锁编辑页返回时刷新摘要，不需要重新 init。
        updateChainButton();
        truncateButton.visible = truncateButton.active = slot().hasForwardSelector();
        atTargetButton.visible = atTargetButton.active = slot().usesSelector('t');

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, "技能列表", listX, top, 0xFFFFFFFF);
        for (int i = 0; i < WandConfig.SLOTS; i++) {
            int y = slotY(i);
            boolean selected = i == editing;
            g.fill(listX, y, listX + listW, y + slotHeight, 0xA0404040);
            g.outline(listX, y, listW, slotHeight, selected ? 0xFFFFFFFF : 0x50505050);
            String text = cfg.slots[i].armed()
                    ? ColorText.plain(cfg.slots[i].name.isBlank() ? cfg.slots[i].firstCommand() : cfg.slots[i].name)
                    : "空技能";
            text = font.plainSubstrByWidth(text, Math.max(40, listW - 38));
            g.text(font, (i + 1) + "  " + text, listX + 7, y + (slotHeight - 9) / 2,
                    selected ? 0xFFFFFFFF : 0xFF808080);
        }
        g.text(font, "第 " + (editing + 1) + " 个技能", panelX, top, 0xFFFFFFFF);
        g.text(font, "名称", panelX, labelY(ROW_NAME), 0xFFB0B0B0);

        switch (slot().type) {
            case SINGLE -> g.text(font, "指令", panelX, labelY(ROW_COMMAND), 0xFFB0B0B0);
            case REPEAT -> g.text(font, "循环指令", panelX, labelY(ROW_COMMAND), 0xFFB0B0B0);
            case PULSE -> g.text(font, "连锁指令", panelX, labelY(ROW_COMMAND), 0xFFB0B0B0);
        }
        if (slot().type == SkillType.REPEAT) {
            g.text(font, "频率（tick）", panelX, labelY(ROW_PARAM), 0xFFB0B0B0);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0101010);
    }

    @Override
    public void onClose() {
        cfg.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int parseInt(String s, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(s.trim()))); }
        catch (RuntimeException ignored) { return min; }
    }
}
