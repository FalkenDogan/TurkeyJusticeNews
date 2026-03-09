package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DeepSeekService {

    private final String apiKey = System.getenv("DEEPSEEK_API_KEY");

    public String filterNews(String allNewsText) {
        if (apiKey == null || apiKey.isBlank()) {
            return "[]";
        }

        try {
            String prompt = "Asagidaki haberleri analiz et. SADECE yargi bagimsizligi, insan haklari ihlalleri, keyfi tutuklama, " +
                    "yolsuzluk ve hukuksuzluklarla, hakim ve savcilarin isledikleri suclarla, ozel hayatlari ile, " +
                    "Yargitay, Danistay, Anayasa Mahkemesi ve AIHM'nin onemli kararlari ilgili olanlari, " +
                    "FETO, Gulen Hareketi ve Hizmet Hareketi temali haberlerle ilgili olanlari sec.\n\n" +
                    "ONEMLI: Cevabini MUTLAKA [Sayi]- formatinda ver. Ornek:\n" +
                    "0- Mahkeme karari iptal edildi\n" +
                    "2- Hukuksuz tutuklama raporu\n\n" +
                    "Eger kriterlere uygun HABER BULAMAZSAN, bos bir dizi dondur: []\n\n" +
                    "HABERLER:\n" + allNewsText;

            JSONObject jsonBody = new JSONObject()
                    .put("model", "deepseek-chat")
                    .put("messages", new JSONArray()
                            .put(new JSONObject().put("role", "system").put("content", "You are a strict news filter."))
                            .put(new JSONObject().put("role", "user").put("content", prompt)))
                    .put("temperature", 0.1);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "[]";
            }

            JSONObject resJson = new JSONObject(response.body());
            JSONArray choices = resJson.optJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "[]";
            }

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                return "[]";
            }

            String content = message.optString("content", "[]").trim();
            return content.isEmpty() ? "[]" : content;

        } catch (Exception e) {
            return "[]";
        }
    }
}

