package fr.tuto.oneblock.models;

import java.util.List;

/**
 * Représente une question de trivia (calcul mental ou capitales/pays)
 * actuellement posée dans le chat, en attente d'une réponse.
 */
public class TriviaQuestion {

    /** Difficulté : influence le gain de base. */
    public enum Difficulty {
        FACILE, MOYEN, DIFFICILE
    }

    private final String displayText;
    private final List<String> acceptedAnswers; // déjà normalisées (minuscules, sans accents)
    private final Difficulty difficulty;
    private final double minReward;
    private final double maxReward;
    private final long askedAtMillis;
    /** Clé technique (non affichée) utilisée uniquement pour l'anti-répétition, ex: "capitale:France". */
    private final String signature;

    public TriviaQuestion(String displayText, List<String> acceptedAnswers, Difficulty difficulty,
                           double minReward, double maxReward, String signature) {
        this.displayText = displayText;
        this.acceptedAnswers = acceptedAnswers;
        this.difficulty = difficulty;
        this.minReward = minReward;
        this.maxReward = maxReward;
        this.askedAtMillis = System.currentTimeMillis();
        this.signature = signature;
    }

    /** Compatibilité : construit une question sans signature dédiée (utilise le texte affiché). */
    public TriviaQuestion(String displayText, List<String> acceptedAnswers, Difficulty difficulty,
                           double minReward, double maxReward) {
        this(displayText, acceptedAnswers, difficulty, minReward, maxReward, displayText);
    }

    public String getSignature() {
        return signature;
    }

    public String getDisplayText() {
        return displayText;
    }

    public List<String> getAcceptedAnswers() {
        return acceptedAnswers;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public double getMinReward() {
        return minReward;
    }

    public double getMaxReward() {
        return maxReward;
    }

    public long getAskedAtMillis() {
        return askedAtMillis;
    }

    public boolean matches(String normalizedGuess) {
        return acceptedAnswers.contains(normalizedGuess);
    }
}
