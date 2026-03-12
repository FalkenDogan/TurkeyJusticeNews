package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlueskyService {

    private static final String BASE_URL = "https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed";
    private static final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int PAGE_LIMIT = 100;
    private static final int MAX_PAGES_PER_ACTOR = 30;
    private static final int AI_CHUNK_SIZE = 120;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final String[] STRICT_LEGAL_KEYWORDS = {
            "mahkeme", "dava", "hakim", "savci", "yargi", "hukuk",
            "anayasa mahkemesi", "aym", "danistay", "yargitay", "aihm", "hsk",
            "adalet bakanligi", "iddianame", "beraat", "tahliye", "anayasa",
            "insan hak", "hak ihlali", "cezaevi", "iskence", "kotu muamele",
            "keyfi tutuk", "gozalti", "tutuklu", "hizmet hareketi", "gulen", "feto"
    };
    private static final String[] GENERIC_CRIME_KEYWORDS = {
            "yaral", "oldur", "bicak", "silah", "cinayet", "hirsiz", "dolandir", "uyusturucu",
            "kaza", "deprem", "yangin", "akaryakit", "altin", "bitcoin", "faiz", "zam",
            "ekonomi", "futbol", "spor", "hava durumu", "burc", "magazin", "tarif"
    };

    // Manuel test icin isterseniz buraya dogrudan key yazabilirsiniz. Bos birakilirsa ortam degiskeni kullanilir.
    private static String LOCAL_DEEPSEEK_API_KEY = "";

    // Gazetelerin yayin saatleri icin varsayilan saat dilimi. Gerekirse Europe/Berlin yapabilirsiniz.
    private static final ZoneId TARGET_ZONE = ZoneId.of("Europe/Istanbul");

    // Referans tarih: burada bugunu/dunu manuel test edebilirsiniz.
    private static final OffsetDateTime TODAY = OffsetDateTime.of(2026, 3, 12, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final int MINUS_DAYS = 0;

    private static final String[] ACTORS = {
            "ankahaber.net",
            "t24.com.tr",
    };

    /**
     * Belirli bir tarihteki tüm Bluesky haberlerini çeker, filtreler ve hazırlar.
     * Main akışına entegre olmak için public olarak sunulmuştur.
     */
    public static List<NewsItem> fetchAndFilterBlueskyNews(LocalDate targetDate, ZoneId zoneId, String... actorHandles) {
        if (actorHandles == null || actorHandles.length == 0) {
            actorHandles = ACTORS;
        }

        System.out.println("[BlueskyService] Tarama başlıyor: " + targetDate + " (" + zoneId + ")");
        System.out.println("[BlueskyService] Aktif hesap sayısı: " + actorHandles.length);

        List<FetchedNews> fetchedNews = new ArrayList<>();
        for (String actor : actorHandles) {
            System.out.println("[BlueskyService]   -> " + actor);
            List<FetchedNews> actorNews = fetchNewsForDate(actor, targetDate);
            System.out.println("[BlueskyService]      " + actorNews.size() + " haber bulundu");
            fetchedNews.addAll(actorNews);
        }

        fetchedNews.sort(Comparator.comparing(item -> item.publishedAt));
        List<NewsItem> rawItems = dedupeFetchedNews(fetchedNews);
        System.out.println("[BlueskyService] Toplam ayıklanan ham haber: " + rawItems.size());

        if (rawItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<NewsItem> filteredItems = filterLegalNews(rawItems);
        System.out.println("[BlueskyService] Hukuk/yargı filtresinden geçen: " + filteredItems.size());
        return filteredItems;
    }

    public static void main(String[] args) {
        LocalDate targetDate = TODAY.atZoneSameInstant(TARGET_ZONE).toLocalDate().minusDays(MINUS_DAYS);

        System.out.println("Bluesky haber taraması başlıyor...");
        System.out.println("Hedef tarih: " + targetDate + " (" + TARGET_ZONE + ")");
        System.out.println("Aktif hesap sayısı: " + ACTORS.length);
        System.out.println("----------------------------------------");

        List<NewsItem> filteredItems = fetchAndFilterBlueskyNews(targetDate, TARGET_ZONE, ACTORS);

        if (filteredItems.isEmpty()) {
            System.out.println("Seçilen tarihte uygun Bluesky haber linki bulunamadı.");
            return;
        }

        System.out.println("Yargı/hukuk filtresinden geçen haber sayısı: " + filteredItems.size());

        String telegramMessage = Utils.formatNewsForTelegram(filteredItems);
        System.out.println("\nTelegram mesaji onizlemesi:\n");
        System.out.println(telegramMessage);

        sendToTelegramIfConfigured(telegramMessage);

        JsonObject debugResult = new JsonObject();
        debugResult.addProperty("targetDate", targetDate.toString());
        debugResult.addProperty("zone", TARGET_ZONE.toString());
        debugResult.addProperty("filteredCount", filteredItems.size());
        debugResult.add("filteredItems", toJsonArray(filteredItems));

        System.out.println("\nJSON debug ozeti:\n" + GSON.toJson(debugResult));
    }

    private static List<FetchedNews> fetchNewsForDate(String actor, LocalDate targetDate) {
        Map<String, FetchedNews> selected = new LinkedHashMap<>();
        String cursor = null;
        Set<String> seenCursors = new LinkedHashSet<>();

        for (int page = 1; page <= MAX_PAGES_PER_ACTOR; page++) {
            JsonObject pageJson = fetchFeedPage(actor, cursor);
            if (pageJson == null) {
                break;
            }

            JsonArray feed = pageJson.getAsJsonArray("feed");
            if (feed == null || feed.isEmpty()) {
                break;
            }

            int pageMatches = 0;
            boolean sawTargetOrNewer = false;

            for (JsonElement element : feed) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                JsonObject feedEntry = element.getAsJsonObject();
                if (isRepost(feedEntry)) {
                    continue;
                }

                JsonObject postObj = feedEntry.getAsJsonObject("post");
                if (postObj == null) {
                    continue;
                }

                OffsetDateTime publishedAt = extractPublishedAt(postObj);
                if (publishedAt == null) {
                    continue;
                }

                LocalDate postDate = publishedAt.atZoneSameInstant(TARGET_ZONE).toLocalDate();
                if (!postDate.isBefore(targetDate)) {
                    sawTargetOrNewer = true;
                }

                if (!postDate.equals(targetDate)) {
                    continue;
                }

                NewsItem candidate = extractNewsItem(feedEntry, actor);
                if (candidate == null) {
                    continue;
                }

                String dedupeKey = buildDedupeKey(candidate.link, candidate.title);
                if (!selected.containsKey(dedupeKey)) {
                    selected.put(dedupeKey, new FetchedNews(candidate, publishedAt));
                    pageMatches++;
                }
            }

            System.out.println("Sayfa " + page + " tarandi, tarihe uyan yeni haber: " + pageMatches);

            String nextCursor = nextCursor(pageJson);
            if (nextCursor == null || nextCursor.isBlank() || seenCursors.contains(nextCursor)) {
                break;
            }

            seenCursors.add(nextCursor);
            cursor = nextCursor;

            if (!sawTargetOrNewer) {
                break;
            }
        }

        return new ArrayList<>(selected.values());
    }

    private static JsonObject fetchFeedPage(String actor, String cursor) {
        try {
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("?actor=")
                    .append(URLEncoder.encode(actor, StandardCharsets.UTF_8))
                    .append("&limit=")
                    .append(PAGE_LIMIT);

            if (cursor != null && !cursor.isBlank()) {
                url.append("&cursor=")
                        .append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Bluesky API hatasi -> " + actor + " / HTTP " + response.statusCode());
                System.err.println(response.body());
                return null;
            }

            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (IOException | InterruptedException | JsonSyntaxException e) {
            System.err.println("Bluesky verisi cekilirken hata (" + actor + "): " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            System.err.println("Bluesky verisi parse edilemedi (" + actor + "): " + e.getMessage());
            return null;
        }
    }

    private static boolean isRepost(JsonObject feedEntry) {
        JsonObject reason = feedEntry.getAsJsonObject("reason");
        if (reason == null || !reason.has("$type")) {
            return false;
        }
        String type = safeAsString(reason.get("$type"));
        return type != null && type.toLowerCase().contains("repost");
    }

    private static OffsetDateTime extractPublishedAt(JsonObject postObj) {
        JsonObject record = postObj.getAsJsonObject("record");
        String createdAt = record == null ? null : safeAsString(record.get("createdAt"));
        if (createdAt != null) {
            try {
                return OffsetDateTime.parse(createdAt);
            } catch (Exception ignored) {
            }
        }

        String indexedAt = safeAsString(postObj.get("indexedAt"));
        if (indexedAt != null) {
            try {
                return OffsetDateTime.parse(indexedAt);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static NewsItem extractNewsItem(JsonObject feedEntry, String fallbackActor) {
        JsonObject postObj = feedEntry.getAsJsonObject("post");
        if (postObj == null) {
            return null;
        }

        JsonObject record = postObj.getAsJsonObject("record");
        String recordText = record == null ? null : safeAsString(record.get("text"));
        String fallbackTitle = sanitizeTitle(recordText);

        LinkCandidate candidate = extractFromEmbed(postObj.get("embed"), fallbackTitle);
        if (candidate == null) {
            candidate = extractFromFacets(record, fallbackTitle);
        }
        if (candidate == null) {
            candidate = extractFromText(recordText, fallbackTitle);
        }
        if (candidate == null) {
            return null;
        }

        String source = extractSourceName(postObj, fallbackActor);
        return new NewsItem(candidate.title, candidate.uri, source);
    }

    private static LinkCandidate extractFromEmbed(JsonElement element, String fallbackTitle) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();

            if (object.has("external") && object.get("external").isJsonObject()) {
                LinkCandidate direct = createCandidate(object.getAsJsonObject("external"), fallbackTitle);
                if (direct != null) {
                    return direct;
                }
            }

            if (object.has("media")) {
                LinkCandidate media = extractFromEmbed(object.get("media"), fallbackTitle);
                if (media != null) {
                    return media;
                }
            }

            if (object.has("record")) {
                LinkCandidate record = extractFromEmbed(object.get("record"), fallbackTitle);
                if (record != null) {
                    return record;
                }
            }

            LinkCandidate objectCandidate = createCandidate(object, fallbackTitle);
            if (objectCandidate != null) {
                return objectCandidate;
            }

            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                LinkCandidate nested = extractFromEmbed(entry.getValue(), fallbackTitle);
                if (nested != null) {
                    return nested;
                }
            }
        }

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                LinkCandidate nested = extractFromEmbed(child, fallbackTitle);
                if (nested != null) {
                    return nested;
                }
            }
        }

        return null;
    }

    private static LinkCandidate extractFromFacets(JsonObject record, String fallbackTitle) {
        if (record == null) {
            return null;
        }

        JsonArray facets = record.getAsJsonArray("facets");
        if (facets == null) {
            return null;
        }

        for (JsonElement facetElement : facets) {
            if (facetElement == null || !facetElement.isJsonObject()) {
                continue;
            }

            JsonArray features = facetElement.getAsJsonObject().getAsJsonArray("features");
            if (features == null) {
                continue;
            }

            for (JsonElement featureElement : features) {
                if (featureElement == null || !featureElement.isJsonObject()) {
                    continue;
                }

                String uri = safeAsString(featureElement.getAsJsonObject().get("uri"));
                if (isHttpUrl(uri)) {
                    return new LinkCandidate(resolveTitle(fallbackTitle), normalizeUrl(uri));
                }
            }
        }

        return null;
    }

    private static LinkCandidate extractFromText(String text, String fallbackTitle) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String uri = normalizeUrl(matcher.group());
        if (!isHttpUrl(uri)) {
            return null;
        }

        return new LinkCandidate(resolveTitle(fallbackTitle), uri);
    }

    private static LinkCandidate createCandidate(JsonObject object, String fallbackTitle) {
        String uri = normalizeUrl(safeAsString(object.get("uri")));
        if (!isHttpUrl(uri)) {
            return null;
        }

        String title = sanitizeTitle(safeAsString(object.get("title")));
        if (title == null) {
            title = resolveTitle(fallbackTitle);
        }
        if (title == null) {
            return null;
        }

        return new LinkCandidate(title, uri);
    }

    private static String extractSourceName(JsonObject postObj, String fallbackActor) {
        JsonObject author = postObj.getAsJsonObject("author");
        if (author != null) {
            String displayName = safeAsString(author.get("displayName"));
            if (displayName != null) {
                return displayName;
            }
            String handle = safeAsString(author.get("handle"));
            if (handle != null) {
                return handle;
            }
        }
        return fallbackActor;
    }

    private static List<NewsItem> dedupeFetchedNews(List<FetchedNews> fetchedNews) {
        Map<String, NewsItem> deduped = new LinkedHashMap<>();
        for (FetchedNews item : fetchedNews) {
            String key = buildDedupeKey(item.newsItem.link, item.newsItem.title);
            deduped.putIfAbsent(key, item.newsItem);
        }
        return new ArrayList<>(deduped.values());
    }

    private static List<NewsItem> filterLegalNews(List<NewsItem> rawItems) {
        NewsService newsService = new NewsService();
        List<NewsItem> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean anySelection = false;
        boolean aiUsed = false;

        for (int start = 0; start < rawItems.size(); start += AI_CHUNK_SIZE) {
            int end = Math.min(start + AI_CHUNK_SIZE, rawItems.size());
            List<NewsItem> chunk = new ArrayList<>(rawItems.subList(start, end));

            System.out.println("AI filtreleme parcasi: " + start + " - " + (end - 1));
            String allNewsText = newsService.prepareTextForAI(chunk);
            String aiResponse = requestAiSelection(allNewsText);
            if (aiResponse != null && !aiResponse.isBlank()) {
                aiUsed = true;
            }

            List<NewsItem> filteredChunk = newsService.filterByAIResponse(aiResponse, chunk);
            if (!filteredChunk.isEmpty()) {
                anySelection = true;
            }

            for (NewsItem item : filteredChunk) {
                String key = buildDedupeKey(item.link, item.title);
                if (seen.add(key)) {
                    merged.add(item);
                }
            }
        }

        if (anySelection) {
            return applyStrictBlueskyGuard(newsService.applyDomainGuard(merged));
        }

        System.out.println("AI secim yapmadi. Baslik anahtar kelimeleriyle ek guard uygulanacak.");
        if (!aiUsed && !hasAnyApiKey()) {
            System.out.println("Not: DeepSeek API anahtari olmadigi icin fallback guard kullaniliyor.");
        }
        return applyStrictBlueskyGuard(newsService.applyDomainGuard(rawItems));
    }

    private static List<NewsItem> applyStrictBlueskyGuard(List<NewsItem> items) {
        List<NewsItem> guarded = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (NewsItem item : items) {
            if (item == null || item.title == null) {
                continue;
            }

            String lowerTitle = item.title.toLowerCase();
            boolean hasStrictLegalSignal = containsAny(lowerTitle, STRICT_LEGAL_KEYWORDS);
            boolean hasGenericCrimeSignal = containsAny(lowerTitle, GENERIC_CRIME_KEYWORDS);

            if (!hasStrictLegalSignal) {
                continue;
            }

            if (hasGenericCrimeSignal && !containsInstitutionalSignal(lowerTitle)) {
                continue;
            }

            String key = buildDedupeKey(item.link, item.title);
            if (seen.add(key)) {
                guarded.add(item);
            }
        }

        return guarded;
    }

    private static String requestAiSelection(String allNewsText) {
        return callDeepSeek(allNewsText);
    }

    private static String callDeepSeek(String allNewsText) {
        String apiKey = firstNonBlank(LOCAL_DEEPSEEK_API_KEY, System.getenv("DEEPSEEK_API_KEY"));
        if (apiKey == null) {
            System.err.println("HATA: DEEPSEEK_API_KEY tanimlanmamis!");
            return "[]";
        }

        try {
            String prompt =
                    "Asagidaki numarali basliklardan SADECE Turkiye yargi-hukuk-insan haklari kapsamindaki haberleri sec.\n" +
                            "\n" +
                            "DAHIL ET:\n" +
                            "- Adalet Bakanligi, Yargitay, Danistay, AYM, AIHM karar/atama/gorev degisikligi\n" +
                            "- Yargi bagimsizligi, insan haklari ihlali, iskence, keyfi tutuklama, cezaevi ihlali\n" +
                            "- Hakim/savci suclari, yolsuzluk iddialari, dikkat cekici yargi kararlari\n" +
                            "- FETO, Gulen Hareketi, Hizmet Hareketi baglaminda yargi-hukuk haberleri\n" +
                            "\n" +
                            "DISLA:\n" +
                            "- Yemek, magazin, astroloji, burc, yasam tuyolari, spor, ekonomi-genel, dis politika-genel\n" +
                            "- Sadece siyasi, belediye, dis politika veya genel gundem haberleri\n" +
                            "- Adli/asayis haberleri: kavga, cinayet, yaralama, bicaklama, hirsizlik, uyusturucu operasyonu, trafik/kaza\n" +
                            "- Tekil tutuklama veya gozaltı haberlerini, kurumsal yargi/hak ihlali boyutu yoksa SECME\n" +
                            "- Mahkeme ilanlari/tebligat duyurulari\n" +
                            "\n" +
                            "KURAL: Emin degilsen HABERI SECME.\n" +
                            "\n" +
                            "CEVAP FORMATI ZORUNLU:\n" +
                            "- Sadece JSON dizi dondur.\n" +
                            "- Sadece indeksler olsun. Ornek: [3, 8, 25]\n" +
                            "- Aciklama, metin, markdown, kod blogu ekleme.\n" +
                            "- Uygun haber yoksa [] dondur.\n" +
                            "\n" +
                            "HABERLER:\n" + allNewsText;

            JSONObject requestBody = new JSONObject()
                    .put("model", "deepseek-chat")
                    .put("messages", new JSONArray()
                            .put(new JSONObject().put("role", "system").put("content", "You are a strict news filter."))
                            .put(new JSONObject().put("role", "user").put("content", prompt)))
                    .put("temperature", 0.1);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEEPSEEK_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("DeepSeek API hatasi: " + response.statusCode());
                System.err.println(response.body());
                return "[]";
            }

            JSONObject responseJson = new JSONObject(response.body());
            JSONArray choices = responseJson.optJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "[]";
            }

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                return "[]";
            }

            return normalizeToJsonIndexArray(message.optString("content", "[]"));
        } catch (IOException | InterruptedException e) {
            System.err.println("DeepSeek cagrisi sirasinda hata: " + e.getMessage());
            Thread.currentThread().interrupt();
            return "[]";
        } catch (Exception e) {
            System.err.println("DeepSeek yaniti parse edilemedi: " + e.getMessage());
            return "[]";
        }
    }

    private static String normalizeToJsonIndexArray(String content) {
        if (content == null || content.isBlank()) {
            return "[]";
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed = trimmed.replace("```json", "").replace("```", "").trim();
        }

        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return "[]";
        }

        String candidate = trimmed.substring(start, end + 1).trim();
        try {
            JSONArray arr = new JSONArray(candidate);
            JSONArray onlyInts = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                Object value = arr.get(i);
                if (value instanceof Number) {
                    onlyInts.put(((Number) value).intValue());
                }
            }
            return onlyInts.toString();
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private static void sendToTelegramIfConfigured(String telegramMessage) {
        String token = firstNonBlank(System.getenv("TELEGRAM_BOT_TOKEN"), System.getenv("TELEGRAM_TOKEN"));
        if (token == null) {
            System.out.println("Telegram token tanimli degil, mesaj sadece konsola yazdirildi.");
            return;
        }

        Set<String> chatIds = new LinkedHashSet<>(Utils.parseChatIdsCsv(System.getenv("TELEGRAM_CHAT_IDS")));

        String singleChatId = System.getenv("TELEGRAM_CHAT_ID");
        if (singleChatId != null && !singleChatId.isBlank()) {
            chatIds.add(singleChatId.trim());
        }

        if (chatIds.isEmpty()) {
            chatIds.addAll(Utils.discoverChatIdsFromUpdates(token));
        }

        if (chatIds.isEmpty()) {
            System.out.println("Telegram chat id bulunamadi, mesaj gonderilmedi.");
            return;
        }

        int successCount = Utils.sendTelegramToMany(token, chatIds, telegramMessage);
        System.out.println("Telegram gonderim basarili chat sayisi: " + successCount + "/" + chatIds.size());
    }

    private static JsonArray toJsonArray(List<NewsItem> items) {
        JsonArray array = new JsonArray();
        for (NewsItem item : items) {
            JsonObject obj = new JsonObject();
            obj.addProperty("title", item.title);
            obj.addProperty("link", item.link);
            obj.addProperty("source", item.source);
            array.add(obj);
        }
        return array;
    }

    private static String nextCursor(JsonObject json) {
        return safeAsString(json.get("cursor"));
    }

    private static boolean hasAnyApiKey() {
        return !LOCAL_DEEPSEEK_API_KEY.isBlank()
                || (System.getenv("DEEPSEEK_API_KEY") != null && !System.getenv("DEEPSEEK_API_KEY").isBlank());
    }

    private static boolean containsInstitutionalSignal(String text) {
        return containsAny(text, new String[]{
                "mahkeme", "dava", "hakim", "savci", "yargitay", "danistay",
                "anayasa mahkemesi", "aym", "aihm", "hsk", "adalet bakanligi", "iddianame", "tahliye"
        });
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String buildDedupeKey(String link, String title) {
        String normalizedLink = normalizeUrl(link);
        if (normalizedLink != null) {
            return normalizedLink;
        }
        return title == null ? "" : title.trim().toLowerCase();
    }

    private static String resolveTitle(String title) {
        String sanitized = sanitizeTitle(title);
        if (sanitized == null || sanitized.isBlank()) {
            return null;
        }
        return sanitized;
    }

    private static String sanitizeTitle(String text) {
        if (text == null) {
            return null;
        }

        String withoutUrls = URL_PATTERN.matcher(text).replaceAll("");
        String normalized = withoutUrls.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > 220 ? normalized.substring(0, 220).trim() : normalized;
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }

        String normalized = url.trim();
        while (normalized.endsWith(")") || normalized.endsWith("]") || normalized.endsWith(",") || normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static String safeAsString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final class FetchedNews {
        private final NewsItem newsItem;
        private final OffsetDateTime publishedAt;

        private FetchedNews(NewsItem newsItem, OffsetDateTime publishedAt) {
            this.newsItem = newsItem;
            this.publishedAt = publishedAt;
        }
    }

    private static final class LinkCandidate {
        private final String title;
        private final String uri;

        private LinkCandidate(String title, String uri) {
            this.title = title;
            this.uri = uri;
        }
    }
}