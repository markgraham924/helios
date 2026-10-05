# SPEC 0.21 - karta Odczyty (`entities`)

Uzgodniony zakres: jedna karta z 1–3 encjami wybieranymi w edytorze Heliosa
w Home Assistant. Każdy wiersz pokazuje nazwę oraz wartość z jednostką. Kolejność
odczytów i własne nazwy wynikają z konfiguracji. Karta służy do podglądu,
także dla encji `number` i `input_number`.

## Konfiguracja

Nowy typ wymaga `version: 6`, Heliosa 0.19.0 i edytora ha-helios 0.17.0.
W starszej aplikacji nieznany typ odrzuci dokument i pozostanie ostatni
poprawny układ. Aktualizacja APK jest potrzebna przed zapisaniem nowej karty.

```yaml
- id: pomiary
  type: entities
  column: 1
  row: 3
  width: 1
  height: 1
  title: Pomiary
  entries:
    - { entity: sensor.salon_temperature, title: Temp. }
    - { entity: sensor.salon_humidity, title: Wilgotność }
    - { entity: sensor.house_power, title: Pobór }
```

Pola karty: wspólne `id`, pozycja, rozmiar, opcjonalne `title`, `icon`,
`visible_when`, oraz wymagane `entries`. Domyślny tytuł to `Odczyty`,
domyślna ikona `mdi:format-list-bulleted`.

`entries` zawiera od 1 do 3 obiektów w kolejności wyświetlania. Każdy wymaga
poprawnego identyfikatora `entity` z dowolnej domeny HA. Opcjonalne `title`
jest niepustym tekstem do 40 znaków. Bez niego nazwa pochodzi z
`friendly_name`, a przy jego braku z identyfikatora encji z odstępami zamiast
podkreśleń. Karta nie przyjmuje akcji, potwierdzeń ani ręcznych jednostek.
Nie jest kartą zastępczą `alerts.empty`.

## Wyświetlanie

Trzy wiersze mieszczą się w 1×1 (190×132 jednostki ekranu 800×480).
Makieta typografii: [entities-card.png](mockups/entities-card.png), generowana
przez [entities_mock.py](mockups/entities_mock.py) z czcionek aplikacji.
Nagłówek z ikoną ma 22 jednostki wysokości, odstęp przed listą 6, marginesy 12.
Pozostała wysokość dzieli się równo między odczyty, do 36 na wiersz.
Nazwy są po lewej, wartości po prawej. Wartość ma pierwszeństwo szerokości
do 60% wnętrza karty; długie teksty są ucinane wielokropkiem. Większa karta
zachowuje listę wierszy.

Liczby mają do jednego miejsca po przecinku, przecinek dziesiętny i jednostkę
`unit_of_measurement` z HA. Liczby bez jednostki mają ten sam format.
Pozostałe stany korzystają z tłumaczeń kafelka `tile`.

Brak encji, `unknown` i `unavailable` dają `—` tylko w danym wierszu;
pozostałe odczyty pozostają widoczne. Po utracie połączenia zegar zachowuje
ostatnie wartości, wycisza ich kolor i dopisuje `(offline)` do tytułu.
Opis dostępności zawiera pełne nazwy i wartości oraz informację o braku
lub nieaktualności danych.

## Przepływ danych i sprawdzenie

Wszystkie encje listy trafiają do istniejącej subskrypcji stanów HA,
z atrybutami nazwy, ikony, jednostki i klasy urządzenia. Nie powstaje nowe
połączenie ani wywołanie usługi. Zmiana stanu aktualizuje właściwy wiersz.

Walidacja Java i edytora HA obejmuje limit listy, identyfikatory encji,
typy i nieznane pola. Testy obejmują także kolejność, round-trip formularza,
subskrypcję wielu encji, jednostki, brak danych i zachowanie offline.
