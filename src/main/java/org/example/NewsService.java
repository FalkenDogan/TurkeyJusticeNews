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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NewsService {

    private static final Map<String, String> rssSourceMap;

    static {
        rssSourceMap = new LinkedHashMap<>();
        rssSourceMap.put("https://www.sozcu.com.tr/feeds-rss-category-gundem", "SÖZCÜ");
        rssSourceMap.put("https://www.birgun.net/rss/home", "BİRGÜN");
        rssSourceMap.put("https://artigercek.com/service/rss.php", "ARTIGerçek");
        rssSourceMap.put("https://bianet.org/rss/bianet", "BİANET");
        rssSourceMap.put("https://www.cumhuriyet.com.tr/rss/son_dakika.xml", "CUMHURİYET");
        rssSourceMap.put("https://www.diken.com.tr/feed/", "DİKEN");
        rssSourceMap.put("https://rss.dw.com/rdf/rss-tur-all", "DW TÜRKÇE");
        rssSourceMap.put("https://halktv.com.tr/service/rss.php", "HALK TV");
    }

    public List<NewsItem> fetchTodaysNews() {
        // Geriye donuk uyumluluk: eski cagriyi bugunun Berlin tarihine yonlendir.
        return fetchNewsForDate(LocalDate.now(ZoneId.of("Europe/Berlin")), ZoneId.of("Europe/Berlin"));
    }

    public List<NewsItem> fetchNewsForDate(LocalDate targetDate, ZoneId zoneId) {
        List<NewsItem> allItems = new ArrayList<>();

        for (Map.Entry<String, String> entry : rssSourceMap.entrySet()) {
            String url = entry.getKey();
            String sourceName = entry.getValue();
            try {
                URL feedSource = new URL(url);
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new XmlReader(feedSource));

                for (SyndEntry syndEntry : feed.getEntries()) {
                    Date pubDate = syndEntry.getPublishedDate();
                    if (pubDate == null) {
                        continue;
                    }

                    LocalDate entryDate = pubDate.toInstant().atZone(zoneId).toLocalDate();
                    if (entryDate.equals(targetDate)) {
                        allItems.add(new NewsItem(syndEntry.getTitle(), syndEntry.getLink(), sourceName));
                    }
                }
            } catch (Exception e) {
                System.err.println("Hata: " + url + " okunamadi. " + e.getMessage());
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
