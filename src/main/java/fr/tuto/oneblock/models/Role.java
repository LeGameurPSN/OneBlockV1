package fr.tuto.oneblock.models;

/**
 * Rôle d'un joueur vis-à-vis d'une île, utilisé par le système de
 * permissions par rôle (/ob perms). Le propriétaire n'a pas de rôle : il a
 * TOUJOURS toutes les permissions, sans exception (voir IslandData#hasPermission).
 *
 *  - VISITEUR : n'importe quel joueur présent sur l'île, sans invitation ni
 *               confiance particulière (le cas le plus restreint par défaut).
 *  - TRUST    : joueur de confiance (/ob trust) : garde sa propre île, mais
 *               obtient les permissions de ce rôle sur celle-ci.
 *  - MEMBRE   : coéquipier de l'île (/ob team) : partage la caisse et la
 *               progression, obtient les permissions de ce rôle.
 */
public enum Role {
    VISITEUR("&7Visiteur", "&7N'importe quel joueur présent sur l'île."),
    TRUST("&bConfiance", "&7Joueur de confiance (garde sa propre île)."),
    MEMBRE("&aMembre", "&7Coéquipier : partage caisse et progression.");

    private final String displayName;
    private final String description;

    Role(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
