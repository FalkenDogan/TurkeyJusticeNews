package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GeminiService {

    private final String apiKey = System.getenv("GEMINI_API_KEY"); // GitHub Secrets'tan gelecek


    public String filterNews(String allNewsText) {
        try {
            String prompt = "Aşağıdaki haberleri analiz et. SADECE yargı bağımsızlığı, insan hakları ihlalleri, keyfi tutuklama, " +
                    "yolsuzluk ve hukuksuzluklarla, hakim ve savcıların işledikleri suçlarla, özel hayatları ile," +
                    "Yargıtay, Danıştay, Anayasa Mahkemesi ve AİHM'nin önemli kararları ilgili olanları," +
                    "FETÖ, Gülen Hareketi ve Hizmet Hareketi temalı haberlerle ilgil olanları seç.\n\n" +
                    "ÖNEMLİ: Cevaplarını MUTLAKA [Sayı]- formatında ver. Örnek:\n" +
                    "0- Mahkeme kararı iptal edildi\n" +
                    "2- Hukuksuz tutuklama raporu\n\n" +
                    "Eğer kriterlere uygun HABER BULAMAZSAN, boş bir dizi döndür: []\n\n" +
                    "HABERLER:\n" + allNewsText;

            // Gemini 1.5 Flash API endpoint
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

            // JSON Gövdesi oluşturma
            JSONObject jsonBody = new JSONObject()
                    .put("contents", new JSONArray()
                            .put(new JSONObject()
                                    .put("parts", new JSONArray()
                                            .put(new JSONObject().put("text", prompt)))));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Yanıtı ayıklama
            JSONObject resJson = new JSONObject(response.body());
            return resJson.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

        } catch (Exception e) {
            return "[]";
        }
    }
}