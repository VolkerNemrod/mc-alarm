package pl.volkernemrod.mcalarm.model;

/**
 * Tryb pracy centrali alarmowej.
 * GAMEPLAY_SPEC rozdz. 10-11.
 */
public enum TrybPracy {
    ROZBROJONY,
    MONITORING,
    CZUWANIE,
    ALARM;

    /**
     * Czy w tym trybie centrala jest "uzbrojona" w rozumieniu mechaniki
     * fizycznego niszczenia bloku centrali (pulsowanie + eksplozja).
     * Decyzja Volkera 2026-09-12 (mem-palace, wing mc-alarm, room decisions):
     * wybuch następuje tylko gdy centrala jest w CZUWANIE lub ALARM.
     */
    public boolean isUzbrojona() {
        return this == CZUWANIE || this == ALARM;
    }
}
