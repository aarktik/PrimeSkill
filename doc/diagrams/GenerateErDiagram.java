import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Regenerates er-diagram.png without an external diagram tool. */
public final class GenerateErDiagram {
    private static final Color NAVY = new Color(31, 56, 100);
    private static final Color BLUE = new Color(226, 239, 255);
    private static final Color BORDER = new Color(108, 135, 173);

    private GenerateErDiagram() { }

    public static void main(String[] args) throws Exception {
        String output = args.length == 0 ? "er-diagram.png" : args[0];
        BufferedImage image = new BufferedImage(2400, 1400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(NAVY);
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        g.drawString("ToolHub / PrimeSkill — Sprint 0 ER Diagram", 80, 70);

        table(g, 80, 150, "users", "PK id", "email (unique)", "password_hash", "role, enabled", "created_at, updated_at");
        table(g, 80, 560, "user_profiles", "PK id", "FK user_id (unique)", "display_name", "bio, avatar_url", "created_at, updated_at");
        table(g, 780, 110, "categories", "PK id", "name, slug (unique)", "description", "created_at, updated_at");
        table(g, 780, 390, "tools", "PK id", "FK category_id", "FK owner_id", "name, slug (unique)", "description, website_url", "status, created_at, updated_at");
        table(g, 1530, 110, "tags", "PK id", "name, slug (unique)", "created_at, updated_at");
        table(g, 1530, 430, "tool_tags", "PK/FK tool_id", "PK/FK tag_id");
        table(g, 780, 940, "reviews", "PK id", "FK user_id", "FK tool_id", "rating (1–5), comment", "created_at, updated_at", "UNIQUE (user_id, tool_id)");
        table(g, 1530, 920, "tool_versions", "PK id", "FK tool_id", "version", "release_notes, released_at", "UNIQUE (tool_id, version)");

        relation(g, 380, 270, 780, 500, "1", "N");       // user owns tools
        relation(g, 380, 340, 780, 1040, "1", "N");      // user writes reviews
        relation(g, 230, 440, 230, 560, "1", "1");       // profile
        relation(g, 980, 330, 980, 390, "1", "N");       // category tools
        relation(g, 1280, 560, 1530, 530, "1", "N");     // tools tags
        relation(g, 1530, 280, 1680, 430, "1", "N");     // tags join
        relation(g, 1100, 730, 1100, 940, "1", "N");     // tools reviews
        relation(g, 1280, 660, 1530, 1040, "1", "N");    // tools versions

        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.drawString("Indexes: tools.owner_id · reviews.tool_id · tool_tags.tool_id", 80, 1340);
        g.dispose();
        ImageIO.write(image, "png", new File(output));
    }

    private static void table(Graphics2D g, int x, int y, String title, String... fields) {
        int width = 500;
        int rowHeight = 36;
        int height = 52 + fields.length * rowHeight;
        g.setColor(BLUE);
        g.fillRoundRect(x, y, width, height, 12, 12);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, width, height, 12, 12);
        g.setColor(NAVY);
        g.fillRoundRect(x, y, width, 52, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        g.drawString(title, x + 18, y + 34);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        for (int i = 0; i < fields.length; i++) {
            g.drawString(fields[i], x + 18, y + 80 + i * rowHeight);
        }
    }

    private static void relation(Graphics2D g, int x1, int y1, int x2, int y2, String left, String right) {
        g.setColor(new Color(80, 80, 80));
        g.setStroke(new BasicStroke(2));
        g.drawLine(x1, y1, x2, y2);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString(left, x1 + 8, y1 - 8);
        g.drawString(right, x2 + 8, y2 - 8);
    }
}
