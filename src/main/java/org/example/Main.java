package org.example;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        try {
            ZoneId berlinZone = ZoneId.of("Europe/Berlin");
            ZonedDateTime berlinNow = ZonedDateTime.now(berlinZone);
            LocalDate targetDate = berlinNow.toLocalDate().minusDays(0);

            // 1. ADIM: RSS'lerden hedef gun (00:00-23:59 Berlin) haberlerini cek
            System.out.println("========================================");
            System.out.println("RSS haberler cekiliyor. Hedef gun: " + targetDate + " (Europe/Berlin)");
            NewsService newsService = new NewsService();
            List<NewsItem> rssNews = newsService.fetchNewsForDate(targetDate, berlinZone);
            System.out.println("RSS'den bulunan haber sayisi: " + rssNews.size());

            // 2. ADIM: Bluesky'dan hedef gun haberlerini cek
            System.out.println("========================================");
            System.out.println("Bluesky haberler cekiliyor. Hedef gun: " + targetDate + " (Europe/Istanbul)");
            List<NewsItem> blueskyNews = fetchBlueskyNewsForDate(targetDate);
            System.out.println("Bluesky'dan bulunan haber sayisi: " + blueskyNews.size());

            // 3. ADIM: RSS ve Bluesky haberlerini birles
            List<NewsItem> allNews = new ArrayList<>(rssNews);
            allNews.addAll(blueskyNews);
            System.out.println("========================================");
            System.out.println("Toplam (RSS + Bluesky) haber sayisi: " + allNews.size());

            if (allNews.isEmpty()) {
                System.out.println("Uyari: Hedef gun icin haber bulunamadi: " + targetDate);
                return;
            }

            // 4. ADIM: Haberlari AI'ya gonderilecek formata cevir [Sayi]- [Baslik]
            System.out.println("========================================");
            System.out.println("Haberler DeepSeek'e gonderiliyor...");
            String newsTextForAI = newsService.prepareTextForAI(allNews);
            System.out.println("Gonderilen haber sayisi: " + allNews.size());

            // 5. ADIM: DeepSeek'e gonder ve filtrele
            DeepSeekService deepSeekService = new DeepSeekService();
            String aiResponse = deepSeekService.filterNews(newsTextForAI);

            System.out.println("AI yaniti alindi.");
            System.out.println("AI yaniti (ilk 200 karakter): " +
                (aiResponse.length() > 200 ? aiResponse.substring(0, 200) + "..." : aiResponse));

            // 6. ADIM: AI'dan donen veriyi isle ve haber linklerini ekle
            List<NewsItem> filteredNews = newsService.filterByAIResponse(aiResponse, allNews);
            System.out.println("========================================");
            System.out.println("Filtrelenmis haber sayisi: " + filteredNews.size());

            // 7. ADIM: Telegram'a gonder
            System.out.println("========================================");
            if (!filteredNews.isEmpty()) {
                String telegramToken = System.getenv("TELEGRAM_BOT_TOKEN");
                String telegramChatIdsCsv = System.getenv("TELEGRAM_CHAT_IDS");
                String telegramChatId = System.getenv("TELEGRAM_CHAT_ID");

                if (telegramToken != null && !telegramToken.isBlank()) {
                    Set<String> recipientChatIds = new LinkedHashSet<>();
                    recipientChatIds.addAll(Utils.parseChatIdsCsv(telegramChatIdsCsv));
                    if (telegramChatId != null && !telegramChatId.isBlank()) {
                        recipientChatIds.add(telegramChatId.trim());
                    }
                    recipientChatIds.addAll(Utils.discoverChatIdsFromUpdates(telegramToken));

                    if (recipientChatIds.isEmpty()) {
                        System.err.println("Hedef chat bulunamadi. TELEGRAM_CHAT_ID/TELEGRAM_CHAT_IDS ayarla veya bota /start gonder.");
                        return;
                    }

                    List<String> messageChunks = Utils.formatNewsForTelegramChunks(filteredNews);
                    System.out.println("Telegram'a gonderiliyor... (" + filteredNews.size() + " haber, hedef chat: " + recipientChatIds.size() + ", mesaj parcasi: " + messageChunks.size() + ")");
                    int successCount = Utils.sendTelegramChunksToMany(telegramToken, recipientChatIds, messageChunks);
                    System.out.println("Telegram gonderimi tamamlandi. Basarili chat sayisi: " + successCount + "/" + recipientChatIds.size());
                } else {
                    System.err.println("TELEGRAM_BOT_TOKEN ayarlanmadi.");
                }
            } else {
                System.out.println("Kriterlere uygun haber bulunamadi - bos array: []");
            }

        } catch (Exception e) {
            System.err.println("Hata olustu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<NewsItem> fetchBlueskyNewsForDate(LocalDate targetDate) {
        ZoneId targetZone = ZoneId.of("Europe/Istanbul");
        String[] blueskyActors = {
                "ankahaber.net",
                "t24.com.tr"
        };
        
        return BlueskyService.fetchAndFilterBlueskyNews(targetDate, targetZone, blueskyActors);
    }
}