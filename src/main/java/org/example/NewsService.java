package org.example;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NewsService {

    private final String[] rssUrls = {
            "https://www.sozcu.com.tr/feeds-rss-category-gundem",
            "https://www.birgun.net/rss/home",
            "https://artigercek.com/service/rss.php",
            "https://bianet.org/biamag.rss",
            "https://www.cumhuriyet.com.tr/rss/son_dakika.xml",
            "https://www.diken.com.tr/feed/",
            "https://rss.dw.com/rdf/rss-tur-all",
            "https://www.gazeteduvar.com.tr/export/rss",
            "https://halktv.com.tr/service/rss.php"
    };

    public List<NewsItem> fetchTodaysNews() {
        List<NewsItem> allItems = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (String url : rssUrls) {
            try {
                URL feedSource = new URL(url);
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new XmlReader(feedSource));

                for (SyndEntry entry : feed.getEntries()) {
                    Date pubDate = entry.getPublishedDate();
                    // Eğer tarih yoksa veya bugüne aitse listeye ekle
                    if (pubDate != null) {
                        LocalDate entryDate = pubDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        if (entryDate.equals(today)) {
                            allItems.add(new NewsItem(entry.getTitle(), entry.getLink()));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Hata: " + url + " okunamadı. " + e.getMessage());
            }
        }
        return allItems;
    }

    // AI'ya gönderilecek numaralandırılmış string listesi
    public String prepareTextForAI(List<NewsItem> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append(i).append("- ").append(items.get(i).title).append("\n");
        }
        return sb.toString();
    }

    // Gemini'den dönen veriyi işleyerek haber linklerini tekrar ekleme
    public List<NewsItem> filterByAIResponse(String aiResponse, List<NewsItem> originalItems) {
        List<NewsItem> filteredItems = new ArrayList<>();

        // Her satırı işle
        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            // [Sayı]- formatını ara
            if (line.matches(".*\\d+-.*")) {
                try {
                    // Satırdan numarayı çıkar
                    int dashIndex = line.indexOf("-");
                    if (dashIndex > 0) {
                        String numberPart = line.substring(0, dashIndex).trim();
                        int newsIndex = Integer.parseInt(numberPart);

                        // Index geçerliyse orijinal haber linkini ekle
                        if (newsIndex >= 0 && newsIndex < originalItems.size()) {
                            NewsItem original = originalItems.get(newsIndex);
                            filteredItems.add(original);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Geçersiz format, atla
                }
            }
        }

        return filteredItems;
    }
}