package org.example;

import java.util.List;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        try {
            // 1. ADIM: RSS'lerden haberleri çek
            System.out.println("📰 Haberler çekiliyor...");
            NewsService newsService = new NewsService();
            List<NewsItem> todaysNews = newsService.fetchTodaysNews();

            if (todaysNews.isEmpty()) {
                System.out.println("⚠️ Bugünün haberleri bulunamadı.");
                return;
            }

            System.out.println("✅ " + todaysNews.size() + " haber bulundu.");

            // 2. ADIM: Haberları AI'ya gönderilecek formata çevir [Sayı]- [Başlık]
            System.out.println("🤖 Haberler Gemini'ye gönderiliyor...");
            String newsTextForAI = newsService.prepareTextForAI(todaysNews);
            System.out.println("Gönderilen haber sayısı: " + todaysNews.size());

            // 3. ADIM: Gemini'ye gönder ve filtrele
            GeminiService geminiService = new GeminiService();
            String aiResponse = geminiService.filterNews(newsTextForAI);
            System.out.println("AI Yanıtı alındı.");

            // 4. ADIM: AI'dan dönen veriyi işle ve haber linklerini ekle
            List<NewsItem> filteredNews = newsService.filterByAIResponse(aiResponse, todaysNews);

            // 5. ADIM: Telegram'a gönder
            if (!filteredNews.isEmpty()) {
                String telegramToken = System.getenv("TELEGRAM_BOT_TOKEN");
                String telegramChatId = System.getenv("TELEGRAM_CHAT_ID");

                if (telegramToken != null && telegramChatId != null) {
                    System.out.println("📨 Telegram'a gönderiliyor... (" + filteredNews.size() + " haber)");
                    String formattedMessage = Utils.formatNewsForTelegram(filteredNews);
                    Utils.sendTelegram(telegramToken, telegramChatId, formattedMessage);
                    System.out.println("✅ Telegram'a başarıyla gönderildi!");
                } else {
                    System.err.println("❌ TELEGRAM_BOT_TOKEN veya TELEGRAM_CHAT_ID environment variable'ı ayarlanmadı.");
                }
            } else {
                System.out.println("✅ Kriterlere uygun haber bulunamadı - boş array döndürüldü: []");
            }

        } catch (Exception e) {
            System.err.println("❌ Hata oluştu: " + e.getMessage());
            e.printStackTrace();
        }
    }
}