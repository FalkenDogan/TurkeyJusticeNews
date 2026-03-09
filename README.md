# TurkeyJusticeNews

RSS haberlerini toplayan, Gemini ile filtreleyen ve sonucu Telegram'a gonderen Java uygulamasi.

## Zamanlama

- Haber secimi `Europe/Berlin` saat dilimine gore yapilir.
- Islenen tarih: calisma gununden bir onceki gun.
- Aralik: hedef gunun `00:00-23:59` zamani.
- Gonderim: GitHub Actions workflow'u Berlin saati `06:00` icin ayarlidir.

## Workflow

Dosya: `.github/workflows/daily-news.yml`

- `04:00 UTC` ve `05:00 UTC` tetiklenir.
- Workflow icindeki gate adimi sadece Berlin saati `06` oldugunda calismaya devam eder.

## Secrets

- `GEMINI_API_KEY`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHAT_ID`

## Lokal Calistirma

```powershell
mvn clean compile
mvn exec:java "-Dexec.mainClass=org.example.Main"
```

## Lisans

Bu proje `MIT License` ile lisanslanmistir. Ayrintilar icin `LICENSE` dosyasina bakin.
