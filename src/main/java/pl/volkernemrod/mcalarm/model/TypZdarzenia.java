package pl.volkernemrod.mcalarm.model;

/**
 * Typy zdarzeń rejestrowanych przez system (V1 + rezerwacja miejsca pod
 * przyszłe kategorie). README rozdz. 9.
 */
public enum TypZdarzenia {
    // Centrala
    CENTRAL_CREATED,
    CENTRAL_REMOVED_LEGALNIE,
    CENTRAL_DESTROYED_FIZYCZNIE,

    // Ruch
    PLAYER_ENTER_ZONE,
    PLAYER_EXIT_ZONE,

    // Bloki
    BLOCK_BREAK,
    BLOCK_PLACE,

    // Interakcje
    DOOR_USE,
    TRAPDOOR_USE,
    BUTTON_USE,
    LEVER_USE,

    // Kontenery
    CONTAINER_OPEN,
    CONTAINER_ITEM_ADD,
    CONTAINER_ITEM_REMOVE,

    // Inne (V1.1+, zarezerwowane)
    FIRE_IGNITE,
    EXPLOSION,
    CROP_TRAMPLE
}
