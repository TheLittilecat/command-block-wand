package command.wand;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WandConfig {
    public static final int SLOTS = 12;

    private static String cachedWandId = "";
    private static Item cachedWandItem = Items.AIR;

    public final SkillSlot[] slots = new SkillSlot[SLOTS];
    public int activeSlot = 0;

    /** 作为权杖的物品 ID，可在技能配置页修改。 */
    public String wandItem = "minecraft:stick";
    /** @f 命中方块时：false = 方块自身坐标，true = 命中面外侧的放置位置。 */
    public boolean placeOnFace = false;
    /** @f 落在空气时是否选取空气坐标；false 时只允许落在方块上。 */
    public boolean airCoordinates = true;

    public WandConfig() {
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = new SkillSlot();
        }
    }

    public SkillSlot active() {
        return slots[activeSlot];
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("command_wand.json");
    }

    public static WandConfig load() {
        WandConfig cfg = new WandConfig();
        try {
            Path p = file();
            if (Files.isRegularFile(p)) {
                String raw = Files.readString(p, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
                if (root.has("activeSlot")) {
                    cfg.activeSlot = clampSlot(root.get("activeSlot").getAsInt());
                }
                if (root.has("wandItem")) {
                    cfg.wandItem = root.get("wandItem").getAsString();
                }
                if (root.has("placeOnFace")) {
                    cfg.placeOnFace = root.get("placeOnFace").getAsBoolean();
                }
                if (root.has("airCoordinates")) {
                    cfg.airCoordinates = root.get("airCoordinates").getAsBoolean();
                }
                JsonArray arr = root.has("slots") ? root.getAsJsonArray("slots") : null;
                if (arr != null) {
                    for (int i = 0; i < Math.min(arr.size(), SLOTS); i++) {
                        cfg.slots[i].read(arr.get(i).getAsJsonObject());
                    }
                }
            }
        } catch (Exception ignored) {}
        return cfg;
    }

    public void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("activeSlot", activeSlot);
            root.addProperty("wandItem", wandItem);
            root.addProperty("placeOnFace", placeOnFace);
            root.addProperty("airCoordinates", airCoordinates);
            JsonArray arr = new JsonArray();
            for (SkillSlot slot : slots) {
                arr.add(slot.write());
            }
            root.add("slots", arr);
            Path p = file();
            Files.createDirectories(p.getParent());
            Files.writeString(p, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    /** 当前配置的物品 ID 是否是权杖。ID 变化时才重新查注册表。 */
    public static boolean isWand(ItemStack stack) {
        if (stack == null || stack.isEmpty() || CommandWandClient.config == null) return false;
        String id = CommandWandClient.config.wandItem;
        if (id == null) return false;
        if (!id.equals(cachedWandId)) {
            cachedWandId = id;
            Identifier key = Identifier.tryParse(id.trim());
            cachedWandItem = key == null
                    ? Items.AIR
                    : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
        }
        return cachedWandItem != Items.AIR && stack.is(cachedWandItem);
    }

    public static int clampSlot(int i) {
        return Math.max(0, Math.min(SLOTS - 1, i));
    }
}
