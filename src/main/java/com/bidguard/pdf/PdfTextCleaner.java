package com.bidguard.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PdfTextCleaner {

    // ===== 正则预编译（性能 & 稳定性） =====
    
    // 短十六进制碎片：1-6个字符，只包含 0-9/a-f/A-F/-/空格
    private static final Pattern SHORT_HEX_NOISE =
            Pattern.compile("^[\\s0-9a-fA-F\\-]{1,6}$");

    // 长十六进制串：连续7个及以上的 0-9/a-f/A-F/-
    private static final Pattern LONG_HEX_NOISE =
            Pattern.compile("^[0-9a-fA-F\\-]{7,}$");

    private static final Pattern PAGE_NO =
            Pattern.compile("^(第?\\s*\\d+\\s*页.*|Page\\s*\\d+.*)$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PUNCT_ONLY =
            Pattern.compile("^[\\p{Punct}\\s]+$");

    /**
     * 主入口：PDFBox 抽出来的原始文本 → 过滤噪声行
     */
    public static String clean(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        String[] lines = rawText.split("\\r?\\n");
        List<String> kept = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();

            if (shouldDrop(trimmed)) {
                continue;
            }

            kept.add(trimmed);
        }

        return String.join("\n", kept);
    }

    // ===== 行级判定 =====

    private static boolean shouldDrop(String line) {
        if (line == null || line.isEmpty()) {
            return true;
        }

        // 短十六进制碎片（1-6个字符）
        if (SHORT_HEX_NOISE.matcher(line).matches()) {
            return true;
        }

        // 长十六进制串（7+字符）
        if (LONG_HEX_NOISE.matcher(line).matches()) {
            return true;
        }

        // 页码
        if (PAGE_NO.matcher(line).matches()) {
            return true;
        }

        // 纯符号
        if (PUNCT_ONLY.matcher(line).matches()) {
            return true;
        }

        return false;
    }

    // ===== 工具函数 =====

    private static boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }
}

