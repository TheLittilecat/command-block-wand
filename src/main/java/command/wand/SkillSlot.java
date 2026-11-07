package command.wand;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class SkillSlot {

    public static final double DEFAULT_DISTANCE = 0.5;

    public static final double SCROLL_STEP_MIN = 0.9;

    public static final double SCROLL_STEP_MAX = 75.0;

    public static final double SCROLL_SPEED_FULL = 5.0;

    public static final double SCROLL_PROPORTION = 0.24;

    public static final double MIN_DISTANCE = 0.5;
    public static final double MAX_DISTANCE = 100.0;

    /** 一条指令。{@code delay} 是距上一条指令（第一条为距施法）的 tick 数。 */
    public static final class Step {
        public String command;
        public int delay;

        public Step(String command, int delay) {
            this.command = command == null ? "" : command;
            this.delay = Math.max(0, delay);
        }
    }

    public String name = "";
    public SkillType type = SkillType.SINGLE;
    public int interval = 10;

    /** 所有模式共用：单发/循环取第一条，连锁按顺序执行全部。 */
    public final List<Step> steps = new ArrayList<>();

    public double distance = DEFAULT_DISTANCE;

    public boolean truncateCoordinates = false;
    /** @t 目标为玩家时：false = UUID，true = 玩家名。 */
    public boolean atPlayerName = false;

    public SkillSlot() {
        steps.add(new Step("", 0));
    }

    /** 真正执行时使用的第一条指令。 */
    public String firstCommand() {
        return steps.isEmpty() ? "" : steps.get(0).command;
    }

    public List<Step> executionSteps() {
        if (steps.isEmpty()) {
            steps.add(new Step("", 0));
        }
        return steps;
    }

    public boolean armed() {
        if (type == SkillType.PULSE) {
            for (Step step : steps) {
                if (step.command != null && !step.command.isBlank()) return true;
            }
            return false;
        }
        String command = firstCommand();
        return command != null && !command.isBlank();
    }

    public boolean hasForwardSelector() {
        return usesSelector('f');
    }

    /** 指令里是否出现选择器 {@code @kind}；连锁模式检查所有步骤。 */
    public boolean usesSelector(char kind) {
        for (Step step : steps) {
            if (hasSelector(step.command, kind)) return true;
        }
        return false;
    }

    /**
     * 是否包含选择器 {@code @kind}。
     *
     * <p>要求后面紧跟选择器字符或结束（空格、标点等），避免把 {@code @foo} 误判成 {@code @f}。
     */
    private static boolean hasSelector(String command, char kind) {
        if (command == null) return false;
        for (int i = 0; i + 1 < command.length(); i++) {
            if (command.charAt(i) != '@' || command.charAt(i + 1) != kind) continue;
            int next = i + 2;
            if (next >= command.length()) return true;
            char c = command.charAt(next);
            boolean nameChar = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                    || c >= '0' && c <= '9' || c == '_';
            if (!nameChar) return true;
        }
        return false;
    }

    public void read(JsonObject o) {
        if (o.has("name")) name = o.get("name").getAsString();
        if (o.has("type")) {
            try { type = SkillType.valueOf(o.get("type").getAsString()); }
            catch (IllegalArgumentException ignored) { type = SkillType.SINGLE; }
        }
        if (o.has("interval")) interval = Math.max(1, o.get("interval").getAsInt());
        if (o.has("distance")) distance = clampDistance(o.get("distance").getAsDouble());
        if (o.has("truncateCoordinates")) truncateCoordinates = o.get("truncateCoordinates").getAsBoolean();
        if (o.has("atPlayerName")) atPlayerName = o.get("atPlayerName").getAsBoolean();

        steps.clear();
        if (o.has("steps") && o.get("steps").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("steps");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject s = arr.get(i).getAsJsonObject();
                String command = s.has("command") ? s.get("command").getAsString() : "";
                int stepDelay = s.has("delay") ? Math.max(0, s.get("delay").getAsInt()) : 0;
                steps.add(new Step(command, stepDelay));
            }
        }
        if (steps.isEmpty()) {
            steps.add(new Step("", 0));
        }
    }

    public JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", type.name());
        o.addProperty("interval", interval);
        o.addProperty("distance", distance);
        o.addProperty("truncateCoordinates", truncateCoordinates);
        o.addProperty("atPlayerName", atPlayerName);

        JsonArray arr = new JsonArray();
        for (Step step : steps) {
            JsonObject s = new JsonObject();
            s.addProperty("command", step.command);
            s.addProperty("delay", step.delay);
            arr.add(s);
        }
        o.add("steps", arr);
        return o;
    }

    public static double clampDistance(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return DEFAULT_DISTANCE;
        return Math.min(MAX_DISTANCE, Math.max(MIN_DISTANCE, d));
    }

    public static double scrollDelta(double current, double rawScroll) {
        double t = Math.min(1.0, Math.abs(rawScroll) / SCROLL_SPEED_FULL);

        double speedFactor = 0.5 + 0.5 * t;

        double proportional = Math.max(SCROLL_STEP_MIN,
                Math.min(SCROLL_STEP_MAX, current * SCROLL_PROPORTION));
        double step = proportional * speedFactor;
        return rawScroll < 0 ? -step : step;
    }
}
