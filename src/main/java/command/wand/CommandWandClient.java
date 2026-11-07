package command.wand;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CommandWandClient implements ClientModInitializer {
    public static final String MOD_ID = "command_wand";
    static KeyMapping wheelKey;
    static KeyMapping configKey;
    static KeyMapping settingsKey;
    static WandConfig config;
    static WandController controller;

    @Override
    public void onInitializeClient() {
        config = WandConfig.load();
        controller = new WandController();
        ModelLoadingPlugin.register(new WandModelPlugin());

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        wheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".wheel", GLFW.GLFW_KEY_R, category));
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".config", GLFW.GLFW_KEY_N, category));
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".settings", GLFW.GLFW_KEY_B, category));

        ClientTickEvents.START_CLIENT_TICK.register(mc -> controller.lockAltScroll(mc));
        ClientTickEvents.END_CLIENT_TICK.register(controller::tick);

        CommandResultFilter.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> ScrollDelta.install(mc));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "overlay"), new WandOverlay());
    }
}
