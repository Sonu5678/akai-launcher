package dev.mantel.tv;

/**
 * Per-app identity for the shelf: one hue, one scene motif, one original glyph. Apps that are not
 * in the pack get a generated hue and a monogram instead (the fallback ladder in the design).
 */
final class Brand {
    private Brand() {}

    static final int NONE = -1;
    static final int NETFLIX = 0, HOTSTAR = 1, BROWSE = 2, YOUTUBE = 3, JIOTV = 4, SPOTIFY = 5, KARTOONS = 6;

    static final int[] HUE = {0xFFE23B5A, 0xFF7C7CFF, 0xFF38D0EA, 0xFFFF7A33, 0xFFF25CC0, 0xFF2EE59D, 0xFFFFD43B};
    static final int[] BG = {0xFF12040A, 0xFF070817, 0xFF041218, 0xFF140805, 0xFF150613, 0xFF03120C, 0xFF171204};
    static final int[] G1 = {0xFF5A0F26, 0xFF23237A, 0xFF0A4F61, 0xFF6B2A0A, 0xFF6A1A56, 0xFF0A5A3A, 0xFF6E5A0A};
    static final int[] G2 = {0xFF2A0714, 0xFF14143F, 0xFF062B35, 0xFF3A1305, 0xFF380B2E, 0xFF06301F, 0xFF3A2F05};
    /** Dark end of the icon gradient. */
    static final int[] DEEP = {0xFF4A0B1F, 0xFF17175C, 0xFF073B49, 0xFF5C1F06, 0xFF531445, 0xFF073D28, 0xFF6B4E00};

    static final String[] KIND = {"Streaming", "Streaming", "Browser", "Video", "Live TV", "Music", "Kids"};
    static final String[] LINE = {"Cinema nights", "Sport and shows", "The open web", "Anything, anytime",
            "Channels live", "Sound for the room", "Saturday, every day"};

    /** Which pack entry an app belongs to, or NONE. */
    static int of(AppEntry e) {
        if (e == null) return NONE;
        String s = (e.norm == null ? "" : e.norm) + " " + (e.pkg == null ? "" : e.pkg.toLowerCase());
        if (s.contains("netflix")) return NETFLIX;
        if (s.contains("hotstar")) return HOTSTAR;
        if (s.contains("browsehere")) return BROWSE;
        if (s.contains("youtube") && !s.contains("music")) return YOUTUBE;
        if (s.contains("jiotv")) return JIOTV;
        if (s.contains("spotify")) return SPOTIFY;
        if (s.contains("kartoon")) return KARTOONS;
        return NONE;
    }

    /** A stable hue for apps outside the pack, from the package name. */
    static int hueFor(AppEntry e) {
        int b = of(e);
        if (b >= 0) return HUE[b];
        int h = e == null || e.pkg == null ? 0 : e.pkg.hashCode();
        float[] hsv = {Math.abs(h % 360), 0.62f, 0.95f};
        return android.graphics.Color.HSVToColor(hsv);
    }

    /** Darken a colour (0..1, 1 = unchanged). */
    static int shade(int color, float k) {
        int r = Math.round(((color >> 16) & 0xFF) * k);
        int g = Math.round(((color >> 8) & 0xFF) * k);
        int b = Math.round((color & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static String kind(AppEntry e) {
        int b = of(e);
        return b >= 0 ? KIND[b] : "App";
    }

    static String line(AppEntry e) {
        int b = of(e);
        return b >= 0 ? LINE[b] : "";
    }
}
