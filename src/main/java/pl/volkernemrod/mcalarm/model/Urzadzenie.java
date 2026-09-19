package pl.volkernemrod.mcalarm.model;

/**
 * Wspólny interfejs dla urządzeń podpiętych pod centralę (syrena, lampa,
 * w przyszłości: czujnik, drzwi, kamera...). GAMEPLAY_SPEC rozdz. 77,
 * README rozdz. 23 — zasada modułowości: rdzeń zna tylko ten interfejs,
 * nowe urządzenia nie wymagają zmiany logiki centrali.
 */
public interface Urzadzenie {

    /** Unikalna nazwa/etykieta urządzenia w ramach centrali. */
    String getNazwa();

    /** Aktywacja urządzenia (np. wywołana przez alarm lub ręcznie/test). */
    void aktywuj();

    /** Dezaktywacja urządzenia (koniec alarmu / ręczne wyłączenie). */
    void dezaktywuj();

    /** Czy urządzenie jest obecnie aktywne. */
    boolean isAktywne();
}
