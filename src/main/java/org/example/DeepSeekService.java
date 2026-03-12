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
            System.err.println("HATA: DEEPSEEK_API_KEY tanimlanmamis!");
            return "[]";
        }

        System.out.println("DeepSeek API cagiriliyor...");
        try {
            String prompt =
                    "Asagidaki numarali basliklardan SADECE Turkiye yargi-hukuk-insan haklari kapsamindaki haberleri sec.\n" +
                    "\n" +
                    "DAHIL ET:\n" +
                    "- Adalet Bakanligi, HSK, Yargitay, Danistay, AYM, AIHM karar/atama/gorev degisikligi\n" +
                    "- Yargi bagimsizligi, insan haklari ihlali, iskence, keyfi tutuklama, cezaevi ihlali\n" +
                    "- Hakim/savci suclari, yolsuzluk iddialari, dikkat cekici yargi kararlari\n" +
                    "- FETO, Gulen Hareketi, Hizmet Hareketi baglaminda yargi-hukuk haberleri\n" +
                    "\n" +
                    "DISLA:\n" +
                    "- Yemek, magazin, astroloji, burc, yasam tuyolari, spor, ekonomi-genel, dis politika-genel\n" +
                    "- Mahkeme ilanlari/tebligat duyurulari (orn: 'T.C. ... MAHKEMESI HAKIMLIGI')\n" +
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

            System.out.println("DeepSeek API yanit kodu: " + response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("DeepSeek API hatasi: " + response.statusCode());
                System.err.println("Yanit: " + response.body());
                return "[]";
            }

            JSONObject resJson = new JSONObject(response.body());
            JSONArray choices = resJson.optJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                System.err.println("DeepSeek yanitinda 'choices' bulunamadi!");
                System.err.println("Tam yanit: " + response.body());
                return "[]";
            }

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                System.err.println("DeepSeek yanitinda 'message' bulunamadi!");
                return "[]";
            }

            String content = message.optString("content", "[]").trim();
            System.out.println("DeepSeek filtreleme sonucu alindi. Uzunluk: " + content.length());
            return normalizeToJsonIndexArray(content);

        } catch (Exception e) {
            System.err.println("DeepSeek API cagrisi sirasinda hata: " + e.getMessage());
            e.printStackTrace();
            return "[]";
        }
    }

    private String normalizeToJsonIndexArray(String content) {
        if (content == null || content.isBlank()) {
            return "[]";
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed = trimmed.replace("```json", "").replace("```", "").trim();
        }

        // Ilk JSON dizi bolumunu ayikla.
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
                if (arr.get(i) instanceof Number) {
                    onlyInts.put(arr.getInt(i));
                }
            }
            return onlyInts.toString();
        } catch (Exception e) {
            return "[]";
        }
    }
}

