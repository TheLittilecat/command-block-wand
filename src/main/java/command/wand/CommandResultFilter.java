package command.wand;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

final class CommandResultFilter {
    private CommandResultFilter() {}

    static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || message == null) return true;

            if (!WandController.consumeRecentFill()) return true;
            return !isFillFailure(message);
        });
    }

    private static boolean isFillFailure(Component message) {
        if (hasTranslationKey(message, "commands.fill.failed")) return true;

        for (Component sibling : message.getSiblings()) {
            if (hasTranslationKey(sibling, "commands.fill.failed")) return true;
        }
        return false;
    }

    private static boolean hasTranslationKey(Component component, String key) {
        return component != null
                && component.getContents() instanceof TranslatableContents translatable
                && key.equals(translatable.getKey());
    }
}
