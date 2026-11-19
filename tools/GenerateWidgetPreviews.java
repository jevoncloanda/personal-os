import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

// Run from the repository root with JDK 17+: java tools/GenerateWidgetPreviews.java
// Android's two picker attributes use the same artwork. The samples here are never app data.
final class GenerateWidgetPreviews {
    private static final Path SOURCES = Path.of("app", "src");
    private static final int WIDTH = 430;
    private static final int HEIGHT = 234;
    private static final int SCALE = 2;

    private final Graphics2D graphics;
    private final Map<String, String> palette;

    private GenerateWidgetPreviews(Graphics2D graphics, Map<String, String> palette) {
        this.graphics = graphics;
        this.palette = palette;
    }

    public static void main(String[] args) throws Exception {
        for (boolean debug : new boolean[] {false, true}) {
            for (boolean dark : new boolean[] {false, true}) {
                Map<String, String> palette = new HashMap<>();
                loadColors(palette, "main/res/values/widget.xml");
                if (dark) loadColors(palette, "main/res/values-night/widget.xml");
                if (debug) loadColors(palette, "debug/res/values/branding.xml");
                if (debug && dark) loadColors(palette, "debug/res/values-night/branding.xml");

                Path resources = SOURCES.resolve(debug ? "debug" : "main").resolve("res");
                Path images = resources.resolve(dark ? "drawable-night-nodpi" : "drawable-nodpi");
                Files.createDirectories(images);
                render(images, "quick_expense", palette, GenerateWidgetPreviews::quickExpense);
                render(images, "budget_tracker", palette, GenerateWidgetPreviews::budget);
                render(images, "today_overview", palette, GenerateWidgetPreviews::today);
                render(images, "upcoming_schedule", palette, GenerateWidgetPreviews::schedule);
            }
        }
    }

    private static void loadColors(Map<String, String> palette, String name) throws Exception {
        var parser = DocumentBuilderFactory.newInstance();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var colors = parser.newDocumentBuilder()
            .parse(SOURCES.resolve(name).toFile()).getElementsByTagName("color");
        for (int i = 0; i < colors.getLength(); i++) {
            var color = (Element) colors.item(i);
            palette.put(color.getAttribute("name"), color.getTextContent().trim());
        }
    }

    private static void render(
        Path directory,
        String name,
        Map<String, String> palette,
        java.util.function.Consumer<GenerateWidgetPreviews> content
    ) throws Exception {
        var image = new BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.scale(SCALE, SCALE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            var preview = new GenerateWidgetPreviews(graphics, palette);
            preview.fill("widget_surface", 0, 0, WIDTH, HEIGHT, 24);
            content.accept(preview);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", directory.resolve(name + "_preview_image.png").toFile());
    }

    private void quickExpense() {
        text("Quick Expense", 16, 38, 14, true, "widget_accent");
        text("Edit", 386, 38, 12, true, "widget_accent");
        String[] names = {"Transit", "Lunch", "Coffee"};
        String[] amounts = {"Rp25.000", "Rp48.000", "Rp18.000"};
        for (int i = 0; i < names.length; i++) {
            int x = 12 + (i % 2) * 207;
            int y = 60 + (i / 2) * 85;
            fill("widget_panel", x, y, 199, 77, 12);
            text(names[i], x + 10, y + 31, 12, false, "widget_muted");
            text(amounts[i], x + 10, y + 56, 20, true, "widget_text");
        }
        fill("widget_action_surface", 219, 145, 199, 77, 12);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                fill("widget_on_action", 232 + column * 4, 179 + row * 4, 2, 2, 0);
            }
        }
        text("Custom Amount", 251, 189, 15, true, "widget_on_action");
    }

    private void budget() {
        text("Monthly budget", 16, 33, 14, true, "widget_accent");
        text("1 – 30 Nov", 16, 52, 12, false, "widget_muted");
        text("Rp1.250.000", 16, 119, 32, true, "widget_text");
        text("Remaining", 16, 141, 12, false, "dashboard_positive");
        fill("widget_progress_track", 16, 162, 398, 12, 6);
        fill("widget_accent", 16, 162, 232, 12, 6);
        text("Spent", 16, 198, 12, false, "widget_muted");
        text("Rp1.750.000", 16, 218, 16, true, "widget_text");
        text("Limit", 221, 198, 12, false, "widget_muted");
        text("Rp3.000.000", 221, 218, 16, true, "widget_text");
    }

    private void today() {
        text("19", 16, 62, 44, true, "widget_text");
        text("Thursday", 86, 41, 14, true, "widget_accent");
        text("Nov 2026", 86, 59, 12, false, "widget_muted");
        text("Event · Today · 14:00", 16, 113, 12, false, "widget_accent");
        text("Design review", 16, 143, 22, true, "widget_text");
        text("Monthly budget", 16, 192, 12, false, "widget_muted");
        text("Rp1.250.000 left", 16, 212, 14, true, "dashboard_positive");
    }

    private void schedule() {
        text("Upcoming", 16, 33, 14, true, "widget_accent");
        text("Today", 16, 78, 16, true, "widget_text");
        text("14:00", 16, 96, 12, true, "widget_accent");
        fill("widget_accent", 116, 56, 2, 48, 0);
        text("Design review", 128, 77, 18, true, "widget_text");
        text("Event", 128, 97, 12, false, "widget_accent");
        fill("widget_progress_track", 16, 128, 398, 1, 0);
        text("Tomorrow", 16, 170, 16, true, "widget_text");
        fill("dashboard_positive", 116, 151, 2, 48, 0);
        text("Music subscription", 128, 168, 18, true, "widget_text");
        text("Renewal · Forecast", 128, 187, 12, false, "dashboard_positive");
        text("IDR · Rp59.000", 128, 205, 12, true, "dashboard_positive");
    }

    private Color color(String name) {
        String value = palette.get(name);
        while (value.startsWith("@color/")) value = palette.get(value.substring(7));
        return Color.decode(value);
    }

    private void text(String value, int x, int baseline, int size, boolean bold, String color) {
        graphics.setColor(color(color));
        graphics.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        graphics.drawString(value, x, baseline);
    }

    private void fill(String color, int x, int y, int width, int height, int radius) {
        graphics.setColor(color(color));
        graphics.fillRoundRect(x, y, width, height, radius * 2, radius * 2);
    }
}
