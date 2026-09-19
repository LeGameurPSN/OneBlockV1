package fr.tuto.oneblock.models;

/**
 * Statistiques trivia d'un joueur : nombre de bonnes/mauvaises réponses,
 * série (streak) actuelle et meilleure série jamais atteinte.
 * Persisté dans trivia_stats.yml par {@link fr.tuto.oneblock.managers.TriviaManager}.
 */
public class PlayerTriviaStats {

    private String lastKnownName;
    private int correctAnswers;
    private int wrongAnswers;
    private int currentStreak;
    private int bestStreak;

    public PlayerTriviaStats(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public PlayerTriviaStats(String lastKnownName, int correctAnswers, int wrongAnswers,
                              int currentStreak, int bestStreak) {
        this.lastKnownName = lastKnownName;
        this.correctAnswers = correctAnswers;
        this.wrongAnswers = wrongAnswers;
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
    }

    /** Appelée lors d'une bonne réponse : incrémente le compteur et la série. */
    public void registerCorrect() {
        correctAnswers++;
        currentStreak++;
        if (currentStreak > bestStreak) {
            bestStreak = currentStreak;
        }
    }

    /** Appelée lors d'une mauvaise réponse : incrémente le compteur et casse la série en cours. */
    public void registerWrong() {
        wrongAnswers++;
        currentStreak = 0;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public int getWrongAnswers() {
        return wrongAnswers;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getBestStreak() {
        return bestStreak;
    }
}
