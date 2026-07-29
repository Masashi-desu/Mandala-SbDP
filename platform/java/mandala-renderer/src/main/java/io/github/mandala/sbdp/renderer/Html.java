package io.github.mandala.sbdp.renderer;

final class Html {
    private Html() {}

    static String escape(Object value) {
        if (value == null) return "";
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String attribute(Object value) {
        return escape(value).replace("`", "&#96;");
    }
}
