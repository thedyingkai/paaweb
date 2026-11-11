package com.cwnupaa.backend.service.qqbot;

import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.service.FileStorageService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** PNG cards are rendered locally. No bot command can cause an arbitrary avatar/network fetch. */
@Service
public class BotCardRenderer {
    private static final int WIDTH = 860;
    private final FileSystemMapper fileMapper;
    private final FileStorageService storage;
    private final FileAccessPolicy filePolicy;
    private final Font baseFont;
    private final BufferedImage logo;

    public BotCardRenderer(FileSystemMapper fileMapper, FileStorageService storage, FileAccessPolicy filePolicy) {
        this.fileMapper = fileMapper;
        this.storage = storage;
        this.filePolicy = filePolicy;
        Font loaded;
        try (InputStream in = new ClassPathResource("fonts/NotoSansSC.ttf").getInputStream()) {
            loaded = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) { loaded = new Font(Font.SANS_SERIF, Font.PLAIN, 24); }
        baseFont = loaded;
        BufferedImage loadedLogo = null;
        try (InputStream in = new ClassPathResource("qqbot/logo.png").getInputStream()) {
            loadedLogo = ImageIO.read(in);
        } catch (IOException ignored) { }
        logo = loadedLogo;
    }

    public byte[] rating(Users user, HydroBotClient.HydroProfile profile) {
        Color background = color(profile.color());
        Color ink = luminance(background) > 0.46 ? new Color(24, 35, 58) : Color.WHITE;
        int rows = Math.max(1, profile.difficulty() == null ? 0 : profile.difficulty().size());
        int height = Math.max(1060, 635 + rows * 53 + 100);
        BufferedImage image = canvas(background, height);
        Graphics2D g = graphics(image);
        brand(g, ink);
        avatar(g, user, background, ink);
        g.setColor(ink);
        center(g, username(user), 335, 46, Font.BOLD);
        panel(g, 34, 375, 792, 103, ink);
        text(g, "MaxRating", 58, 412, 25, Font.BOLD, ink);
        text(g, String.valueOf(profile.maxRating()), 58, 456, 36, Font.BOLD, ink);
        text(g, "Rated Contest", 452, 412, 25, Font.BOLD, ink);
        text(g, String.valueOf(profile.ratedCount()), 452, 456, 36, Font.BOLD, ink);
        panel(g, 34, 505, 792, height - 575, ink);
        text(g, "Rating", 60, 554, 30, Font.BOLD, ink);
        text(g, String.valueOf(profile.rating()), 60, 649, 79, Font.BOLD, ink);
        text(g, "Level", 60, 714, 30, Font.BOLD, ink);
        fitText(g, profile.level(), 60, 780, 280, 46, ink);
        text(g, "Solved " + profile.solvedCount() + " problems", 388, 556, 27, Font.BOLD, ink);
        if (profile.difficulty() == null || profile.difficulty().isEmpty()) {
            text(g, "No solved problems", 388, 620, 25, Font.PLAIN, ink);
        } else {
            int y = 615;
            for (HydroBotClient.Difficulty d : profile.difficulty()) {
                text(g, clip(englishDifficulty(d.label()), 14), 388, y, 24, Font.BOLD, ink);
                text(g, String.format(java.util.Locale.ROOT, "%.1f%%", d.percent()), 697, y, 24, Font.BOLD, ink);
                g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 45));
                g.fillRoundRect(388, y + 10, 355, 8, 8, 8);
                g.setColor(ink);
                g.fillRoundRect(388, y + 10, (int) Math.round(355 * Math.max(0, Math.min(100, d.percent())) / 100), 8, 8, 8);
                y += 53;
            }
        }
        timestamp(g, height, ink);
        g.dispose();
        return png(image);
    }

    public byte[] tracker(Users user, Map<Long, Long> counts) {
        int height = 1080;
        BufferedImage image = canvas(Color.WHITE, height);
        Graphics2D g = graphics(image);
        Color ink = new Color(29, 41, 65);
        brand(g, ink);
        avatar(g, user, Color.WHITE, ink);
        center(g, username(user), 335, 46, Font.BOLD);
        g.setColor(ink);
        center(g, "CP Tracker", 393, 29, Font.BOLD);
        center(g, "Total Solved", 453, 27, Font.PLAIN);
        center(g, String.valueOf(counts.values().stream().mapToLong(Long::longValue).sum()), 537, 75, Font.BOLD);
        List<String> names = List.of("CodeForces", "AtCoder", "NowCoder", "Luogu", "学校 OJ");
        List<Color> colors = List.of(new Color(37, 99, 235), new Color(38, 49, 71), new Color(14, 146, 118),
                new Color(233, 128, 32), new Color(125, 69, 204));
        for (int i = 0; i < 5; i++) {
            int y = 582 + i * 81;
            g.setColor(colors.get(i));
            g.fillRoundRect(40, y, 780, 66, 20, 20);
            text(g, names.get(i), 64, y + 43, 30, Font.BOLD, Color.WHITE);
            String amount = String.valueOf(counts.getOrDefault((long) i + 1, 0L));
            g.setFont(baseFont.deriveFont(Font.BOLD, 35));
            g.setColor(Color.WHITE);
            g.drawString(amount, 784 - g.getFontMetrics().stringWidth(amount), y + 45);
        }
        timestamp(g, height, new Color(100, 116, 139));
        g.dispose();
        return png(image);
    }

    private BufferedImage canvas(Color background, int height) {
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(background); g.fillRect(0, 0, WIDTH, height); g.dispose();
        return image;
    }

    private Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    private void brand(Graphics2D g, Color ink) {
        if (logo != null) {
            double scale = Math.min(235.0 / logo.getWidth(), 65.0 / logo.getHeight());
            int h = (int) Math.round(logo.getHeight() * scale), w = (int) Math.round(logo.getWidth() * scale);
            g.drawImage(logo, WIDTH - w - 27, 21, w, h, null);
        } else text(g, "BESTI ACM", 627, 54, 27, Font.BOLD, ink);
    }

    private void avatar(Graphics2D g, Users user, Color background, Color ink) {
        BufferedImage avatar = loadAvatar(user);
        Shape oldClip = g.getClip();
        RoundRectangle2D shape = new RoundRectangle2D.Double(325, 86, 210, 210, 55, 55);
        g.setClip(shape);
        if (avatar != null) {
            int size = Math.min(avatar.getWidth(), avatar.getHeight());
            int x = (avatar.getWidth() - size) / 2, y = (avatar.getHeight() - size) / 2;
            g.drawImage(avatar, 325, 86, 535, 296, x, y, x + size, y + size, null);
        } else {
            g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 30)); g.fill(shape);
            g.setColor(ink); center(g, username(user).substring(0, 1).toUpperCase(java.util.Locale.ROOT), 224, 98, Font.BOLD);
        }
        g.setClip(oldClip);
    }

    private BufferedImage loadAvatar(Users user) {
        if (user.getAvatarFileId() == null) return null;
        try {
            FileSystem file = fileMapper.selectById(user.getAvatarFileId());
            if (file == null || !filePolicy.canAccess(file, null)) return null;
            Resource resource = storage.readResource(file);
            if (resource == null) return null;
            byte[] bytes;
            try (InputStream in = resource.getInputStream()) {
                bytes = in.readNBytes(5 * 1024 * 1024 + 1);
            }
            if (bytes.length > 5 * 1024 * 1024) return null;
            return decodeImage(bytes);
        } catch (Exception ignored) { return null; }
    }

    static BufferedImage decodeImage(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || (long) width * height > 12_000_000) return null;
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    private void panel(Graphics2D g, int x, int y, int w, int h, Color ink) {
        g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 20)); g.fillRoundRect(x, y, w, h, 26, 26);
    }
    private void text(Graphics2D g, String value, int x, int y, int size, int style, Color ink) {
        g.setFont(baseFont.deriveFont(style, size)); g.setColor(ink); g.drawString(value == null ? "—" : value, x, y);
    }
    private void fitText(Graphics2D g, String value, int x, int y, int maxWidth, int size, Color ink) {
        value = clip(value == null ? "Unrated" : value, 32);
        while (size > 18 && baseFont.deriveFont(Font.BOLD, size).getStringBounds(value, g.getFontRenderContext()).getWidth() > maxWidth) size--;
        text(g, value, x, y, size, Font.BOLD, ink);
    }
    private void center(Graphics2D g, String value, int y, int size, int style) {
        value = clip(value, 28);
        while (size > 18 && baseFont.deriveFont(style, size).getStringBounds(value, g.getFontRenderContext()).getWidth() > 770) size--;
        g.setFont(baseFont.deriveFont(style, size));
        g.drawString(value, (WIDTH - g.getFontMetrics().stringWidth(value)) / 2, y);
    }
    private void timestamp(Graphics2D g, int height, Color ink) {
        text(g, LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "  UTC+8",
                35, height - 24, 21, Font.PLAIN, ink);
    }
    private static String username(Users user) {
        return user.getUserAccount() == null || user.getUserAccount().isBlank() ? "BESTI 用户" : user.getUserAccount();
    }
    private static String englishDifficulty(String value) {
        if (value == null || value.isBlank() || value.equals("待评级") || value.equals("未分级") || value.equals("未评级")) return "Unrated";
        return value;
    }
    private static String clip(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }
    private static Color color(String hex) {
        try { return hex != null && hex.matches("#[0-9a-fA-F]{6}") ? Color.decode(hex) : new Color(100, 116, 139); }
        catch (Exception e) { return new Color(100, 116, 139); }
    }
    private static double luminance(Color color) { return (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255; }
    private byte[] png(BufferedImage image) {
        try { ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray(); }
        catch (IOException e) { throw new IllegalStateException("资料卡生成失败", e); }
    }
}
