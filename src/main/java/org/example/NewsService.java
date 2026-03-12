package org.example;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.json.JSONArray;

import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

public class NewsService {

    private static final Pattern AI_INDEX_LINE_PATTERN =
            Pattern.compile("^\\[?\\s*(\\d+)\\s*\\]?\\s*[-).:]\\s*.*$");

    private static final String[] LEGAL_INCLUDE_KEYWORDS = {
            "yargi", "mahkeme", "dava", "hakim", "savci",
            "adalet", "anayasa mahkemesi", "aym", "danistay", "yargitay",
            "aihm", "hukuk", "tutuk", "gozalti", "cezaevi", "iskence",
            "ihlal", "hsk", "karar", "beraat", "iddianame", "yolsuzluk", "feto", "gulen", "hizmet hareketi"
    };

    private static final String[] NON_LEGAL_EXCLUDE_KEYWORDS = {
            "yemek", "tarif", "makarna", "limon", "burc", "astroloji", "moda", "magazin",
            "sac", "cilt", "makyaj", "diyet", "zayif", "burclar"
    };

    private static final Map<String, String> rssSourceMap;

    static {
        rssSourceMap = new LinkedHashMap<>();
        rssSourceMap.put("http://www.aa.com.tr/tr/rss/default?cat=guncel", "Anadolu Ajans");
        rssSourceMap.put("https://artigercek.com/service/rss.php", "ARTIGerçek");
        rssSourceMap.put("https://www.birgun.net/rss/home", "BİRGÜN");
        rssSourceMap.put("https://bianet.org/rss/bianet", "BİANET");
        rssSourceMap.put("https://www.cumhuriyet.com.tr/rss/son_dakika.xml", "CUMHURİYET");
        rssSourceMap.put("https://rss.dw.com/rdf/rss-tur-all", "DW TÜRKÇE");
        rssSourceMap.put("https://halktv.com.tr/service/rss.php", "HALK TV");
        rssSourceMap.put("https://www.milliyet.com.tr/rss/rssnew/sondakikarss.xml", "MİLLİYET");
        rssSourceMap.put("https://www.sozcu.com.tr/feeds-rss-category-gundem", "SÖZCÜ");

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

    // AI'dan dönen veriyi işleyerek haber linklerini tekrar ekleme
    public List<NewsItem> filterByAIResponse(String aiResponse, List<NewsItem> originalItems) {
        List<NewsItem> filteredItems = new ArrayList<>();

        if (aiResponse == null || aiResponse.isBlank() || aiResponse.trim().equals("[]")) {
            return filteredItems;
        }

        // 1) Once JSON dizi bekle: [1, 5, 16]
        try {
            String trimmed = aiResponse.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    int newsIndex = array.optInt(i, -1);
                    if (newsIndex >= 0 && newsIndex < originalItems.size()) {
                        NewsItem original = originalItems.get(newsIndex);
                        if (!filteredItems.contains(original)) {
                            filteredItems.add(original);
                        }
                    }
                }
                return applyDomainGuard(filteredItems);
            }
        } catch (Exception ignored) {
            // JSON parse basarisizsa satir bazli fallback'e gec
        }

        int parsedLineCount = 0;

        // Her satiri isle
        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            line = line.trim();

            // Kod blogu isaretleri veya bos satirlari atla
            if (line.isBlank() || line.equals("```") || line.startsWith("```")) {
                continue;
            }

            // Baslangictaki madde isaretlerini temizle
            if (line.startsWith("- ") || line.startsWith("* ")) {
                line = line.substring(2).trim();
            }

            // Desteklenen formatlar: [0]- Baslik, 0- Baslik, 0) Baslik, 0. Baslik
            Matcher matcher = AI_INDEX_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                parsedLineCount++;
                try {
                    int newsIndex = Integer.parseInt(matcher.group(1));

                    // Index gecerliyse orijinal haber linkini ekle (tekrar eklemeyi onle)
                    if (newsIndex >= 0 && newsIndex < originalItems.size()) {
                        NewsItem original = originalItems.get(newsIndex);
                        if (!filteredItems.contains(original)) {
                            filteredItems.add(original);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Gecersiz format, atla
                }
            }
        }

        if (parsedLineCount == 0 && !aiResponse.trim().equals("[]")) {
            System.err.println("Uyari: AI yanitinda indeksli satir parse edilemedi.");
        }

        return applyDomainGuard(filteredItems);
    }

    public List<NewsItem> applyDomainGuard(List<NewsItem> items) {
        List<NewsItem> guarded = new ArrayList<>();
        Set<String> seenLinks = new LinkedHashSet<>();

        for (NewsItem item : items) {
            if (item == null || item.title == null) {
                continue;
            }

            String lowerTitle = item.title.toLowerCase();
            boolean hasLegalSignal = containsAny(lowerTitle, LEGAL_INCLUDE_KEYWORDS);
            boolean hasNonLegalSignal = containsAny(lowerTitle, NON_LEGAL_EXCLUDE_KEYWORDS);

            if (hasLegalSignal && !hasNonLegalSignal) {
                String key = item.link == null ? item.title : item.link;
                if (!seenLinks.contains(key)) {
                    guarded.add(item);
                    seenLinks.add(key);
                }
            }
        }
        return guarded;
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
