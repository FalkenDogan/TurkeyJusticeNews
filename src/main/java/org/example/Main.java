package org.example;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        try {
            ZoneId berlinZone = ZoneId.of("Europe/Berlin");
            ZonedDateTime berlinNow = ZonedDateTime.now(berlinZone);
            LocalDate targetDate = berlinNow.toLocalDate().minusDays(1);

            // 1. ADIM: RSS'lerden hedef gun (00:00-23:59 Berlin) haberlerini cek
            System.out.println("Haberler cekiliyor. Hedef gun: " + targetDate + " (Europe/Berlin)");
            NewsService newsService = new NewsService();
            List<NewsItem> targetDayNews = newsService.fetchNewsForDate(targetDate, berlinZone);

            if (targetDayNews.isEmpty()) {
                System.out.println("Uyari: Hedef gun icin haber bulunamadi: " + targetDate);
                return;
            }

            System.out.println(targetDayNews.size() + " haber bulundu.");

            // 2. ADIM: Haberlari AI'ya gonderilecek formata cevir [Sayi]- [Baslik]
            System.out.println("Haberler DeepSeek'e gonderiliyor...");
            String newsTextForAI = newsService.prepareTextForAI(targetDayNews);
            System.out.println("Gonderilen haber sayisi: " + targetDayNews.size());

            // 3. ADIM: DeepSeek'e gonder ve filtrele
            DeepSeekService deepSeekService = new DeepSeekService();
            String aiResponse = deepSeekService.filterNews(newsTextForAI);

            System.out.println("AI yaniti alindi.");
            System.out.println("AI yaniti (ilk 200 karakter): " +
                (aiResponse.length() > 200 ? aiResponse.substring(0, 200) + "..." : aiResponse));

            // 4. ADIM: AI'dan donen veriyi isle ve haber linklerini ekle
            List<NewsItem> filteredNews = newsService.filterByAIResponse(aiResponse, targetDayNews);
            System.out.println("Filtrelenmis haber sayisi: " + filteredNews.size());

            // 5. ADIM: Telegram'a gonder
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

                    System.out.println("Telegram'a gonderiliyor... (" + filteredNews.size() + " haber, hedef chat: " + recipientChatIds.size() + ")");
                    String formattedMessage = Utils.formatNewsForTelegram(filteredNews);
                    int successCount = Utils.sendTelegramToMany(telegramToken, recipientChatIds, formattedMessage);
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
}