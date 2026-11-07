package command.wand;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class ProbeEditBox extends EditBox {

    private static final String PROBE_ORIGIN = "0 0 0";

    private record Segment(int probeStart, int probeEnd, int realStart, int realEnd, boolean synthetic) {}

    private record Probe(String text, List<Segment> segments) {}

    ProbeEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    /** 真实文本（不含 @t/@f 的探针替换）。 */
    String realValue() {
        return super.getValue();
    }

    @Override
    public String getValue() {
        return buildProbe(super.getValue()).text();
    }

    @Override
    public int getCursorPosition() {
        String real = super.getValue();
        int realCursor = Math.min(super.getCursorPosition(), real.length());
        return probeIndexOf(buildProbe(real), realCursor);
    }

    @Override
    public void setValue(String value) {
        String real = super.getValue();
        if (value != null && !real.isBlank() && !value.equals(real)) {
            super.setValue(restore(buildProbe(real), real, value));
            return;
        }
        super.setValue(value == null ? "" : value);
    }

    private static Probe buildProbe(String real) {
        StringBuilder out = new StringBuilder(real.length() + 4);
        List<Segment> segments = new ArrayList<>();
        int i = 0;
        while (i < real.length()) {
            int at = real.indexOf('@', i);
            if (at < 0) {
                appendMapped(out, segments, real, i, real.length(), false);
                i = real.length();
                break;
            }
            appendMapped(out, segments, real, i, at, false);
            char kind = at + 1 < real.length() ? real.charAt(at + 1) : 0;
            if (kind == 't' && isSelectorEnd(real, at + 2)) {
                // @t 注册为目标选择器
                appendLiteral(out, segments, "@s", at, at + 2, false);
                i = at + 2;
            } else if (kind == 'f' && isSelectorEnd(real, at + 2)) {
                // @f 以及 @f~ ~ ~ 统一按三个浮点坐标解析
                int next = CommandSubstituter.forwardSuffixEnd(real, at + 2);
                appendLiteral(out, segments, PROBE_ORIGIN, at, next, true);
                i = next;
            } else {
                appendLiteral(out, segments, real.substring(at, at + 1), at, at + 1, false);
                i = at + 1;
            }
        }
        if (i < real.length()) appendMapped(out, segments, real, i, real.length(), false);
        return new Probe(out.toString(), segments);
    }

    private static int probeIndexOf(Probe probe, int realIndex) {
        int last = probe.segments().isEmpty() ? 0 : probe.text().length();
        if (probe.segments().isEmpty()) return Math.min(Math.max(realIndex, 0), last);
        for (Segment s : probe.segments()) {
            if (realIndex >= s.realStart() && realIndex <= s.realEnd()) {
                int offset = Math.min(realIndex - s.realStart(), s.probeEnd() - s.probeStart());
                return s.probeStart() + Math.max(offset, 0);
            }
        }
        return last;
    }

    private static void appendMapped(StringBuilder out, List<Segment> segments, String real,
                                     int from, int to, boolean synthetic) {
        if (to <= from) return;
        appendLiteral(out, segments, real.substring(from, to), from, to, synthetic);
    }

    private static void appendLiteral(StringBuilder out, List<Segment> segments, String literal,
                                      int realStart, int realEnd, boolean synthetic) {
        int probeStart = out.length();
        out.append(literal);
        segments.add(new Segment(probeStart, out.length(), realStart, realEnd, synthetic));
    }

    private static int mappedReal(Probe probe, int probeIndex) {
        for (Segment s : probe.segments()) {
            if (probeIndex >= s.probeStart() && probeIndex <= s.probeEnd()) {
                if (s.synthetic()) {
                    if (probeIndex == s.probeStart()) return s.realStart();
                    if (probeIndex == s.probeEnd()) return s.realEnd();
                    return -1;
                }
                return s.realStart() + (probeIndex - s.probeStart());
            }
        }
        return -1;
    }

    private static String restore(Probe probe, String real, String accepted) {
        String old = probe.text();
        int common = 0;
        int maxCommon = Math.min(old.length(), accepted.length());
        while (common < maxCommon && old.charAt(common) == accepted.charAt(common)) common++;
        int oldTail = old.length();
        int newTail = accepted.length();
        while (oldTail > common && newTail > common && old.charAt(oldTail - 1) == accepted.charAt(newTail - 1)) {
            oldTail--;
            newTail--;
        }
        int realStart = mappedReal(probe, common);
        int realEnd = mappedReal(probe, oldTail);
        if (realStart < 0 || realEnd < realStart) return accepted;
        return real.substring(0, realStart) + accepted.substring(common, newTail) + real.substring(realEnd);
    }

    private static boolean isSelectorEnd(String value, int next) {
        return next >= value.length() || !isNameChar(value.charAt(next));
    }

    private static boolean isNameChar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
    }
}
