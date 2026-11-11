package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.Major;
import com.cwnupaa.backend.entity.School;
import com.cwnupaa.backend.entity.SignupPersonal;
import com.cwnupaa.backend.entity.SignupTeam;
import com.cwnupaa.backend.entity.SignupTeamDetail;
import com.cwnupaa.backend.entity.University;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.mapper.MajorMapper;
import com.cwnupaa.backend.mapper.SchoolMapper;
import com.cwnupaa.backend.mapper.UniversityMapper;
import org.springframework.beans.factory.annotation.Value;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class AdmissionTicketPdfService {
    private static final float TITLE_SIZE = 18f;
    private static final float TABLE_FONT_SIZE = 12f;
    private static final float NOTICE_FONT_SIZE = 10.5f;
    private static final float PAGE_MARGIN = 32f;
    private static final float CELL_PADDING_X = 6f;
    private static final float CELL_PADDING_Y = 5f;
    private static final float LINE_HEIGHT = 16f;
    private static final float TABLE_ROW_HEIGHT = 32f;
    private static final int PHOTO_ROW_COUNT = 4;
    private static final float PHOTO_INSET_X = 2.5f;
    private static final float NOTICE_LINE_HEIGHT = 15.5f;
    private static final float LOGO_WIDTH = 72f;
    private static final float SEAL_SIZE = 116f;
    private static final float TEAM_SEAT_LEFT_SHIFT = 18f;
    private static final String ADMISSION_TICKET_SUBTITLE = "参赛证明";
    private static final String NOTICE_TITLE = "参赛选手须知";
    private static final String DEFAULT_NOTICE = "一、竞赛设备由赛事统一提供，无特殊说明选手不得自带设备；除比赛用机外，禁止访问网络、外接 USB 等外部资料。\n" +
            "二、选手须在规定时间到指定地点报到，配合工作人员完成身份与证件核查。\n" +
            "三、比赛期间选手可短暂离开赛场，但不可离开比赛区域，特殊情况需征得工作人员许可；休息、饮水、如厕时间计入竞赛总时长，赛场时钟为官方计时依据。\n" +
            "四、手机等通讯工具禁止带入赛场，选手之间严禁以任何形式互通传递比赛相关信息。\n" +
            "五、严禁擅自挪动赛场设备、私自修改比赛软硬件、干扰他人竞赛，违者直接剥夺参赛资格。\n" +
            "六、赛场内保持安静，不得喧哗，杜绝一切影响其他选手比赛的行为；爱护赛场器材，遵守操作规范，保障设备与人身安全，服从工作人员监管。\n" +
            "七、赛场遇到问题须举手示意，听从工作人员处置；文明参赛，尊重选手与工作人员，严禁辱骂、斗殴等不文明行为。\n" +
            "八、场外人员不得暗示、指导、协助参赛选手，由此产生的后果，将视情节扣减选手比赛成绩；非授权人员禁止进入比赛现场。\n" +
            "九、赛后代码将开展查重检测并在协会官网外榜公示，查重率偏高或使用 AI 工具视作作弊；对成绩有异议，可于赛后 2 个工作日内向组委会提交反馈。\n" +
            "十、严禁各类作弊行为，一经查实立即取消队伍参赛资格，并通报选手所在学校（学院）；若专项赛事规则与本总则冲突，以专项赛事规则为准；选手可带走气球等非实验室原有设备物品。";

    @Autowired
    private MajorMapper majorMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private UniversityMapper universityMapper;
    @Autowired
    private FileSystemMapper fileSystemMapper;
    @Autowired
    private FileStorageService fileStorageService;
    @Value("${file.upload.path:/tmp/uploads/}")
    private String uploadPath;

    public byte[] renderPersonal(Event event, SignupPersonal signup, Users user) throws Exception {
        Credential credential = new Credential(signup.getContestAccount(), signup.getContestPassword(), signup.getSeatNumber());
        Profile profile = profile(user);
        List<RowSpec> rows = new ArrayList<>(List.of(
                row(TABLE_ROW_HEIGHT, cell("姓名", 1), cell(profile.name(), 1), cell("性别", 1), cell(profile.gender(), 1)),
                row(TABLE_ROW_HEIGHT, cell("学校", 1), cell(profile.university(), 1), cell("学院", 1), cell(profile.school(), 1)),
                row(TABLE_ROW_HEIGHT, cell("专业", 1), cell(profile.major(), 1), cell("年级", 1), cell(profile.grade(), 1)),
                row(TABLE_ROW_HEIGHT, cell("学号", 1), cell(profile.numberId(), 1), cell("身份类型", 1), cell(profile.eduBg(), 1)),
                row(cell("参赛组别", 1), cell(formatTeamType(signup.getType(), false), 1), cell("是否排名", 1), cell(formatRanking(signup.getIsRanking()), 2)),
                row(cell("账号", 1), cell(credential.account(), 1), cell("指导老师", 1), cell(safe(signup.getAdvisor()), 2)),
                row(cell("密码", 1), cell(credential.password(), 1), cell("座位号", 1), cell(credential.seat(), 2))
        ));
        return render(event.getName(), rows, event.getAdmissionTicketRules(), user == null ? null : user.getIdPhotoFileId());
    }

    public byte[] renderTeam(Event event, SignupTeam signup, SignupTeamDetail myDetail, Users currentUser, List<Users> members) throws Exception {
        Credential credential = StringUtils.hasText(myDetail == null ? null : myDetail.getContestAccount())
                ? new Credential(myDetail.getContestAccount(), myDetail.getContestPassword(), myDetail.getSeatNumber())
                : new Credential(signup.getContestAccount(), signup.getContestPassword(), signup.getSeatNumber());
        Profile profile = profile(currentUser);
        String memberNames = members == null ? "" : members.stream().map(Users::getRealName).filter(StringUtils::hasText).collect(Collectors.joining("、"));
        List<RowSpec> rows = new ArrayList<>(List.of(
                row(TABLE_ROW_HEIGHT, cell("姓名", 1), cell(profile.name(), 1), cell("性别", 1), cell(profile.gender(), 1)),
                row(TABLE_ROW_HEIGHT, cell("学校", 1), cell(profile.university(), 1), cell("学院", 1), cell(profile.school(), 1)),
                row(TABLE_ROW_HEIGHT, cell("专业", 1), cell(profile.major(), 1), cell("年级", 1), cell(profile.grade(), 1)),
                row(TABLE_ROW_HEIGHT, cell("学号", 1), cell(profile.numberId(), 1), cell("身份类型", 1), cell(profile.eduBg(), 1)),
                row(cell("队伍名称", 1), cell(safe(signup.getTeamName()), 1), cell("指导老师", 1), cell(safe(signup.getAdvisor()), 2)),
                row(cell("队伍成员", 1), cell(memberNames, 4)),
                row(cell("参赛组别", 1), cell(formatTeamType(signup.getType(), true), 1), cell("是否排名", 1), cell(formatRanking(signup.getIsRanking()), 2)),
                teamCredentialRow(
                        cell("账号", 1), cell(credential.account(), 1),
                        cell("密码", 1), cell(credential.password(), 1),
                        cell("座位号", 1), cell(credential.seat(), 1))
        ));
        return render(event.getName(), rows, event.getAdmissionTicketRules(), currentUser == null ? null : currentUser.getIdPhotoFileId());
    }

    public boolean hasCredential(SignupPersonal signup) {
        return signup != null
                && StringUtils.hasText(signup.getContestAccount())
                && StringUtils.hasText(signup.getContestPassword())
                && StringUtils.hasText(signup.getSeatNumber());
    }

    public boolean hasCredential(SignupTeam signup, SignupTeamDetail detail) {
        boolean personalCredential = detail != null
                && StringUtils.hasText(detail.getContestAccount())
                && StringUtils.hasText(detail.getContestPassword())
                && StringUtils.hasText(detail.getSeatNumber());
        boolean teamCredential = signup != null
                && StringUtils.hasText(signup.getContestAccount())
                && StringUtils.hasText(signup.getContestPassword())
                && StringUtils.hasText(signup.getSeatNumber());
        return personalCredential || teamCredential;
    }

    private byte[] render(String eventName, List<RowSpec> rows, String notice, Long idPhotoFileId) throws Exception {
        try (PDDocument document = new PDDocument();
             LoadedFont loadedFont = loadSimSun(document);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType0Font font = loadedFont.font();
            PDImageXObject logo = loadLogo(document);
            PDImageXObject seal = loadSeal(document);
            PDRectangle box = page.getMediaBox();
            float contentWidth = box.getWidth() - PAGE_MARGIN * 2;
            float titleY = box.getHeight() - PAGE_MARGIN - imageHeight(logo, LOGO_WIDTH) - 24f;
            float subtitleY = titleY - 25f;
            float tableY = subtitleY - 30f;
            float labelWidth = labelColumnWidth(font, rows);
            float photoWidth = TABLE_ROW_HEIGHT * PHOTO_ROW_COUNT * 3f / 4f;
            float valueWidth = (contentWidth - photoWidth - labelWidth * 2f) / 2f;
            float[] colWidths = new float[]{labelWidth, valueWidth, labelWidth, valueWidth, photoWidth};

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.setLineWidth(.8f);
                drawLogo(stream, logo, box);
                drawCenteredText(stream, font, safe(eventName), TITLE_SIZE, titleY, box);
                drawCenteredText(stream, font, ADMISSION_TICKET_SUBTITLE, TITLE_SIZE, subtitleY, box);

                float y = tableY;
                int rowIndex = 0;
                float photoTop = tableY;
                float photoHeight = 0f;
                float photoX = PAGE_MARGIN + spanWidth(colWidths, 0, 4);
                for (RowSpec row : rows) {
                    float rowHeight = row.height() > 0 ? row.height() : dynamicRowHeight(font, row, colWidths);
                    float x = PAGE_MARGIN;
                    int colIndex = 0;
                    float[] rowWidths = rowCellWidths(font, row, colWidths);
                    for (int cellIndex = 0; cellIndex < row.cells().size(); cellIndex++) {
                        CellSpec cell = row.cells().get(cellIndex);
                        if (rowIndex < PHOTO_ROW_COUNT && colIndex >= 4) break;
                        float width = rowWidths[cellIndex];
                        stream.addRect(x, y - rowHeight, width, rowHeight);
                        stream.stroke();
                        drawCellText(stream, font, cell.text(), x, y, width, rowHeight);
                        x += width;
                        colIndex += cell.colSpan();
                    }
                    if (rowIndex < PHOTO_ROW_COUNT) photoHeight += rowHeight;
                    y -= rowHeight;
                    rowIndex++;
                    if (rowIndex == PHOTO_ROW_COUNT) {
                        drawPhotoCell(stream, document, font, idPhotoFileId, photoX, photoTop, photoWidth, photoHeight);
                        drawSeal(stream, seal, photoX, photoTop - photoHeight);
                    }
                }
                drawNotice(stream, font, box, y - 34f, StringUtils.hasText(notice) ? notice.trim() : DEFAULT_NOTICE);
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private PDImageXObject loadLogo(PDDocument document) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("images/logo.png")) {
            if (input == null) return null;
            return PDImageXObject.createFromByteArray(document, input.readAllBytes(), "logo.png");
        }
    }

    private PDImageXObject loadSeal(PDDocument document) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("images/association-seal.png")) {
            if (input == null) return null;
            return PDImageXObject.createFromByteArray(document, input.readAllBytes(), "association-seal.png");
        }
    }

    private void drawLogo(PDPageContentStream stream, PDImageXObject logo, PDRectangle box) throws Exception {
        if (logo == null) return;
        float logoHeight = imageHeight(logo, LOGO_WIDTH);
        float logoY = box.getHeight() - PAGE_MARGIN - logoHeight;
        stream.drawImage(logo, PAGE_MARGIN, logoY, LOGO_WIDTH, logoHeight);
    }

    private void drawSeal(PDPageContentStream stream, PDImageXObject seal,
                          float photoX, float photoBottom) throws Exception {
        if (seal == null) return;
        float sealX = photoX - SEAL_SIZE / 2f;
        float sealY = photoBottom - SEAL_SIZE / 2f;
        stream.drawImage(seal, sealX, sealY, SEAL_SIZE, SEAL_SIZE);
    }

    private void drawPhotoCell(PDPageContentStream stream, PDDocument document, PDType0Font font, Long fileId,
                               float x, float topY, float width, float height) throws Exception {
        stream.addRect(x, topY - height, width, height);
        stream.stroke();
        PDImageXObject photo = loadUserPhoto(document, fileId);
        if (photo == null) {
            drawCenteredInCell(stream, font, "证件照", x, topY, width, height, TABLE_FONT_SIZE);
            return;
        }
        float imageWidth = width - PHOTO_INSET_X * 2f;
        float imageHeight = imageWidth * 4f / 3f;
        float imageY = topY - height + (height - imageHeight) / 2f;
        stream.drawImage(photo, x + PHOTO_INSET_X, imageY, imageWidth, imageHeight);
        stream.addRect(x, topY - height, width, height);
        stream.stroke();
    }

    private PDImageXObject loadUserPhoto(PDDocument document, Long fileId) throws Exception {
        if (fileId == null) return null;
        FileSystem fs = fileSystemMapper.selectById(fileId);
        if (fs == null || !StringUtils.hasText(fs.getUrl())) return null;
        byte[] bytes;
        if (fileStorageService != null) {
            bytes = fileStorageService.readBytes(fs);
        } else {
            String relativePath = fs.getUrl() == null ? "" : fs.getUrl();
            if (relativePath.startsWith("/api")) relativePath = relativePath.substring(4);
            if (relativePath.startsWith("/uploads/")) relativePath = relativePath.substring("/uploads/".length());
            else if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            String base = uploadPath.endsWith(File.separator) || uploadPath.endsWith("/") ? uploadPath : uploadPath + File.separator;
            File local = new File(base + relativePath);
            bytes = local.isFile() ? Files.readAllBytes(local.toPath()) : null;
        }
        if (bytes == null) return null;
        return PDImageXObject.createFromByteArray(document, bytes, fs.getName());
    }

    private void drawCenteredInCell(PDPageContentStream stream, PDType0Font font, String text,
                                    float x, float topY, float width, float height, float size) throws Exception {
        String value = safe(text);
        float textWidth = textWidth(font, value, size);
        float textX = x + (width - textWidth) / 2f;
        float textY = topY - height / 2f - size / 3f;
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(textX, textY);
        stream.showText(sanitize(font, value));
        stream.endText();
    }

    private float imageHeight(PDImageXObject image, float width) {
        return image == null ? 0 : width * image.getHeight() / image.getWidth();
    }

    private void drawNotice(PDPageContentStream stream, PDType0Font font, PDRectangle box, float titleY, String notice) throws Exception {
        float contentWidth = box.getWidth() - PAGE_MARGIN * 2;
        drawCenteredText(stream, font, NOTICE_TITLE, TITLE_SIZE, titleY, box);
        float availableHeight = titleY - 24f - PAGE_MARGIN;
        float fontSize = NOTICE_FONT_SIZE;
        float lineHeight = NOTICE_LINE_HEIGHT;
        List<String> lines = wrap(font, notice, contentWidth, fontSize);
        while (fontSize > 8f && lines.size() * lineHeight > availableHeight) {
            fontSize -= .5f;
            lineHeight = fontSize * 1.5f;
            lines = wrap(font, notice, contentWidth, fontSize);
        }
        float y = titleY - 28f;
        for (String line : lines) {
            if (y < PAGE_MARGIN) break;
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(PAGE_MARGIN, y);
            stream.showText(sanitize(font, line));
            stream.endText();
            y -= lineHeight;
        }
    }

    private void drawCenteredText(PDPageContentStream stream, PDType0Font font, String text, float size, float y, PDRectangle box) throws Exception {
        String value = safe(text);
        float textWidth = textWidth(font, value, size);
        float x = (box.getWidth() - textWidth) / 2f;
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitize(font, value));
        stream.endText();
    }

    private float fitFontSize(PDType0Font font, String text, float preferredSize, float minSize, float maxWidth) throws Exception {
        float size = preferredSize;
        while (size > minSize && textWidth(font, safe(text), size) > maxWidth) {
            size -= .5f;
        }
        return size;
    }

    private void drawCellText(PDPageContentStream stream, PDType0Font font, String text,
                              float x, float topY, float width, float height) throws Exception {
        List<String> lines = wrap(font, safe(text), width - CELL_PADDING_X * 2, TABLE_FONT_SIZE);
        float textBlockHeight = TABLE_FONT_SIZE + Math.max(0, lines.size() - 1) * LINE_HEIGHT;
        float textY = topY - (height - textBlockHeight) / 2f - TABLE_FONT_SIZE;
        float minY = topY - height + CELL_PADDING_Y;
        for (String line : lines) {
            if (textY < minY) break;
            stream.beginText();
            stream.setFont(font, TABLE_FONT_SIZE);
            stream.newLineAtOffset(x + CELL_PADDING_X, textY);
            stream.showText(sanitize(font, line));
            stream.endText();
            textY -= LINE_HEIGHT;
        }
    }

    private List<String> wrap(PDType0Font font, String text, float maxWidth, float fontSize) throws Exception {
        List<String> lines = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String token = new String(Character.toChars(codePoint));
            if ("\n".equals(token) || "\r".equals(token)) {
                lines.add(current.toString());
                current.setLength(0);
            } else if (textWidth(font, current + token, fontSize) <= maxWidth || current.isEmpty()) {
                current.append(token);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(token);
            }
            offset += Character.charCount(codePoint);
        }
        lines.add(current.toString());
        return lines;
    }

    private float dynamicRowHeight(PDType0Font font, RowSpec row, float[] colWidths) throws Exception {
        int maxLines = 1;
        float[] rowWidths = rowCellWidths(font, row, colWidths);
        for (int cellIndex = 0; cellIndex < row.cells().size(); cellIndex++) {
            CellSpec cell = row.cells().get(cellIndex);
            float width = rowWidths[cellIndex];
            maxLines = Math.max(maxLines, wrap(font, safe(cell.text()), width - CELL_PADDING_X * 2, TABLE_FONT_SIZE).size());
        }
        return Math.max(TABLE_ROW_HEIGHT, CELL_PADDING_Y * 2 + TABLE_FONT_SIZE + (maxLines - 1) * LINE_HEIGHT);
    }

    private float[] rowCellWidths(PDType0Font font, RowSpec row, float[] colWidths) throws Exception {
        float[] widths = new float[row.cells().size()];
        if (row.layout() == RowLayout.STANDARD) {
            int colIndex = 0;
            for (int cellIndex = 0; cellIndex < row.cells().size(); cellIndex++) {
                CellSpec cell = row.cells().get(cellIndex);
                widths[cellIndex] = spanWidth(colWidths, colIndex, cell.colSpan());
                colIndex += cell.colSpan();
            }
            return widths;
        }

        if (row.layout() == RowLayout.TEAM_CREDENTIAL) {
            System.arraycopy(colWidths, 0, widths, 0, 4);
            widths[3] -= TEAM_SEAT_LEFT_SHIFT;
            widths[4] = textWidth(font, row.cells().get(4).text(), TABLE_FONT_SIZE) + CELL_PADDING_X * 2f;
            widths[5] = colWidths[4] + TEAM_SEAT_LEFT_SHIFT - widths[4];
            return widths;
        }

        return widths;
    }

    private float labelColumnWidth(PDType0Font font, List<RowSpec> rows) throws Exception {
        float maxTextWidth = 0f;
        for (RowSpec row : rows) {
            if (row.layout() != RowLayout.STANDARD) continue;
            int colIndex = 0;
            for (CellSpec cell : row.cells()) {
                if (colIndex == 0 || colIndex == 2) {
                    maxTextWidth = Math.max(maxTextWidth, textWidth(font, cell.text(), TABLE_FONT_SIZE));
                }
                colIndex += cell.colSpan();
            }
        }
        return maxTextWidth + CELL_PADDING_X * 2f;
    }

    private Profile profile(Users user) {
        if (user == null) return new Profile("", "", "", "", "", "", "", "");
        Major major = user.getMajorId() == null ? null : majorMapper.selectById(user.getMajorId());
        School school = major == null || major.getSchoolId() == null ? null : schoolMapper.selectById(major.getSchoolId());
        University university = school == null || school.getUniversityId() == null ? null : universityMapper.selectById(school.getUniversityId());
        String universityName = university == null ? "" : safe(university.getUniversityName());
        String schoolName = school == null ? "" : safe(school.getSchoolName());
        String majorName = major == null ? "" : safe(major.getMajorName());
        boolean duplicateSchool = sameName(schoolName, universityName);
        boolean duplicateMajor = sameName(majorName, schoolName) || sameName(majorName, universityName);
        return new Profile(
                safe(user.getRealName()),
                safe(user.getNumberId()),
                gender(user.getGender()),
                universityName,
                duplicateSchool ? "" : schoolName,
                duplicateMajor ? "" : majorName,
                safe(user.getGradeYear()),
                formatEduBg(user.getEduBg())
        );
    }

    private boolean sameName(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.trim().equals(right.trim());
    }

    private LoadedFont loadSimSun(PDDocument document) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("fonts/NotoSansSC.ttf")) {
            if (input == null) throw new IllegalStateException("缺少开源字体资源 fonts/NotoSansSC.ttf");
            return new LoadedFont(PDType0Font.load(document, input, true), null);
        }
    }

    private float spanWidth(float[] widths, int start, int colSpan) {
        float width = 0;
        for (int i = start; i < Math.min(widths.length, start + colSpan); i++) width += widths[i];
        return width;
    }

    private float textWidth(PDType0Font font, String text, float size) throws Exception {
        return !StringUtils.hasText(text) ? 0 : font.getStringWidth(sanitize(font, text)) / 1000f * size;
    }

    private String sanitize(PDType0Font font, String text) {
        StringBuilder result = new StringBuilder();
        String value = text == null ? "" : text;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String piece = new String(Character.toChars(codePoint));
            try {
                font.encode(piece);
                result.append(piece);
            } catch (Exception ignored) {
                result.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private String gender(Integer gender) {
        if (gender == null) return "";
        if (gender == 1) return "男";
        if (gender == 2) return "女";
        return "未知";
    }

    private String formatRanking(Integer value) {
        if (value == null) return "";
        return value == 0 ? "否" : "是";
    }

    private String formatTeamType(String value, boolean team) {
        if (!StringUtils.hasText(value)) return "";
        if ("official".equals(value)) return team ? "正式队伍" : "正式参赛";
        if ("star".equals(value)) return team ? "打星队伍" : "打星参赛";
        if ("girl".equals(value)) return "女生队伍";
        return value;
    }

    private String formatEduBg(String value) {
        if (!StringUtils.hasText(value)) return "";
        return switch (value) {
            case "undergraduate" -> "本科生";
            case "postgraduate" -> "研究生";
            case "teacher" -> "教师";
            case "department" -> "部门";
            case "off-campus" -> "校外";
            default -> value;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private RowSpec row(CellSpec... cells) {
        return row(0, cells);
    }

    private RowSpec row(float height, CellSpec... cells) {
        return new RowSpec(height, List.of(cells), RowLayout.STANDARD);
    }

    private RowSpec teamCredentialRow(CellSpec... cells) {
        return new RowSpec(0, List.of(cells), RowLayout.TEAM_CREDENTIAL);
    }

    private CellSpec cell(String text, int colSpan) {
        return new CellSpec(StringUtils.hasText(text) ? text : "无", colSpan);
    }

    private record Credential(String account, String password, String seat) {}
    private record Profile(String name, String numberId, String gender, String university, String school, String major, String grade, String eduBg) {}
    private record CellSpec(String text, int colSpan) {}
    private enum RowLayout { STANDARD, TEAM_CREDENTIAL }
    private record RowSpec(float height, List<CellSpec> cells, RowLayout layout) {}
    private record LoadedFont(PDType0Font font, TrueTypeCollection collection) implements AutoCloseable {
        @Override
        public void close() {
            if (collection != null) {
                try { collection.close(); } catch (Exception ignored) { }
            }
        }
    }
}
