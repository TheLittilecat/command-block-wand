package command.wand;

import com.mojang.brigadier.StringReader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CommandSyntaxColor {
    private static final int GRAY = 0xFFAAAAAA;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int AQUA = 0xFF55FFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;

    private static final UUID PLACEHOLDER_TARGET =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private CommandSyntaxColor() {}

    public static List<ColorText.Seg> parse(Minecraft mc, String command) {
        if (command == null || command.isBlank()) return new ArrayList<>();
        String shown = command.startsWith("/") ? command : "/" + command;
        return ColorText.applyColorCodes(colorize(mc, shown, shown));
    }

    public static String formatDistance(double value) {
        if (!Double.isFinite(value)) return "0";
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 1.0E-9) return Long.toString((long) rounded);
        String text = String.format(Locale.ROOT, "%.2f", value);

        text = text.replaceAll("0+$", "");
        return text.replaceAll("\\.$", "");
    }

    private static List<ColorText.Seg> colorize(Minecraft mc, String probe, String display) {
        List<ColorText.Seg> out = new ArrayList<>();
        if (!isValid(mc, probe)) {
            out.add(new ColorText.Seg(display, RED));
            return out;
        }
        int i = display.startsWith("/") ? 1 : 0;
        int headEnd = i;
        while (headEnd < display.length() && (isName(display.charAt(headEnd)) || display.charAt(headEnd) == ':')) headEnd++;
        out.add(new ColorText.Seg(display.substring(0, headEnd), GRAY));
        i = headEnd;
        while (i < display.length()) {
            char c = display.charAt(i);
            if (c == '@') {
                int end = i + 1;
                if (end < display.length() && isName(display.charAt(end))) end++;
                if (end < display.length() && display.charAt(end) == '[') {
                    int close = bracketEnd(display, end);
                    if (close > 0) end = close;
                }
                out.add(new ColorText.Seg(display.substring(i, end), AQUA));
                i = end;
            } else if (isCoordinateStart(display, i)) {
                int end = coordinateEnd(display, i);
                out.add(new ColorText.Seg(display.substring(i, end), YELLOW));
                i = end;
            } else {
                int end = i;
                while (end < display.length() && display.charAt(end) != '@' && !isCoordinateStart(display, end)) end++;
                out.add(new ColorText.Seg(display.substring(i, end), WHITE));
                i = end;
            }
        }
        return out;
    }

    private static boolean isValid(Minecraft mc, String command) {
        if (mc.getConnection() == null) return command.length() > 2;
        String probe = stripColorCodes(command.startsWith("/") ? command.substring(1) : command);

        probe = replaceTargetSelector(probe);
        if (probe.indexOf('@') < 0 || !containsForward(probe)) {
            return parses(mc, probe);
        }
        StringBuilder builder = new StringBuilder(probe);

        CommandSubstituter.apply(builder, PLACEHOLDER_TARGET.toString(), Vec3.ZERO,
                new Vec3(0, 0, 1), new Vec3(-1, 0, 0), new Vec3(0, 1, 0), false);
        return parses(mc, builder.toString());
    }

    private static String stripColorCodes(String command) {
        if (command.indexOf('§') < 0 && command.indexOf('&') < 0) return command;
        StringBuilder out = new StringBuilder(command.length());
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < command.length()
                    && ChatFormatting.getByCode(command.charAt(i + 1)) != null) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean parses(Minecraft mc, String probe) {
        try {
            var parse = mc.getConnection().getCommands().parse(new StringReader(probe),
                    mc.getConnection().getSuggestionsProvider());
            return parse.getExceptions().isEmpty() && !parse.getReader().canRead();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean containsForward(String command) {
        for (int i = 0; i + 1 < command.length(); i++) {
            if (command.charAt(i) != '@' || command.charAt(i + 1) != 'f') continue;
            if (i + 2 >= command.length() || !isSelectorChar(command.charAt(i + 2))) return true;
        }
        return false;
    }

    private static String replaceTargetSelector(String command) {
        StringBuilder out = new StringBuilder(command.length());
        int i = 0;
        while (i < command.length()) {
            int at = command.indexOf('@', i);
            if (at < 0) {
                out.append(command, i, command.length());
                break;
            }
            out.append(command, i, at);
            boolean isTarget = at + 1 < command.length()
                    && command.charAt(at + 1) == 't'
                    && (at + 2 >= command.length() || !isSelectorChar(command.charAt(at + 2)));
            if (isTarget) {
                out.append("@s");
                i = at + 2;
            } else {
                out.append('@');
                i = at + 1;
            }
        }
        return out.toString();
    }

    private static int bracketEnd(String value, int open) {
        int depth = 0;
        for (int i = open; i < value.length(); i++) {
            if (value.charAt(i) == '[') depth++;
            else if (value.charAt(i) == ']' && --depth == 0) return i + 1;
        }
        return -1;
    }

    private static int coordinateEnd(String value, int from) {
        int end = from;
        if (value.charAt(end) == '~' || value.charAt(end) == '^') end++;
        while (end < value.length() && (isNumber(value.charAt(end)) || value.charAt(end) == '.' || value.charAt(end) == '-')) end++;
        return end;
    }

    private static boolean isCoordinateStart(String value, int index) {
        char c = value.charAt(index);
        if (c == '~' || c == '^') return true;
        if (c == '-') {

            return index == 0 || Character.isWhitespace(value.charAt(index - 1));
        }
        if (isNumber(c)) {
            if (index == 0) return true;
            char prev = value.charAt(index - 1);

            return !isNumber(prev) && prev != '.';
        }
        return false;
    }
    private static boolean isNumber(char c) { return c >= '0' && c <= '9'; }
    private static boolean isName(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-'; }

    private static boolean isSelectorChar(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_'; }
}
