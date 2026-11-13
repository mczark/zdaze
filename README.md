# Zdążę! – aplikacja, która pilnuje, żebyś się nie spóźnił

Trzy tryby:

1. **⏱️ Pomiary** – dwa rodzaje zadań, każdy z osobnym formularzem:
   - **Czynność** – duży „+” → mówisz nazwę → gotowe. Okrągły przycisk START/STOP mierzy czas.
   - **🚗 Dojazd** – skąd, dowolna liczba **przystanków** (z czasem postoju), dokąd, środek transportu, dzień i godzina wyjazdu. Czas z Google Maps liczony jest odcinkami (każdy odcinek startuje po przyjeździe + postoju), jako średnia z 3 najbliższych takich dni.
   - Głosem (🎤 w nagłówku): „nowe zadanie prysznic”, „nowy dojazd”, „start prysznic”, „stop”.
2. **🗓️ Plan**: układasz zadania w kolejności i ustawiasz godzinę końca. Aplikacja liczy wstecz godziny startu i ustawia budzik.
3. **🛡️ Pilnowanie**: startuje razem z budzikiem i odlicza każde zadanie. W połowie czasu mówi „połowa czasu”, 30 s przed końcem są 3 krótkie piknięcia, na końcu długi głośny sygnał i komunikat głosowy z nazwą następnego zadania. Na koniec pyta „Czy zdążyłeś?” (przyciskiem albo głosem), a odpowiedź trafia do historii na ekranie startowym.

## Budowanie (GitHub Actions)

1. Utwórz repozytorium na GitHubie i wypchnij do niego ten katalog.
2. Każdy push na `main` uruchamia workflow **Build Android APK**:
   - APK trafia do **Releases** (np. `v1.0.5`) oraz do artefaktów przebiegu,
   - wersja webowa (PWA): włącz *Settings → Pages → Source: GitHub Actions* i uruchom ręcznie workflow „Deploy web”.
3. Na telefonie otwórz stronę Releases, pobierz `zdaze-N.apk` i zainstaluj (zezwól na instalację z nieznanych źródeł).

Kolejne wersje instalują się jako aktualizacje, bo APK jest zawsze podpisany tym samym kluczem (`android/app/zdaze.keystore`).
Ten klucz jest publiczny, więc wystarcza do użytku prywatnego. Własny klucz ustawisz w sekretach repozytorium:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Uprawnienia przy pierwszym uruchomieniu (Android)

- **Mikrofon**: do nazywania zadań i komend głosowych.
- **Powiadomienia**: potrzebne do budzika.
- **Powiadomienia pełnoekranowe** (Android 14+): aplikacja otworzy ustawienia przy pierwszym ustawieniu budzika. Włącz je, żeby budzik pokazywał aplikację na zablokowanym ekranie.
- Rozpoznawanie mowy korzysta z usługi Google na telefonie. Dla trybu offline pobierz pakiet języka polskiego w ustawieniach rozpoznawania mowy.

## Google Maps

W ⚙️ Ustawieniach wklej klucz API z Google Cloud z włączonym **Routes API**. Bez klucza przycisk „Otwórz Maps” pokaże trasę, a czas wpiszesz ręcznie.

## Struktura

```
web/                 aplikacja (HTML/JS), używana też jako PWA
android/             natywna powłoka: WebView + mowa, TTS, AlarmManager
.github/workflows/   budowanie APK + GitHub Pages
```
