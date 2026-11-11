package com.cwnupaa.backend.service;

import com.cwnupaa.backend.dto.CertificateTemplateSchemaDTO;
import com.cwnupaa.backend.dto.CertificateTextBlockDTO;
import com.cwnupaa.backend.dto.CertificateQrCodeBlockDTO;
import com.cwnupaa.backend.entity.CertificateIssue;
import com.cwnupaa.backend.entity.CertificateIssueMember;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class CertificateTemplateRenderService {
    private static final DateTimeFormatter CHINESE_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final int MAX_BLOCKS = 30;
    private static final float CERTIFICATE_DPI = 300;
    private static final float CERTIFICATE_JPEG_QUALITY = 0.92f;
    private static final double MAX_RASTER_PIXELS = 12_000_000;
    private static final double MAX_RASTER_DIMENSION = 8192;
    private static final String VERIFY_BASE_URL = com.cwnupaa.backend.utils.DeploymentUrls.site() + "/certificate/verify";
    private final CertificatePdfCache pdfCache = new CertificatePdfCache();

    @Autowired
    private FileSystemMapper fileMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FileStorageService fileStorageService;

    @Value("${file.upload.path:/tmp/uploads/}")
    private String uploadPath;

    public String normalizeAndValidateSchema(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) throw new IllegalArgumentException("请至少添加一个文字占位符");
        try {
            CertificateTemplateSchemaDTO schema = objectMapper.readValue(schemaJson, CertificateTemplateSchemaDTO.class);
            if ((schema.getBlocks() == null || schema.getBlocks().isEmpty()) && !isQrEnabled(schema.getQrCode())) {
                throw new IllegalArgumentException("请至少添加一个文字占位符");
            }
            if (schema.getBlocks() == null) schema.setBlocks(new ArrayList<>());
            if (schema.getBlocks().size() > MAX_BLOCKS) throw new IllegalArgumentException("文字块不能超过 " + MAX_BLOCKS + " 个");
            for (int i = 0; i < schema.getBlocks().size(); i++) normalizeBlock(schema.getBlocks().get(i), i);
            normalizeQrCode(schema.getQrCode());
            schema.setVersion(2);
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("模板占位符配置格式错误", e);
        }
    }

    public byte[] renderBackground(Long fileId) throws Exception {
        File file = resolvePdfFile(fileId);
        try (PDDocument document = PDDocument.load(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ensureSinglePage(document);
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 120, ImageType.RGB);
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    public byte[] renderPreview(Long fileId, String schemaJson, boolean team, LocalDate issueDate) throws Exception {
        String normalizedSchema = normalizeAndValidateSchema(schemaJson);
        CertificateIssue sample = new CertificateIssue();
        sample.setCertificateNo("BESTI-2026-0001");
        sample.setAwardName("一等奖");
        sample.setTeamName(team ? "星辰算法队" : null);
        sample.setCertificateKind(team ? 1 : 0);
        sample.setIssueDate(issueDate == null ? LocalDate.now() : issueDate);
        List<CertificateIssueMember> members = new ArrayList<>();
        int count = team ? 4 : 1;
        for (int i = 0; i < count; i++) {
            CertificateIssueMember member = new CertificateIssueMember();
            member.setMemberName(List.of("张三", "李四", "王五", "赵六").get(i));
            member.setStudentId("2026000" + (i + 1));
            member.setSortOrder(i);
            members.add(member);
        }
        return render(resolvePdfFile(fileId), normalizedSchema, sample, members);
    }

    public byte[] renderIssuedCertificate(File templateFile, String schemaJson, CertificateIssue issue,
                                          List<CertificateIssueMember> members) throws Exception {
        return render(templateFile, normalizeAndValidateSchema(schemaJson), issue, members);
    }

    public File resolvePdfFile(Long fileId) {
        if (fileId == null) throw new IllegalArgumentException("PDF 模板文件不能为空");
        FileSystem fileInfo = fileMapper.selectById(fileId);
        if (fileInfo == null || !StringUtils.hasText(fileInfo.getName()) || !fileInfo.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("模板文件不存在或不是 PDF");
        }
        File file;
        try {
            if (fileStorageService != null) {
                file = fileStorageService.materialize(fileInfo);
            } else {
                String cleanUrl = fileInfo.getUrl();
                if (cleanUrl.startsWith("/api/uploads/")) cleanUrl = cleanUrl.substring("/api/uploads/".length());
                else if (cleanUrl.startsWith("/uploads/")) cleanUrl = cleanUrl.substring("/uploads/".length());
                String base = uploadPath.endsWith(File.separator) || uploadPath.endsWith("/") ? uploadPath : uploadPath + File.separator;
                file = new File(base, cleanUrl);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("PDF 模板文件读取失败", e);
        }
        if (!file.exists() || !file.isFile()) throw new IllegalArgumentException("PDF 模板物理文件不存在");
        return file;
    }

    public void validateSinglePagePdf(Long fileId) {
        File file = resolvePdfFile(fileId);
        try (PDDocument document = PDDocument.load(file)) {
            ensureSinglePage(document);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new IllegalArgumentException("无法读取 PDF 模板文件：" + e.getMessage(), e);
        }
    }

    private byte[] render(File templateFile, String schemaJson, CertificateIssue issue,
                          List<CertificateIssueMember> members) throws Exception {
        CertificateTemplateSchemaDTO schema = objectMapper.readValue(schemaJson, CertificateTemplateSchemaDTO.class);
        List<String> contents = schema.getBlocks().stream()
                .map(block -> replacePlaceholders(block.getContent(), issue, members)).toList();
        String certificateNo = isQrEnabled(schema.getQrCode()) ? issue.getCertificateNo() : null;
        String key = CertificatePdfCache.key(templateFile, schemaJson,
                objectMapper.writeValueAsString(contents), certificateNo);
        return pdfCache.get(key, () -> renderUncached(templateFile, schema, contents, certificateNo));
    }

    private byte[] renderUncached(File templateFile, CertificateTemplateSchemaDTO schema,
                                  List<String> contents, String certificateNo) throws Exception {
        try (PDDocument document = PDDocument.load(templateFile)) {
            ensureSinglePage(document);
            if (document.getNumberOfPages() == 0) throw new IllegalArgumentException("PDF 模板没有页面");
            PDPage page = document.getPage(0);
            PDRectangle box = page.getCropBox();
            Map<String, LoadedFont> fonts = new HashMap<>();
            try {
                for (int index = 0; index < schema.getBlocks().size(); index++) {
                    CertificateTextBlockDTO block = schema.getBlocks().get(index);
                    LoadedFont loaded = fonts.computeIfAbsent(normalizeFont(block.getFontFamily()), family -> loadFont(document, family));
                    drawBlock(document, page, box, loaded.font(), block, contents.get(index));
                }
                if (isQrEnabled(schema.getQrCode())) {
                    drawQrCode(document, page, box, schema.getQrCode(), certificateNo);
                }
                removeFormFields(document);
                return renderImageOnlyPdf(document);
            } finally {
                for (LoadedFont font : fonts.values()) font.close();
            }
        }
    }

    /** Fresh pages retain only the rendered pixels, never the source text, forms or attachments. */
    public byte[] renderImageOnlyPdf(PDDocument source) throws Exception {
        try (PDDocument prepared = prepareForRasterization(source);
             PDDocument result = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            result.setVersion(1.7f);
            PDFRenderer renderer = new PDFRenderer(prepared);
            renderer.setSubsamplingAllowed(true);
            for (int index = 0; index < prepared.getNumberOfPages(); index++) {
                PDPage original = prepared.getPage(index);
                PDRectangle box = original.getCropBox();
                float unit = original.getUserUnit();
                if (!Float.isFinite(box.getWidth()) || !Float.isFinite(box.getHeight())
                        || box.getWidth() <= 0 || box.getHeight() <= 0 || !Float.isFinite(unit) || unit <= 0) {
                    throw new IllegalArgumentException("证书 PDF 页面尺寸无效");
                }
                double scale = CERTIFICATE_DPI / 72d * unit;
                double widthPixels = box.getWidth() * scale;
                double heightPixels = box.getHeight() * scale;
                double limit = Math.min(1d, Math.sqrt(MAX_RASTER_PIXELS / (widthPixels * heightPixels)));
                limit = Math.min(limit, MAX_RASTER_DIMENSION / Math.max(widthPixels, heightPixels));
                BufferedImage image = renderer.renderImage(index, (float) (scale * limit), ImageType.RGB);
                try {
                    int rotation = (original.getRotation() % 360 + 360) % 360;
                    boolean sideways = rotation == 90 || rotation == 270;
                    PDRectangle size = new PDRectangle(sideways ? box.getHeight() : box.getWidth(),
                            sideways ? box.getWidth() : box.getHeight());
                    PDPage page = new PDPage(size);
                    page.setUserUnit(unit);
                    result.addPage(page);
                    PDImageXObject bitmap = hasContinuousTone(image)
                            ? JPEGFactory.createFromImage(result, image, CERTIFICATE_JPEG_QUALITY, (int) CERTIFICATE_DPI)
                            : LosslessFactory.createFromImage(result, image);
                    try (PDPageContentStream stream = new PDPageContentStream(result, page)) {
                        stream.drawImage(bitmap, 0, 0, size.getWidth(), size.getHeight());
                    }
                } finally {
                    image.flush();
                }
            }
            AccessPermission permissions = new AccessPermission();
            permissions.setCanPrint(true);
            permissions.setCanPrintDegraded(true);
            permissions.setCanModify(false);
            permissions.setCanExtractContent(false);
            permissions.setCanExtractForAccessibility(false);
            permissions.setCanModifyAnnotations(false);
            permissions.setCanFillInForm(false);
            permissions.setCanAssembleDocument(false);
            // An empty user password permits normal viewing/printing; compliant editors
            // require the separate random owner password for modification or extraction.
            StandardProtectionPolicy protection = new StandardProtectionPolicy(UUID.randomUUID().toString(), "", permissions);
            protection.setEncryptionKeyLength(128);
            protection.setPreferAES(true);
            result.protect(protection);
            result.save(output);
            return output.toByteArray();
        }
    }

    private boolean hasContinuousTone(BufferedImage image) {
        // Sampling avoids the costly PNG predictor/compression pass on photographic
        // backgrounds. Flat artwork and text keep their exact pixels losslessly.
        var colors = new HashSet<Integer>();
        int stepX = Math.max(1, image.getWidth() / 128);
        int stepY = Math.max(1, image.getHeight() / 128);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                colors.add(image.getRGB(x, y));
                if (colors.size() > 512) return true;
            }
        }
        return false;
    }

    private PDDocument prepareForRasterization(PDDocument source) throws IOException {
        // Saving completes subset font embedding and the CID-to-glyph maps. Rendering
        // an unfinished in-memory font would substitute or lose Chinese characters.
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            source.save(bytes);
            return PDDocument.load(bytes.toByteArray());
        }
    }

    private void ensureSinglePage(PDDocument document) {
        if (document.getNumberOfPages() != 1) {
            throw new IllegalArgumentException("证书模板 PDF 必须且只能包含 1 页");
        }
    }

    /** 签发成品不得保留模板中的任何可交互 PDF 表单域。 */
    private void removeFormFields(PDDocument document) throws Exception {
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        if (acroForm == null) return;
        for (PDPage page : document.getPages()) {
            page.getAnnotations().removeIf(annotation -> annotation instanceof PDAnnotationWidget);
        }
        document.getDocumentCatalog().setAcroForm(null);
    }

    private void drawBlock(PDDocument document, PDPage page, PDRectangle box, PDType0Font font,
                           CertificateTextBlockDTO block, String renderedText) throws Exception {
        String safeText = sanitize(font, renderedText);
        String[] lines = safeText.split("\\R", -1);
        float maxWidth = (float) (box.getWidth() * block.getWidth() / 100d);
        float fontSize = block.getFontSize().floatValue();
        float minSize = block.getMinFontSize().floatValue();
        if (Boolean.TRUE.equals(block.getAutoFit())) {
            for (String line : lines) {
                float widthAtSize = textWidth(font, line, fontSize);
                if (widthAtSize > maxWidth && widthAtSize > 0) {
                    fontSize = Math.max(minSize, fontSize * maxWidth / widthAtSize);
                }
            }
        }

        float baseX = box.getLowerLeftX() + (float) (box.getWidth() * block.getX() / 100d);
        float firstBaseline = box.getLowerLeftY() + box.getHeight()
                - (float) (box.getHeight() * block.getY() / 100d) - fontSize;
        Color color = parseColor(block.getColor());
        try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream.setNonStrokingColor(color);
            stream.setStrokingColor(color);
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                String line = lines[lineIndex];
                float lineWidth = textWidth(font, line, fontSize);
                float x = switch (block.getAlignment().toUpperCase(Locale.ROOT)) {
                    case "RIGHT" -> baseX + maxWidth - lineWidth;
                    case "CENTER" -> baseX + (maxWidth - lineWidth) / 2f;
                    default -> baseX;
                };
                float y = firstBaseline - lineIndex * fontSize * 1.35f;
                // These Chinese fonts have no bundled bold face; stroke the same glyphs
                // so all supported families retain their shape before rasterization.
                stream.setLineWidth(Math.max(0.15f, fontSize / 40f));
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.setRenderingMode(Boolean.TRUE.equals(block.getBold()) ? RenderingMode.FILL_STROKE : RenderingMode.FILL);
                stream.newLineAtOffset(x, y);
                stream.showText(line);
                stream.endText();
                if (Boolean.TRUE.equals(block.getUnderline()) && lineWidth > 0) {
                    float underlineY = y - fontSize * 0.15f;
                    stream.setLineWidth(Math.max(0.4f, fontSize / 20f));
                    stream.moveTo(x, underlineY);
                    stream.lineTo(x + lineWidth, underlineY);
                    stream.stroke();
                }
            }
        }
    }

    private String replacePlaceholders(String content, CertificateIssue issue, List<CertificateIssueMember> members) {
        List<CertificateIssueMember> ordered = members == null ? List.of() : members.stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder() == null ? 99 : a.getSortOrder(), b.getSortOrder() == null ? 99 : b.getSortOrder()))
                .collect(Collectors.toList());
        String names = ordered.stream().map(CertificateIssueMember::getMemberName).collect(Collectors.joining("、"));
        String ids = ordered.stream().map(CertificateIssueMember::getStudentId).collect(Collectors.joining("、"));
        String schools = ordered.stream().map(CertificateIssueMember::getSchoolName).filter(StringUtils::hasText).collect(Collectors.joining("、"));
        String firstName = ordered.isEmpty() ? "" : ordered.get(0).getMemberName();
        String firstId = ordered.isEmpty() ? "" : ordered.get(0).getStudentId();
        String firstSchool = ordered.isEmpty() ? "" : (ordered.get(0).getSchoolName() == null ? "" : ordered.get(0).getSchoolName());
        String result = content == null ? "" : content;
        result = replace(result, issue.getTeamName(), "<teamname>", "<队伍名称>");
        result = replace(result, names, "<members>", "<获奖人姓名>");
        result = replace(result, ids, "<studentids>", "<获奖人学号>");
        result = replace(result, schools, "<schools>", "<获奖人学校>");
        result = replace(result, firstName, "<name>", "<姓名>");
        result = replace(result, firstId, "<studentid>", "<学号>");
        result = replace(result, firstSchool, "<school>", "<学校>");
        result = replace(result, issue.getAwardName(), "<award>", "<奖项>");
        result = replace(result, issue.getCertificateNo(), "<number>", "<编号>");
        result = replace(result, issue.getIssueDate() == null ? "" : CHINESE_DATE.format(issue.getIssueDate()), "<date>", "<落款日期>");
        for (int i = 1; i <= 10; i++) {
            String memberName = ordered.size() >= i ? ordered.get(i - 1).getMemberName() : "";
            String studentId = ordered.size() >= i ? ordered.get(i - 1).getStudentId() : "";
            String memberSchool = ordered.size() >= i && ordered.get(i - 1).getSchoolName() != null ? ordered.get(i - 1).getSchoolName() : "";
            result = replace(result, memberName, "<member" + i + "name>", "<队员" + i + "姓名>");
            result = replace(result, studentId, "<member" + i + "id>", "<队员" + i + "学号>");
            result = replace(result, memberSchool, "<member" + i + "school>", "<队员" + i + "学校>");
        }
        return result;
    }

    private String replace(String source, String value, String... placeholders) {
        String result = source;
        String safeValue = value == null ? "" : value;
        for (String placeholder : placeholders) result = result.replace(placeholder, safeValue);
        return result;
    }

    private void normalizeBlock(CertificateTextBlockDTO block, int index) {
        if (block == null) throw new IllegalArgumentException("第 " + (index + 1) + " 个文字块为空");
        if (!StringUtils.hasText(block.getId())) block.setId(UUID.randomUUID().toString());
        if (!StringUtils.hasText(block.getContent())) throw new IllegalArgumentException("第 " + (index + 1) + " 个文字块内容不能为空");
        block.setX(clamp(block.getX(), 0, 99, 10));
        block.setY(clamp(block.getY(), 0, 96, 40));
        block.setWidth(clamp(block.getWidth(), 1, 100, 80));
        if (block.getX() + block.getWidth() > 100) block.setWidth(100 - block.getX());
        block.setFontSize(clamp(block.getFontSize(), 6, 120, 28));
        block.setMinFontSize(clamp(block.getMinFontSize(), 6, block.getFontSize(), 12));
        block.setFontFamily(normalizeFont(block.getFontFamily()));
        String alignment = StringUtils.hasText(block.getAlignment()) ? block.getAlignment().toUpperCase(Locale.ROOT) : "CENTER";
        if (!List.of("LEFT", "CENTER", "RIGHT").contains(alignment)) alignment = "CENTER";
        block.setAlignment(alignment);
        block.setColor(normalizeColor(block.getColor()));
        if (block.getAutoFit() == null) block.setAutoFit(true);
        if (block.getBold() == null) block.setBold(false);
        if (block.getUnderline() == null) block.setUnderline(false);
    }

    private boolean isQrEnabled(CertificateQrCodeBlockDTO qrCode) {
        return qrCode != null && Boolean.TRUE.equals(qrCode.getEnabled());
    }

    private void normalizeQrCode(CertificateQrCodeBlockDTO qrCode) {
        if (qrCode == null) return;
        if (qrCode.getEnabled() == null) qrCode.setEnabled(false);
        qrCode.setSize(clamp(qrCode.getSize(), 5, 40, 14));
        qrCode.setX(clamp(qrCode.getX(), 0, 100 - qrCode.getSize(), 82));
        qrCode.setY(clamp(qrCode.getY(), 0, 96, 72));
        if (qrCode.getX() + qrCode.getSize() > 100) qrCode.setX(100 - qrCode.getSize());
    }

    private void drawQrCode(PDDocument document, PDPage page, PDRectangle box, CertificateQrCodeBlockDTO qrCode,
                            String certificateNo) throws Exception {
        if (!StringUtils.hasText(certificateNo)) return;
        BufferedImage image = createQrImage(verifyUrl(certificateNo), 512);
        PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
        float size = (float) (box.getWidth() * qrCode.getSize() / 100d);
        float x = box.getLowerLeftX() + (float) (box.getWidth() * qrCode.getX() / 100d);
        float y = box.getLowerLeftY() + box.getHeight() - (float) (box.getHeight() * qrCode.getY() / 100d) - size;
        try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream.drawImage(pdfImage, x, Math.max(box.getLowerLeftY(), y), size, size);
        }
    }

    private String verifyUrl(String certificateNo) {
        return VERIFY_BASE_URL + "?number=" + URLEncoder.encode(certificateNo, StandardCharsets.UTF_8);
    }

    private BufferedImage createQrImage(String content, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int transparent = new Color(255, 255, 255, 0).getRGB();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : transparent);
            }
        }
        return image;
    }

    private double clamp(Double value, double min, double max, double fallback) {
        double normalized = value == null || !Double.isFinite(value) ? fallback : value;
        return Math.max(min, Math.min(max, normalized));
    }

    private String normalizeFont(String fontFamily) {
        if (!StringUtils.hasText(fontFamily)) return "SIMKAI";
        String normalized = fontFamily.toUpperCase(Locale.ROOT);
        return List.of("SIMSUN", "SIMKAI", "STXINGKA").contains(normalized) ? normalized : "SIMKAI";
    }

    private String normalizeColor(String color) {
        if (color != null && color.matches("#[0-9a-fA-F]{6}")) return color.toUpperCase(Locale.ROOT);
        return "#000000";
    }

    private Color parseColor(String color) {
        try { return Color.decode(normalizeColor(color)); }
        catch (Exception ignored) { return Color.BLACK; }
    }

    private float textWidth(PDType0Font font, String text, float size) throws Exception {
        return text == null || text.isEmpty() ? 0 : font.getStringWidth(text) / 1000f * size;
    }

    private String sanitize(PDType0Font font, String text) {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (codePoint == '\n' || codePoint == '\r') {
                result.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
                continue;
            }
            String value = new String(Character.toChars(codePoint));
            try {
                font.encode(value);
                result.append(value);
            } catch (Exception ignored) {
                result.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private LoadedFont loadFont(PDDocument document, String family) {
        // Legacy family identifiers remain accepted by stored template schemas.
        try (InputStream input = resource("fonts/NotoSansSC.ttf")) {
            return new LoadedFont(PDType0Font.load(document, input, true), null);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载开源中文字体", e);
        }
    }

    private InputStream resource(String path) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("缺少字体资源：" + path);
        return input;
    }

    private record LoadedFont(PDType0Font font, TrueTypeCollection collection) implements AutoCloseable {
        @Override
        public void close() {
            if (collection != null) {
                try { collection.close(); } catch (Exception ignored) { }
            }
        }
    }
}
