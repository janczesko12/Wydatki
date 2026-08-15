# Wydatki V4

Aplikacja Android do zarządzania wydatkami na jednym telefonie, z lokalnym zapisem i synchronizacją w chmurze.

## Zrealizowane punkty
- 2. wygodny ekran dodawania/edycji wydatku
- 3. historia wydatków w kategorii
- 4. statystyki miesięczne
- 5. historia/przełączanie miesięcy
- 8. globalne wyszukiwanie
- 9. GitHub Releases: automatyczne sprawdzenie przy starcie + ręczne sprawdzenie w Ustawieniach
- 10. eksport/import JSON

## Dane i chmura
- dane są zapisywane lokalnie na telefonie
- po zalogowaniu do Firebase dane są synchronizowane z Cloud Firestore
- Firebase Authentication używa e-mail + hasło
- dzięki kontu dane można odzyskać po odinstalowaniu i ponownej instalacji
- nie ma synchronizacji między telefonami jako osobnej funkcji; konto służy do kopii chmurowej

## Konfiguracja Firebase
Prawdziwe dane konkretnego projektu Firebase nie mogą być wygenerowane automatycznie. Jednorazowo ustaw wartości w `app/build.gradle.kts`:
- FIREBASE_API_KEY
- FIREBASE_APP_ID
- FIREBASE_PROJECT_ID

Następnie w Firebase:
1. Authentication -> Email/Password -> włącz.
2. Firestore Database -> utwórz bazę.
3. Wklej reguły z `firestore.rules`.

Szczegółowe kroki są w `FIREBASE_SETUP.txt`.

## GitHub updater
Ustaw w `app/build.gradle.kts`:
- GITHUB_OWNER
- GITHUB_REPO

GitHub Release musi zawierać APK, np. `Wydatki-v1.1.0.apk`.
Aplikacja porównuje numer wersji i pobiera nowszy APK.

## Uruchomienie
1. Otwórz folder projektu `Wydatki` w Android Studio.
2. Użyj Gradle 9.5.0 (plik `gradle/wrapper/gradle-wrapper.properties`).
3. Poczekaj na synchronizację.
4. Wybierz `app`.
5. Run.

Jeśli Android Studio nie ma lokalnie Gradle 9.5.0, pozwól IDE pobrać dystrybucję Gradle.
