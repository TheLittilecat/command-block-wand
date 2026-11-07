package command.wand;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 连锁技能的指令序列编辑页。 */
final class ChainScreen extends Screen {
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 8;
    private static final int LIST_TOP = 54;
    private static final int MARGIN = 20;

    private final SkillSlot slot;
    private final Screen parent;
    private int scroll;

    private FakeCommandBox firstCommandBox;

    ChainScreen(SkillSlot slot, Screen parent) {
        super(Component.literal("命令方块权杖 · 指令列表"));
        this.slot = slot;
        this.parent = parent;
    }

    @Override
    protected void init() {
        clampScroll();
        List<SkillSlot.Step> steps = slot.executionSteps();

        int commandX = MARGIN + 28;
        int delayW = 64;
        int delayX = width - MARGIN - delayW - 70;
        int commandW = Math.max(80, delayX - commandX - 12);

        firstCommandBox = null;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= steps.size()) break;
            SkillSlot.Step step = steps.get(index);
            int y = LIST_TOP + row * (ROW_H + ROW_GAP);

            FakeCommandBox command = new FakeCommandBox(font, commandX, y, commandW, ROW_H,
                    Component.literal("指令"), () -> minecraft.setScreenAndShow(new CommandEditScreen(
                    this, step.command, value -> step.command = value)));
            command.setDisplay(step.command);
            addRenderableWidget(command);
            if (firstCommandBox == null) firstCommandBox = command;

            EditBox delay = new EditBox(font, delayX, y, delayW, ROW_H, Component.literal("延迟"));
            delay.setMaxLength(6);
            delay.setValue(Integer.toString(step.delay));
            delay.setResponder(value -> step.delay = parseInt(value, 0, 72000));
            addRenderableWidget(delay);

            final int removeIndex = index;
            Button remove = Button.builder(Component.literal("删除"), b -> {
                List<SkillSlot.Step> current = slot.executionSteps();
                if (current.size() > 1) {
                    current.remove(removeIndex);
                } else if (!current.isEmpty()) {
                    current.get(0).command = "";
                    current.get(0).delay = 0;
                }
                clampScroll();
                rebuildWidgets();
            }).bounds(width - MARGIN - 60, y, 60, ROW_H).build();
            addRenderableWidget(remove);
        }

        Button add = Button.builder(Component.literal("+ 添加指令"), b -> {
            slot.executionSteps().add(new SkillSlot.Step("", 20));
            scroll = Math.max(0, slot.steps.size() - VISIBLE_ROWS);
            rebuildWidgets();
        }).bounds(MARGIN, height - 34, 110, 22).build();
        addRenderableWidget(add);

        Button done = Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(width - MARGIN - 90, height - 34, 90, 22).build();
        addRenderableWidget(done);

        setFocused(null);
    }

    private void clampScroll() {
        int max = Math.max(0, slot.executionSteps().size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int before = scroll;
        scroll += scrollY > 0 ? -1 : 1;
        clampScroll();
        if (scroll != before) {
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        g.text(font, "指令列表", MARGIN, 16, 0xFFFFFFFF);
        g.text(font, "指令", MARGIN + 28, LIST_TOP - 14, 0xFFB0B0B0);
        g.text(font, "延迟（tick）", width - MARGIN - 64 - 70, LIST_TOP - 14, 0xFFB0B0B0);

        List<SkillSlot.Step> steps = slot.executionSteps();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= steps.size()) break;
            int y = LIST_TOP + row * (ROW_H + ROW_GAP);
            g.text(font, (index + 1) + ".", MARGIN, y + 7, 0xFFB0B0B0);
        }

        if (steps.size() > VISIBLE_ROWS) {
            g.text(font, (scroll + 1) + "-" + Math.min(steps.size(), scroll + VISIBLE_ROWS)
                    + " / " + steps.size(), MARGIN + 120, height - 30, 0xFF909090);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0101010);
    }

    @Override
    public void onClose() {
        CommandWandClient.config.save();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int parseInt(String value, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (RuntimeException ignored) {
            return min;
        }
    }
}
