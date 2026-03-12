package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.*;

public class BlueskyYesterdayFeedFetcher {

    private static final String BASE_URL = "https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed";
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // Bugünün tarihi (sorunun bağlamına göre sabit)
    private static final OffsetDateTime TODAY = OffsetDateTime.of(2026, 3, 12, 0, 0, 0, 0, ZoneOffset.UTC);

    public static void main(String[] args) {
        // Birden fazla actor (handle) buraya yazılır
        String[] actors = {

                //"boldmedya.bsky.social",
                //"ankahaber.net",
                //"t24.com.tr",
                //"bianet.org"// örnek
                //"hurriyet.bsky.social",     // örnek
                "sozcugazete.bsky.social"    // örnek
        };

        // Otomatik: Dün = bugün - 1 gün
        OffsetDateTime yesterday = TODAY.minusDays(0);
        OffsetDateTime start = yesterday.withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime end   = yesterday.withHour(23).withMinute(59).withSecond(59).withNano(999_999_000);

        String startStr = start.format(DateTimeFormatter.ISO_INSTANT);  // ...Z formatında
        String endStr   = end.format(DateTimeFormatter.ISO_INSTANT);

        System.out.println("Otomatik tarih aralığı (dün):");
        System.out.println("Başlangıç: " + startStr);
        System.out.println("Bitiş:     " + endStr);
        System.out.println("----------------------------------------");

        List<JsonObject> allSelectedPosts = new ArrayList<>();

        for (String actor : actors) {
            System.out.println("İşleniyor: " + actor);
            List<JsonObject> posts = fetchAndFilterPosts(actor, startStr, endStr);
            allSelectedPosts.addAll(posts);
        }

        // Sonuçları JSON olarak formatla ve yazdır
        JsonObject result = new JsonObject();
        JsonArray selectedArray = new JsonArray();
        allSelectedPosts.forEach(selectedArray::add);
        result.add("selected_posts", selectedArray);
        result.addProperty("query_date_range", startStr + " → " + endStr);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("\nSonuç (tüm hesaplardan toplanan):");
        System.out.println(gson.toJson(result));
    }

    private static List<JsonObject> fetchAndFilterPosts(String actor, String startStr, String endStr) {
        List<JsonObject> selected = new ArrayList<>();

        try {
            String url = BASE_URL + "?actor=" + actor + "&limit=100&filter=posts_no_replies";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Hata - " + actor + " → Status: " + response.statusCode());
                return selected;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray feed = json.getAsJsonArray("feed");

            if (feed == null) return selected;

            OffsetDateTime start = OffsetDateTime.parse(startStr);
            OffsetDateTime end   = OffsetDateTime.parse(endStr);

            for (JsonElement element : feed) {
                JsonObject postObj = element.getAsJsonObject().getAsJsonObject("post");
                if (postObj == null) continue;

                String indexedAtStr = postObj.get("indexedAt").getAsString();
                OffsetDateTime indexedAt = OffsetDateTime.parse(indexedAtStr);

                // Tarih aralığı kontrolü (inclusive)
                if (!indexedAt.isBefore(start) && !indexedAt.isAfter(end)) {
                    JsonObject embed = postObj.getAsJsonObject("embed");
                    if (embed == null) continue;

                    JsonObject external = embed.getAsJsonObject("external");
                    if (external != null && external.has("title") && external.has("uri")) {
                        JsonObject item = new JsonObject();
                        item.addProperty("title", external.get("title").getAsString());
                        item.addProperty("uri",   external.get("uri").getAsString());
                        item.addProperty("actor", actor);
                        item.addProperty("indexedAt", indexedAtStr);
                        selected.add(item);
                    }
                }
            }

        } catch (IOException | InterruptedException | JsonSyntaxException e) {
            System.err.println("Hata (" + actor + "): " + e.getMessage());
        }

        return selected;
    }
}