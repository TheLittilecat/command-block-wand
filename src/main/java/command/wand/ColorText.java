package command.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class ColorText {
    public record Seg(String text, int color) {
    }

    private ColorText() {
    }

    private static Integer colorOf(ChatFormatting f) {
        return switch (f) {
            case BLACK -> 0x000000;
            case DARK_BLUE -> 0x0000AA;
            case DARK_GREEN -> 0x00AA00;
            case DARK_AQUA -> 0x00AAAA;
            case DARK_RED -> 0xAA0000;
            case DARK_PURPLE -> 0xAA00AA;
            case GOLD -> 0xFFAA00;
            case GRAY -> 0xAAAAAA;
            case DARK_GRAY -> 0x555555;
            case BLUE -> 0x5555FF;
            case GREEN -> 0x55FF55;
            case AQUA -> 0x55FFFF;
            case RED -> 0xFF5555;
            case LIGHT_PURPLE -> 0xFF55FF;
            case YELLOW -> 0xFFFF55;
            case WHITE -> 0xFFFFFF;
            default -> null;
        };
    }

    public static List<Seg> parse(String raw) {
        List<Seg> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        int color = 0xFFFFFFFF;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < raw.length()) {
                ChatFormatting f = ChatFormatting.getByCode(raw.charAt(i + 1));
                if (f != null) {
                    flush(out, cur, color);
                    if (f == ChatFormatting.RESET) {
                        color = 0xFFFFFFFF;
                    } else {
                        Integer rgb = colorOf(f);
                        if (rgb != null) {
                            color = 0xFF000000 | rgb;
                        }
                    }
                    i++;
                    continue;
                }
            }
            cur.append(c);
        }
        flush(out, cur, color);
        return out;
    }

    private static void flush(List<Seg> out, StringBuilder cur, int color) {
        if (!cur.isEmpty()) {
            out.add(new Seg(cur.toString(), color));
            cur.setLength(0);
        }
    }

    public static List<Seg> applyColorCodes(List<Seg> base) {
        boolean hasCode = false;
        for (Seg s : base) {
            if (s.text().indexOf('§') >= 0 || s.text().indexOf('&') >= 0) {
                hasCode = true;
                break;
            }
        }
        if (!hasCode) return base;

        List<Seg> out = new ArrayList<>();
        for (Seg seg : base) {
            int userColor = seg.color();
            StringBuilder pending = new StringBuilder();
            String text = seg.text();
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if ((c == '§' || c == '&') && i + 1 < text.length()) {
                    ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                    if (f != null) {

                        flush(out, pending, userColor);
                        if (f == ChatFormatting.RESET) {
                            userColor = seg.color();
                        } else {
                            Integer rgb = colorOf(f);
                            if (rgb != null) userColor = 0xFF000000 | rgb;
                        }
                        i += 2;
                        continue;
                    }
                }
                pending.append(c);
                i++;
            }
            flush(out, pending, userColor);
        }
        return out;
    }

    public static int width(Font font, List<Seg> segs) {
        int w = 0;
        for (Seg s : segs) {
            w += font.width(s.text());
        }
        return w;
    }

    public static String plain(String raw) {
        StringBuilder sb = new StringBuilder();
        for (Seg s : parse(raw)) {
            sb.append(s.text());
        }
        return sb.toString();
    }

    public static void draw(GuiGraphicsExtractor g, Font font, List<Seg> segs, int x, int y, boolean shadow) {
        int px = x;
        for (Seg s : segs) {
            g.text(font, s.text(), px, y, s.color(), shadow);
            px += font.width(s.text());
        }
    }
}
