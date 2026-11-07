package command.wand;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import java.util.concurrent.atomic.AtomicBoolean;

final class ScrollDelta {

    private static volatile double capturedY;

    private static volatile boolean suppressOriginal;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static GLFWScrollCallback original;

    private ScrollDelta() {}

    static void install(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) return;
        GLFWScrollCallback previous = null;
        try {
            long handle = mc.getWindow().handle();
            GLFWScrollCallbackI wrapper = (window, xOffset, yOffset) -> {

                capturedY += yOffset;
                if (suppressOriginal) return;
                if (original != null) original.invoke(window, xOffset, yOffset);
            };
            previous = GLFW.glfwSetScrollCallback(handle, wrapper);
            // previous 为 null 说明窗口原本没有回调；此时仍保留我们的 wrapper，
            // 只是没有需要转发的原回调。
            original = previous;
        } catch (Throwable ignored) {
            INSTALLED.set(false);
            original = null;
            if (previous != null) {
                try {
                    GLFW.glfwSetScrollCallback(mc.getWindow().handle(), previous);
                } catch (Throwable ignoredAgain) {}
            }
        }
    }

    static void setSuppressOriginal(boolean suppress) {
        suppressOriginal = suppress;
    }

    static double takeY() {
        double value = capturedY;
        if (value == 0.0) return 0.0;
        capturedY = 0.0;
        return value;
    }
}
