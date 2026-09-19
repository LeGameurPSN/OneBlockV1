package fr.tuto.oneblock.world;

import org.bukkit.generator.ChunkGenerator;

/**
 * Générateur de monde totalement vide, utilisé pour le monde dédié du
 * OneBlock (voir OneBlockManager#initWorld). Aucun terrain, aucune caverne,
 * aucune structure, aucun mob : le monde est un vide complet et chaque île
 * démarre uniquement avec le bedrock + le bloc de départ posés par le
 * plugin, sans dépendre d'un nettoyage a posteriori de chunks déjà générés.
 */
public class VoidGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
