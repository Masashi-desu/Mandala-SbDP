package io.github.mandala.sbdp.renderer;

public record RenderOptions(boolean allowCustomJavaScript, String title) {
    public RenderOptions {
        title = title == null || title.isBlank() ? "Mandala Documentation" : title.strip();
    }

    public static RenderOptions defaults() {
        return new RenderOptions(false, "Mandala Documentation");
    }
}
