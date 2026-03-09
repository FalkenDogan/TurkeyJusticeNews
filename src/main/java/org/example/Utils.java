package org.example;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class Utils {

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

    // Haberler listesini Telegram formatına çevirme
    public static String formatNewsForTelegram(List<NewsItem> items) {
        if (items == null || items.isEmpty()) {
            return "📋 Bugünün önemli hukuk ve yargı haberleri bulunamadı.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Türkiye Hukuk ve Yargı Haberleri*\n\n");

        for (NewsItem item : items) {
            sb.append("🔹 ").append(item.title).append("\n");
            sb.append("[Detaylar için tıkla](").append(item.link).append(")\n\n");
        }

        return sb.toString();
    }
}
