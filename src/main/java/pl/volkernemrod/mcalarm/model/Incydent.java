package pl.volkernemrod.mcalarm.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Incydent — grupa logicznie powiązanych zdarzeń (zwykle: jedna "wizyta"
 * obcego gracza w uzbrojonej strefie). GAMEPLAY_SPEC rozdz. 30-32.
 *
 * Sama lista zdarzeń incydentu NIE jest tu trzymana w pamięci — zdarzenia
 * odwołują się do incydentu przez incydentId (patrz Zdarzenie) i są
 * pobierane ze storage na żądanie (historia/GUI), żeby uniknąć trzymania
 * dużych obiektów w RAM.
 */
public class Incydent {

    private final UUID id;
    private final UUID centralaId;
    private final UUID strefaId;
    private final UUID gracz; // może być null, jeśli sprawca nieznany
    private final Instant start;
    private Instant koniec; // null dopóki OTWARTY
    private StatusIncydentu status;

    public Incydent(UUID id, UUID centralaId, UUID strefaId, UUID gracz, Instant start) {
        this.id = id;
        this.centralaId = centralaId;
        this.strefaId = strefaId;
        this.gracz = gracz;
        this.start = start;
        this.status = StatusIncydentu.OTWARTY;
    }

    public void zamknij(Instant koniec) {
        this.koniec = koniec;
        this.status = StatusIncydentu.ZAMKNIETY;
    }

    public UUID getId() { return id; }
    public UUID getCentralaId() { return centralaId; }
    public UUID getStrefaId() { return strefaId; }
    public UUID getGracz() { return gracz; }
    public Instant getStart() { return start; }
    public Instant getKoniec() { return koniec; }
    public StatusIncydentu getStatus() { return status; }
}
