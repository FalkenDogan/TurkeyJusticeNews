# TurkeyJusticeNews

RSS haberlerini toplayan, DeepSeek ile filtreleyen ve sonucu Telegram'a gonderen Java uygulamasi.

## Zamanlama

- Haber secimi `Europe/Berlin` saat dilimine gore yapilir.
- Islenen tarih: calisma gununden bir onceki gun.
- Aralik: hedef gunun `00:00-23:59` zamani.
- Gonderim: GitHub Actions workflow'u Berlin saati `06:00` icin ayarlidir.

## Workflow

Dosya: `.github/workflows/daily-news.yml`

- `04:00 UTC` ve `05:00 UTC` tetiklenir.
- Workflow icindeki gate adimi sadece schedule tetiklemelerinde Berlin saati `06` oldugunda calismaya devam eder.
- `workflow_dispatch` ile manuel calistirmada build adimi direkt calisir.

## Secrets

- `DEEPSEEK_API_KEY`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHAT_ID` (opsiyonel, tek chat icin)
- `TELEGRAM_CHAT_IDS` (opsiyonel, coklu chat icin CSV; ornek: `12345,-10098765,67890`)

Not:
- Bot ile DM'de `/start` yazan kullanicilarin chat id'leri Telegram `getUpdates` ile otomatik kesfedilir.
- Bu sayede sadece tek bir chat id'ye degil, botu baslatan diger kullanicilara da gonderim yapilabilir.

## Lokal Calistirma

```powershell
mvn clean compile
mvn exec:java "-Dexec.mainClass=org.example.Main"
```

## Lisans

Bu proje `MIT License` ile lisanslanmistir. Ayrintilar icin `LICENSE` dosyasina bakin.
