package org.example;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

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

            // 4. ADIM: AI'dan donen veriyi isle ve haber linklerini ekle
            List<NewsItem> filteredNews = newsService.filterByAIResponse(aiResponse, targetDayNews);

            // 5. ADIM: Telegram'a gonder
            if (!filteredNews.isEmpty()) {
                String telegramToken = System.getenv("TELEGRAM_BOT_TOKEN");
                String telegramChatId = System.getenv("TELEGRAM_CHAT_ID");

                if (telegramToken != null && telegramChatId != null) {
                    System.out.println("Telegram'a gonderiliyor... (" + filteredNews.size() + " haber)");
                    String formattedMessage = Utils.formatNewsForTelegram(filteredNews);
                    Utils.sendTelegram(telegramToken, telegramChatId, formattedMessage);
                    System.out.println("Telegram'a basariyla gonderildi.");
                } else {
                    System.err.println("TELEGRAM_BOT_TOKEN veya TELEGRAM_CHAT_ID ayarlanmadi.");
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