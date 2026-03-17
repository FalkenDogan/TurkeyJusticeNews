package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Utils {

    private static final String DEFAULT_SOURCE = "DİĞER";
    private static final int TELEGRAM_SAFE_LEN = 3900;

    static Set<String> parseChatIdsCsv(String csv) {
        Set<String> ids = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }

        String[] parts = csv.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    static int sendTelegramToMany(String token, Set<String> chatIds, String text) {
        int successCount = 0;
        for (String chatId : chatIds) {
            try {
                sendTelegram(token, chatId, text);
                successCount++;
            } catch (Exception e) {
                System.err.println("Telegram chat atlandi (" + chatId + "): " + e.getMessage());
            }
        }
        return successCount;
    }

    static int sendTelegramChunksToMany(String token, Set<String> chatIds, List<String> chunks) {
        int successCount = 0;
        for (String chatId : chatIds) {
            try {
                for (String chunk : chunks) {
                    sendTelegram(token, chatId, chunk);
                }
                successCount++;
            } catch (Exception e) {
                System.err.println("Telegram chat atlandi (" + chatId + "): " + e.getMessage());
            }
        }
        return successCount;
    }

    static Set<String> discoverChatIdsFromUpdates(String token) {
        Set<String> ids = new LinkedHashSet<>();
        HttpURLConnection conn = null;
        try {
            String urlString = "https://api.telegram.org/bot" + token +
                    "/getUpdates?limit=100&allowed_updates=%5B%22message%22%2C%22channel_post%22%5D";
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            if (code != 200) {
                System.err.println("getUpdates hatasi -> HTTP " + code);
                return ids;
            }

            try (InputStream in = conn.getInputStream()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(body);
                if (!json.optBoolean("ok", false)) {
                    return ids;
                }

                JSONArray result = json.optJSONArray("result");
                if (result == null) {
                    return ids;
                }

                for (int i = 0; i < result.length(); i++) {
                    JSONObject update = result.optJSONObject(i);
                    if (update == null) {
                        continue;
                    }

                    addChatIdFromUpdate(update, "message", ids);
                    addChatIdFromUpdate(update, "channel_post", ids);
                }
            }
        } catch (Exception e) {
            System.err.println("Chat kesfi yapilamadi: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return ids;
    }

    private static void addChatIdFromUpdate(JSONObject update, String fieldName, Set<String> target) {
        JSONObject message = update.optJSONObject(fieldName);
        if (message == null) {
            return;
        }

        JSONObject chat = message.optJSONObject("chat");
        if (chat == null) {
            return;
        }

        long id = chat.optLong("id", Long.MIN_VALUE);
        if (id != Long.MIN_VALUE) {
            target.add(String.valueOf(id));
        }
    }

    static void sendTelegram(String token, String chatId, String text) throws Exception {
        String urlString = "https://api.telegram.org/bot" + token + "/sendMessage";

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        JSONObject payload = new JSONObject();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        payload.put("parse_mode", "Markdown");

        byte[] output = payload.toString().getBytes(StandardCharsets.UTF_8);
        conn.getOutputStream().write(output);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            System.err.println("Telegram gönderme hatası → HTTP " + responseCode);
        }

        InputStream responseStream = responseCode >= 200 && responseCode < 300
                ? conn.getInputStream()
                : conn.getErrorStream();

        if (responseStream != null) {
            try (InputStream in = responseStream) {
                in.readAllBytes();
            }
        }
    }

    // Haberler listesini Telegram formatına çevirme (kaynağa göre gruplandırılmış)
    public static String formatNewsForTelegram(List<NewsItem> items) {
        List<String> chunks = formatNewsForTelegramChunks(items);
        if (chunks.isEmpty()) {
            return "";
        }
        return String.join("", chunks);
    }

    public static List<String> formatNewsForTelegramChunks(List<NewsItem> items) {
        List<String> chunks = new java.util.ArrayList<>();
        if (items == null || items.isEmpty()) {
            chunks.add("📋 Bugünün önemli hukuk ve yargı haberleri bulunamadı.");
            return chunks;
        }

        // Haberleri kaynak sırasını koruyarak gruplandır
        Map<String, List<NewsItem>> groupedBySource = new LinkedHashMap<>();
        for (NewsItem item : items) {
            String source = (item.source != null && !item.source.isBlank()) ? item.source : DEFAULT_SOURCE;
            groupedBySource.computeIfAbsent(source, k -> new java.util.ArrayList<>()).add(item);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Türkiye Hukuk ve Yargı Haberleri*\n\n");
        
        // Günün tarihini ekle
        LocalDate today = LocalDate.now().minusDays(0); // Haberler dünün tarihine göre çekildiği için
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        sb.append("📅 *").append(today.format(formatter)).append("*\n\n");

        String baseHeader = sb.toString();
        sb = new StringBuilder(baseHeader);

        for (Map.Entry<String, List<NewsItem>> entry : groupedBySource.entrySet()) {
            String sourceHeader = "📰 *" + entry.getKey() + "*\n\n";

            if (sb.length() + sourceHeader.length() > TELEGRAM_SAFE_LEN) {
                chunks.add(sb.toString());
                sb = new StringBuilder(baseHeader);
            }
            sb.append(sourceHeader);

            for (NewsItem item : entry.getValue()) {
                String title = item.title == null ? "" : item.title;
                String link = item.link == null ? "" : item.link;
                String newsBlock = "🔹 " + title + "\n" +
                        "[Detaylar için tıkla](" + link + ")\n\n";

                if (sb.length() + newsBlock.length() > TELEGRAM_SAFE_LEN) {
                    chunks.add(sb.toString());
                    sb = new StringBuilder(baseHeader);
                    sb.append(sourceHeader);
                }

                if (newsBlock.length() > TELEGRAM_SAFE_LEN) {
                    String truncatedTitle = title.length() > 500 ? title.substring(0, 500) + "..." : title;
                    newsBlock = "🔹 " + truncatedTitle + "\n" +
                            "[Detaylar için tıkla](" + link + ")\n\n";
                }

                sb.append(newsBlock);
            }
        }

        if (sb.length() > baseHeader.length()) {
            chunks.add(sb.toString());
        }

        if (chunks.isEmpty()) {
            chunks.add("📋 Bugünün önemli hukuk ve yargı haberleri bulunamadı.");
        }

        return chunks;
    }
}
