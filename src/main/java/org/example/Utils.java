package org.example;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    private static final String DEFAULT_SOURCE = "DİĞER";

    static void sendTelegram(String token, String chatId, String text) throws Exception {
        String encodedText = java.net.URLEncoder.encode(text, "UTF-8");
        String urlString = "https://api.telegram.org/bot" + token +
                "/sendMessage?chat_id=" + chatId +
                "&text=" + encodedText +
                "&parse_mode=Markdown";

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            System.err.println("Telegram gönderme hatası → HTTP " + responseCode);
        }

        // Response body okumaya gerek yok ama bağlantıyı temizlemek için
        try (var in = conn.getInputStream()) {
            in.readAllBytes();
        }
    }

    // Haberler listesini Telegram formatına çevirme (kaynağa göre gruplandırılmış)
    public static String formatNewsForTelegram(List<NewsItem> items) {
        if (items == null || items.isEmpty()) {
            return "📋 Bugünün önemli hukuk ve yargı haberleri bulunamadı.";
        }

        // Haberleri kaynak sırasını koruyarak gruplandır
        Map<String, List<NewsItem>> groupedBySource = new LinkedHashMap<>();
        for (NewsItem item : items) {
            String source = (item.source != null && !item.source.isBlank()) ? item.source : DEFAULT_SOURCE;
            groupedBySource.computeIfAbsent(source, k -> new java.util.ArrayList<>()).add(item);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Türkiye Hukuk ve Yargı Haberleri*\n\n");

        for (Map.Entry<String, List<NewsItem>> entry : groupedBySource.entrySet()) {
            sb.append("📰 *").append(entry.getKey()).append("*\n\n");
            for (NewsItem item : entry.getValue()) {
                sb.append("🔹 ").append(item.title).append("\n");
                sb.append("[Detaylar için tıkla](").append(item.link).append(")\n\n");
            }
        }

        return sb.toString();
    }
}
