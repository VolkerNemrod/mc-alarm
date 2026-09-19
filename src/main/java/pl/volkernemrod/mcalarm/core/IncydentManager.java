package pl.volkernemrod.mcalarm.core;

import pl.volkernemrod.mcalarm.VolkerNemrodAlarmPlugin;
import pl.volkernemrod.mcalarm.model.Incydent;
import pl.volkernemrod.mcalarm.storage.IncydentRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Śledzi OTWARTE incydenty w pamięci (gracz+centrala -> id incydentu) i
 * synchronizuje je z bazą. Plan działania — Etap 6.
 *
 * Incydent = "wizyta" obcego gracza w uzbrojonej strefie: start przy wejściu,
 * koniec przy wyjściu (GAMEPLAY_SPEC rozdz. 30-32). Zdarzenia zarejestrowane
 * w trakcie otwartego incydentu (dla tego samego gracza+centrali) są do
 * niego automatycznie dołączane — patrz StrefaZdarzeniaListener#zapiszZdarzenie.
 *
 * V1: koniec incydentu wyzwala TYLKO opuszczenie strefy. Koniec alarmu/
 * timeout jako alternatywne wyzwalacze zamknięcia (wspomniane w planie) —
 * jeszcze nie zaimplementowane, TODO na przyszłość.
 */
public class IncydentManager {

    private final VolkerNemrodAlarmPlugin plugin;
    private final IncydentRepository incydentRepository;

    /** klucz "gracz|centralaId" -> id otwartego incydentu. */
    private final Map<String, UUID> otwarte = new HashMap<>();

    public IncydentManager(VolkerNemrodAlarmPlugin plugin, IncydentRepository incydentRepository) {
        this.plugin = plugin;
        this.incydentRepository = incydentRepository;
    }

    private static String klucz(UUID gracz, UUID centralaId) {
        return gracz + "|" + centralaId;
    }

    /** Otwiera nowy incydent (jeśli nie ma już otwartego dla tego gracza+centrali) i zwraca jego id. */
    public UUID otworzLubPobierz(UUID gracz, UUID centralaId, UUID strefaId) {
        String k = klucz(gracz, centralaId);
        UUID istniejacy = otwarte.get(k);
        if (istniejacy != null) {
            return istniejacy;
        }
        UUID nowyId = UUID.randomUUID();
        Incydent incydent = new Incydent(nowyId, centralaId, strefaId, gracz, Instant.now());
        try {
            incydentRepository.zapisz(incydent);
            otwarte.put(k, nowyId);
            return nowyId;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd tworzenia incydentu.", e);
            return null;
        }
    }

    /** Zwraca id otwartego incydentu (bez tworzenia nowego) — do dołączania kolejnych zdarzeń. */
    public UUID pobierzOtwarty(UUID gracz, UUID centralaId) {
        return otwarte.get(klucz(gracz, centralaId));
    }

    /** Zamyka otwarty incydent gracza+centrali, jeśli istnieje. Bezpieczne wywołanie "na wszelki wypadek". */
    public void zamknij(UUID gracz, UUID centralaId) {
        String k = klucz(gracz, centralaId);
        UUID id = otwarte.remove(k);
        if (id == null) {
            return;
        }
        try {
            incydentRepository.zamknij(id, Instant.now());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zamykania incydentu.", e);
        }
    }
}
