package command.wand;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 只负责显示指令的假编辑框：点击后打开真正的指令编辑页。
 * 外观沿用原版 EditBox，但不可直接输入。
 */
final class FakeCommandBox extends EditBox {
    private final Runnable onClick;

    FakeCommandBox(Font font, int x, int y, int width, int height, Component message, Runnable onClick) {
        super(font, x, y, width, height, message);
        this.onClick = onClick;
        setMaxLength(32500);
        setEditable(false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (active && visible && isMouseOver(event.x(), event.y())) {
            onClick.run();
            return true;
        }
        return false;
    }

    void setDisplay(String text) {
        super.setValue(text == null ? "" : text);
        setCursorPosition(0);
        setHighlightPos(0);
    }
}
