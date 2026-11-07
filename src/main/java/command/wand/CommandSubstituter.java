package command.wand;

import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class CommandSubstituter {
    private CommandSubstituter() {}

    public static String apply(StringBuilder cmd, String targetReplacement, Vec3 anchor,
                               Vec3 forward, Vec3 left, Vec3 up, boolean truncate) {

        Vec3 exec = anchor;
        boolean anchored = false;
        int i = 0;
        while (i + 1 < cmd.length()) {
            if (cmd.charAt(i) != '@') {
                i++;
                continue;
            }
            char kind = cmd.charAt(i + 1);
            boolean boundary = i + 2 >= cmd.length() || !isNameChar(cmd.charAt(i + 2));
            if (kind == 't' && boundary) {
                if (targetReplacement == null || targetReplacement.isBlank()) return "@t";
                cmd.replace(i, i + 2, targetReplacement);
                i += targetReplacement.length();
                continue;
            }
            if (kind == 'f' && boundary) {
                Vec3[] base = {anchor};
                int end = consumeTrio(cmd, i + 2, base, forward, left, up);
                exec = base[0];
                anchored = true;
                String coords = format(truncate ? truncate(exec) : exec);
                cmd.replace(i, end, coords);
                i += coords.length();
                continue;
            }
            i += 2;
        }
        if (anchored) {
            rewriteRelativeTo(cmd, exec, forward, left, up, truncate);
        }
        return null;
    }

    // ---- 坐标后缀扫描 ----

    public static int forwardSuffixEnd(CharSequence text, int from) {
        return scanSuffix(text, from).end();
    }

    public static int resolveRelativeRun(CharSequence cmd, int start, Vec3 origin,
                                         Vec3 forward, Vec3 left, Vec3 up, String[] out) {
        double[] axes = {origin.x, origin.y, origin.z};
        int pos = start;
        int lastEnd = start;
        int count = 0;
        while (count < 3) {
            int tokenStart = pos;
            int spaces = 0;
            while (tokenStart < cmd.length() && cmd.charAt(tokenStart) == ' ') {
                tokenStart++;
                spaces++;
            }
            if (count > 0 && spaces == 0) break;
            if (tokenStart >= cmd.length()) break;
            int end = scanToken(cmd, tokenStart);
            if (end < 0) break;
            String token = cmd.subSequence(tokenStart, end).toString();
            if (token.isEmpty() || (token.charAt(0) != '~' && token.charAt(0) != '^')) break;
            applyOffset(token, count, axes, forward, left, up);
            lastEnd = end;
            pos = end;
            count++;
        }
        if (count == 0) {
            out[0] = null;
            return -1;
        }
        out[0] = format(new Vec3(axes[0], axes[1], axes[2]));
        return lastEnd;
    }

    private record Suffix(int end, String[] tokens) {}

    // ---- 坐标后缀 ----

    private static Suffix scanSuffix(CharSequence cmd, int from) {
        String[] tokens = new String[3];
        int pos = from;
        int lastEnd = from;
        int count = 0;
        while (count < 3) {
            int start = pos;
            int spaces = 0;
            while (start < cmd.length() && cmd.charAt(start) == ' ') {
                start++;
                spaces++;
            }

            if (count > 0 && spaces == 0) break;
            if (start >= cmd.length()) break;
            int end = scanToken(cmd, start);
            if (end < 0) break;
            tokens[count] = cmd.subSequence(start, end).toString();
            lastEnd = end;
            pos = end;
            count++;
        }

        return new Suffix(count == 0 ? from : lastEnd, tokens);
    }

    private static int consumeTrio(CharSequence cmd, int from, Vec3[] base,
                                   Vec3 forward, Vec3 left, Vec3 up) {
        Suffix suffix = scanSuffix(cmd, from);
        double[] axes = {base[0].x, base[0].y, base[0].z};
        for (int axis = 0; axis < suffix.tokens().length; axis++) {
            String token = suffix.tokens()[axis];
            if (token == null || token.isEmpty()) continue;
            applyOffset(token, axis, axes, forward, left, up);
        }
        base[0] = new Vec3(axes[0], axes[1], axes[2]);
        return suffix.end();
    }

    // ---- 相对坐标改写 ----

    private static void rewriteRelativeTo(StringBuilder cmd, Vec3 origin,
                                          Vec3 forward, Vec3 left, Vec3 up, boolean truncate) {
        int i = 0;
        char quote = 0;
        while (i < cmd.length()) {
            char c = cmd.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == quote) quote = 0;
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                i++;
                continue;
            }
            if ((c == '~' || c == '^') && atTokenStart(cmd, i)) {
                int next = rewriteRun(cmd, i, origin, forward, left, up, truncate);
                if (next > 0) {
                    i = next;
                    continue;
                }
            }
            i++;
        }
    }

    private static int rewriteRun(StringBuilder cmd, int start, Vec3 origin,
                                  Vec3 forward, Vec3 left, Vec3 up, boolean truncate) {
        String[] out = new String[1];
        int end = resolveRelativeRun(cmd, start, origin, forward, left, up, out);
        if (end < 0 || out[0] == null) return 0;
        String coords = truncate ? truncate(out[0]) : out[0];
        cmd.replace(start, end, coords);
        return start + coords.length();
    }

    private static boolean atTokenStart(CharSequence cmd, int i) {
        if (i == 0) return true;
        char prev = cmd.charAt(i - 1);
        return prev == ' ' || prev == '\t';
    }

    private static void applyOffset(String token, int axis, double[] axes,
                                    Vec3 forward, Vec3 left, Vec3 up) {
        char first = token.charAt(0);
        if (first == '~') {
            axes[axis] += token.length() > 1 ? parse(token.substring(1)) : 0.0;
        } else if (first == '^') {
            double value = token.length() > 1 ? parse(token.substring(1)) : 0.0;

            Vec3 axisVector = axis == 0 ? left : axis == 1 ? up : forward;
            axes[0] += axisVector.x * value;
            axes[1] += axisVector.y * value;
            axes[2] += axisVector.z * value;
    // ---- token 扫描 ----

        } else {
            axes[axis] = parse(token);
        }
    }

    private static int scanToken(CharSequence cmd, int from) {
        if (from >= cmd.length()) return -1;
        char c = cmd.charAt(from);
        int end;
        if (c == '~' || c == '^') {
            end = scanNumber(cmd, from + 1);
            if (end < cmd.length() && isNameChar(cmd.charAt(end))) return -1;
            return end;
        }
        end = scanNumber(cmd, from);
        if (end > from && (end >= cmd.length() || !isNameChar(cmd.charAt(end)))) return end;
        return -1;
    }

    private static int scanNumber(CharSequence cmd, int pos) {
        if (pos < cmd.length() && (cmd.charAt(pos) == '+' || cmd.charAt(pos) == '-')) pos++;
        while (pos < cmd.length()
                && ((cmd.charAt(pos) >= '0' && cmd.charAt(pos) <= '9') || cmd.charAt(pos) == '.')) {
            pos++;
        }
        return pos;
    }

    private static double parse(String value) {
        try {
    // ---- 数值格式化 ----

            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static Vec3 truncate(Vec3 v) {
        return new Vec3(trim(v.x), trim(v.y), trim(v.z));
    }

    private static double trim(double value) {
        if (!Double.isFinite(value) || Math.abs(value) < 1.0E-9) return 0.0;
        // 坐标要取「所在方块」的整数坐标，必须统一向负无穷取整。
        // 旧代码对负数用 ceil（向零取整），导致 -30.5 变成 -30，
        // 相对真正的方块 -31 正好 +1；-60.5 的 Y 也一样 +1。
        // 这正是之前需要用固定 -1 偏移去补的根因。
        return Math.floor(value);
    }

    private static String format(Vec3 v) {
        return formatCoordinate(v.x) + " " + formatCoordinate(v.y) + " " + formatCoordinate(v.z);
    }

    private static String truncate(String coords) {
        String[] parts = coords.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(' ');
            out.append(formatCoordinate(trim(parse(parts[i]))));
        }
    // ---- 字符判定 ----

        return out.toString();
    }

    private static String formatCoordinate(double value) {
        if (!Double.isFinite(value) || Math.abs(value) < 1.0E-9) return "0";
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 1.0E-9) return Long.toString((long) rounded);
        return String.format(Locale.ROOT, "%.6f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static boolean isNameChar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
    }
}
