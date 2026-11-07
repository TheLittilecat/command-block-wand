package command.wand;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Consumer;

/** 纯净指令编辑页：标题、贯穿屏幕的输入框、保存/取消。 */
public final class CommandEditScreen extends Screen {
    private static final int MARGIN = 20;

    private final Screen parent;
    private final String initial;
    private final Consumer<String> onSave;

    private ProbeEditBox input;
    private CommandSuggestions suggestions;
    private String draft;

    public CommandEditScreen(Screen parent, String initial, Consumer<String> onSave) {
        super(Component.literal("指令编辑"));
        this.parent = parent;
        this.initial = initial == null ? "" : initial;
        this.draft = this.initial;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        // 窗口缩放会再次 init；保留当前草稿，不要退回初始文本。
        if (input != null) {
            draft = input.realValue();
        }
        int inputY = Math.max(56, Math.min(height * 3 / 4, height - 78));
        input = addRenderableWidget(new ProbeEditBox(font, MARGIN, inputY, width - MARGIN * 2, 20, Component.literal("指令")));
        input.setMaxLength(32500);
        String text = (draft == null ? initial : draft).strip();
        if (!text.startsWith("/")) text = "/" + text;
        input.setValue(text);
        input.setResponder(value -> {
            draft = input.realValue();
            if (suggestions != null) suggestions.updateCommandInfo();
        });
        input.setCursorPosition(input.realValue().length());

        // 补全列表默认在输入框下方；BottomSuggestions 会把它移到输入框上方。
        suggestions = new BottomSuggestions(minecraft, this, input, font);
        suggestions.setAllowSuggestions(true);

        int buttonW = 100;
        int buttonY = Math.min(inputY + 28, height - 24);
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(width / 2 - buttonW - 5, buttonY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> cancel())
                .bounds(width / 2 + 5, buttonY, buttonW, 20).build());

        setInitialFocus(input);
        suggestions.updateCommandInfo();
    }

    private void save() {
        String value = input.realValue().strip();
        while (value.startsWith("/")) value = value.substring(1);
        onSave.accept(value);
        CommandWandClient.config.save();
        minecraft.setScreenAndShow(parent);
    }

    private void cancel() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (suggestions != null && suggestions.keyPressed(event)) return true;
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (suggestions != null && suggestions.mouseClicked(event)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (suggestions != null && suggestions.mouseScrolled(scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, "指令编辑", (width - font.width("指令编辑")) / 2, 24, 0xFFFFFFFF);
        if (suggestions != null) {
            suggestions.extractRenderState(g, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xE0101010);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 原版补全列表只会画在输入框下方（或屏幕底部）。
     * 这里在列表创建后把它的矩形移到输入框上方，保证向上展开且不挡按钮。
     */
    private static final class BottomSuggestions extends CommandSuggestions {
        private final ProbeEditBox box;
        private final Screen screen;
        private final Font font;

        private BottomSuggestions(net.minecraft.client.Minecraft mc, Screen screen, ProbeEditBox box, Font font) {
            super(mc, screen, box, font, false, false, 1, 10, false, 0);
            this.box = box;
            this.screen = screen;
            this.font = font;
        }

        @Override
        public void showSuggestions(boolean nested) {
            super.showSuggestions(nested);
            moveAboveInput();
        }

        /**
         * 原版把 usage/错误文本画在顶部（或屏幕底部）；这里改成按钮下方，
         * 空间不足时放输入框上方，避免“未知的方块类型”留在原位置。
         */
        @Override
        @SuppressWarnings("unchecked")
        public void extractUsage(GuiGraphicsExtractor g) {
            if (USAGE_FIELD == null) {
                super.extractUsage(g);
                return;
            }
            List<FormattedCharSequence> lines;
            try {
                lines = (List<FormattedCharSequence>) USAGE_FIELD.get(this);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                super.extractUsage(g);
                return;
            }
            if (lines == null || lines.isEmpty()) return;

            int lineHeight = 12;
            int total = lines.size() * lineHeight;
            int below = box.getY() + 50;
            int y = below + total <= screen.height - 4
                    ? below
                    : Math.max(4, box.getY() - total - 4);
            for (FormattedCharSequence line : lines) {
                g.text(font, line, MARGIN, y, 0xFFAAAAAA);
                y += lineHeight;
            }
        }

        private void moveAboveInput() {
            try {
                Object list = SUGGESTIONS_FIELD == null ? null : SUGGESTIONS_FIELD.get(this);
                if (list == null) return;

                Field rectField = findFieldByType(list.getClass(), Rect2i.class);
                if (rectField == null) return;
                Rect2i rect = (Rect2i) rectField.get(list);
                int targetY = box.getY() - rect.getHeight() - 3;
                rect.setY(Math.max(4, targetY));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 反射失败时退回原版位置，不影响补全功能。
            }
        }

        /**
         * 不按字段名反射，避免生产环境 intermediary 重映射后名字变化。
         * 列表字段是唯一一个自身声明了 Rect2i 字段的字段。
         */
        private static final Field SUGGESTIONS_FIELD = findSuggestionsField();
        private static final Field USAGE_FIELD = findUsageField();

        private static Field findUsageField() {
            for (Field field : CommandSuggestions.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            return null;
        }

        private static Field findSuggestionsField() {
            for (Field field : CommandSuggestions.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                for (Field inner : field.getType().getDeclaredFields()) {
                    if (inner.getType() == Rect2i.class) {
                        field.setAccessible(true);
                        return field;
                    }
                }
            }
            return null;
        }

        private static Field findFieldByType(Class<?> owner, Class<?> type) {
            for (Field field : owner.getDeclaredFields()) {
                if (field.getType() == type) {
                    field.setAccessible(true);
                    return field;
                }
            }
            return null;
        }
    }
}
