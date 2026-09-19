package fr.tuto.oneblock.managers;

import fr.tuto.oneblock.OneBlockPlugin;
import fr.tuto.oneblock.models.PlayerTriviaStats;
import fr.tuto.oneblock.models.TriviaQuestion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Système de questions posées automatiquement dans le chat (calcul mental +
 * capitales/pays, dans les deux sens), avec une récompense en argent qui
 * dépend de la difficulté de la question ET de la rapidité de la réponse.
 * <p>
 * Une question est posée toutes les {@code trivia.interval-minutes} minutes
 * (config.yml). L'administrateur LeGameurPSN_YT (ou un op) peut en forcer une
 * en avance avec /sendquestion, voir
 * {@link fr.tuto.oneblock.commands.SendQuestionCommand}.
 */
public class TriviaManager {

    private final OneBlockPlugin plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final Random random = new Random();

    /** Le temps qu'ont les joueurs pour répondre avant qu'une question n'expire. */
    private static final int ANSWER_WINDOW_SECONDS = 90;

    private TriviaQuestion activeQuestion;
    private BukkitTask timerTask;
    private BukkitTask expiryTask;

    // ---- Données pays/capitales (FR) : { pays, capitale, difficulté } ----
    private static final String[][] COUNTRY_CAPITALS_EASY = {
            {"France", "Paris"}, {"Espagne", "Madrid"}, {"Italie", "Rome"}, {"Allemagne", "Berlin"},
            {"Royaume-Uni", "Londres"}, {"Portugal", "Lisbonne"}, {"Belgique", "Bruxelles"},
            {"Chine", "Pekin"}, {"Japon", "Tokyo"}, {"Etats-Unis", "Washington"}, {"Russie", "Moscou"},
            {"Egypte", "Le Caire"}, {"Grece", "Athenes"}, {"Canada", "Ottawa"}, {"Bresil", "Brasilia"},
    };
    private static final String[][] COUNTRY_CAPITALS_MEDIUM = {
            {"Suisse", "Berne"}, {"Pays-Bas", "Amsterdam"}, {"Autriche", "Vienne"}, {"Suede", "Stockholm"},
            {"Norvege", "Oslo"}, {"Danemark", "Copenhague"}, {"Pologne", "Varsovie"}, {"Irlande", "Dublin"},
            {"Turquie", "Ankara"}, {"Maroc", "Rabat"}, {"Mexique", "Mexico"}, {"Argentine", "Buenos Aires"},
            {"Inde", "New Delhi"}, {"Coree du Sud", "Seoul"}, {"Vietnam", "Hanoi"}, {"Thailande", "Bangkok"},
    };
    private static final String[][] COUNTRY_CAPITALS_HARD = {
            {"Finlande", "Helsinki"}, {"Ukraine", "Kiev"}, {"Nouvelle-Zelande", "Wellington"},
            {"Australie", "Canberra"}, {"Perou", "Lima"}, {"Chili", "Santiago"}, {"Colombie", "Bogota"},
            {"Algerie", "Alger"}, {"Tunisie", "Tunis"}, {"Nigeria", "Abuja"}, {"Kenya", "Nairobi"},
            {"Senegal", "Dakar"}, {"Islande", "Reykjavik"}, {"Slovaquie", "Bratislava"}, {"Kazakhstan", "Astana"},
    };

    // ---- Sciences / nature : { question, réponse, difficulté (F/M/D) } ----
    private static final String[][] SCIENCE_FACTS = {
            {"Combien y a-t-il de planetes dans le systeme solaire ?", "8", "F"},
            {"Quelle est la planete la plus proche du Soleil ?", "Mercure", "F"},
            {"Quelle est la plus grande planete du systeme solaire ?", "Jupiter", "F"},
            {"Quelle est la planete rouge, connue pour sa couleur ?", "Mars", "F"},
            {"Quelle planete est la plus proche de la Terre en partant du Soleil (avant elle) ?", "Venus", "M"},
            {"Quelle est la derniere planete du systeme solaire (la plus eloignee du Soleil) ?", "Neptune", "M"},
            {"Quelle planete est entouree de magnifiques anneaux bien visibles ?", "Saturne", "F"},
            {"Combien y a-t-il d'os dans le corps humain adulte ?", "206", "M"},
            {"Combien de dents possede un adulte en moyenne (dents de sagesse comprises) ?", "32", "F"},
            {"Combien de dents de lait possede un enfant ?", "20", "M"},
            {"Combien de chambres possede le coeur humain ?", "4", "F"},
            {"Quel est l'organe le plus grand du corps humain ?", "la peau", "M"},
            {"Quel est l'os le plus long du corps humain ?", "le femur", "D"},
            {"Combien de cotes possede le corps humain (en tout) ?", "24", "D"},
    };

    // ---- Chimie : { symbole, element } pour poser dans les deux sens ----
    private static final String[][] CHEMICAL_SYMBOLS = {
            {"Au", "Or"}, {"Fe", "Fer"}, {"O", "Oxygene"}, {"H", "Hydrogene"}, {"C", "Carbone"},
            {"N", "Azote"}, {"Na", "Sodium"}, {"K", "Potassium"}, {"Ag", "Argent"}, {"Cu", "Cuivre"},
            {"Cl", "Chlore"}, {"He", "Helium"}, {"Ca", "Calcium"}, {"Zn", "Zinc"}, {"Pb", "Plomb"},
    };

    // ---- Histoire : { question, reponse (annee), difficulté } ----
    private static final String[][] HISTORY_DATES = {
            {"En quelle annee a eu lieu la chute du mur de Berlin ?", "1989", "M"},
            {"En quelle annee a debute la Revolution francaise ?", "1789", "M"},
            {"En quelle annee Christophe Colomb a-t-il decouvert l'Amerique ?", "1492", "D"},
            {"En quelle annee l'Homme a-t-il marche sur la Lune pour la premiere fois ?", "1969", "F"},
            {"En quelle annee la Seconde Guerre mondiale s'est-elle terminee ?", "1945", "F"},
            {"En quelle annee la Seconde Guerre mondiale a-t-elle commence ?", "1939", "M"},
            {"En quelle annee la Premiere Guerre mondiale a-t-elle commence ?", "1914", "M"},
            {"En quelle annee les Etats-Unis ont-ils declare leur independance ?", "1776", "D"},
            {"En quelle annee a eu lieu la prise de la Bastille ?", "1789", "M"},
            {"En quelle annee l'Empire romain d'Occident s'est-il effondre ?", "476", "D"},
    };

    // ---- Minecraft : biomes/structures : { question, reponse, difficulté } ----
    private static final String[][] MC_BIOMES_STRUCTURES = {
            {"Dans quel biome trouve-t-on naturellement des Husks (zombies du desert) ?", "desert", "F"},
            {"Dans quelle structure peut-on affronter l'Ender Dragon ?", "end", "F"},
            {"Dans quel biome trouve-t-on des Strays (squelettes glaces) ?", "taiga glaciale", "M"},
            {"Dans quelle structure trouve-t-on generalement un Nether Fortress (et des Blazes) ?", "nether", "F"},
            {"Dans quelle structure peut-on trouver un forgeron avec des lingots gratuits (village) ?", "village", "F"},
            {"Dans quelle structure trouve-t-on des Piglins et des coffres a Or dans le Nether ?", "bastion", "M"},
            {"Quelle structure du desert cache souvent un piege a TNT sous une salle au tresor ?", "temple du desert", "M"},
            {"Quel avant-poste hostile est garde par un Illusioner ou des Pillagers avec une cage ?", "avant poste des pillards", "D"},
            {"Dans quel biome trouve-t-on des Polar Bears (ours polaires) ?", "toundra glacee", "M"},
            {"Dans quelle structure sous-marine trouve-t-on des Elder Guardians ?", "monument oceanique", "D"},
            {"Dans quel biome les Slimes apparaissent-ils le plus facilement en surface ?", "marecage", "M"},
            {"Quelle structure permet de trouver la Totem of Undying grace aux Pillagers d'elite ?", "avant poste des pillards", "D"},
    };

    // ---- Minecraft : potions/redstone : { question, reponse, difficulté } ----
    private static final String[][] MC_POTIONS_REDSTONE = {
            {"Quel ingredient de base sert a preparer une potion bizarre (eau + ...) ?", "verrue du nether", "F"},
            {"Quel ingredient donne la resistance au feu a une potion ?", "creme de magma", "M"},
            {"Quel ingredient rend une potion de vitesse ?", "sucre", "F"},
            {"Quel ingredient permet de faire une potion de force ?", "poudre de blaze", "M"},
            {"Quel ingredient permet de creer une potion de faiblesse en fiole (sans brasserie) ?", "oeil d araignee fermente", "D"},
            {"Quel bloc redstone retarde un signal de 1 a 4 ticks redstone ?", "repeteur", "F"},
            {"Quel composant redstone inverse un signal (allume devient eteint) ?", "torche redstone", "F"},
            {"Quel bloc redstone peut copier et retarder un signal sur plusieurs sorties (multi-directions) ?", "comparateur", "D"},
            {"Quel bloc actionne un mecanisme quand un joueur marche dessus, puis se relache ?", "plaque de pression", "F"},
            {"Quel objet redstone active un mecanisme une seule fois quand on l'actionne, puis revient tout seul ?", "bouton", "M"},
            {"Quel bloc explosif peut etre amorce par un signal redstone ?", "tnt", "F"},
            {"Quel item permet de deplacer des blocs grace a la redstone ?", "piston", "M"},
    };

    /** Historique récent des signatures de questions déjà posées, pour éviter les répétitions. */
    private final Deque<String> recentSignatures = new ArrayDeque<>();

    /** Statistiques trivia par joueur (streak, bonnes/mauvaises réponses), persistées dans trivia_stats.yml. */
    private final Map<UUID, PlayerTriviaStats> playerStats = new HashMap<>();
    private File statsFile;

    /** Joueurs ayant déjà "raté" la question active (pour ne compter qu'une mauvaise réponse par joueur/question). */
    private final Set<UUID> wrongAttemptsThisRound = new HashSet<>();

    public TriviaManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
        loadStats();
    }

    // ==================== CONFIG / CYCLE DE VIE ====================

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("trivia.enabled", true);
    }

    /** Démarre le minuteur (toutes les trivia.interval-minutes minutes). Appelé depuis onEnable(). */
    public void start() {
        stop();
        if (!isEnabled()) return;
        long intervalTicks = Math.max(1L, Math.round(
                plugin.getConfig().getDouble("trivia.interval-minutes", 9) * 60.0 * 20.0));
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::askQuestion, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        cancelExpiry();
    }

    private void cancelExpiry() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    // ==================== POSER UNE QUESTION ====================

    /**
     * Pose une nouvelle question dans le chat public. Si une question est
     * déjà en attente de réponse, ne fait rien (retourne false).
     */
    public boolean askQuestion() {
        synchronized (this) {
            if (activeQuestion != null) return false;
            if (Bukkit.getOnlinePlayers().isEmpty()) return false;
            activeQuestion = generateQuestionAvoidingRepeats();
            wrongAttemptsThisRound.clear();
        }

        broadcast("");
        broadcast("&6&l✦ &e&lQUESTION BONUS &6&l✦");
        broadcast("&f" + activeQuestion.getDisplayText());
        broadcast(difficultyLabel(activeQuestion.getDifficulty())
                + " &7- Réponds dans le chat, le/la plus rapide gagne entre &a"
                + moneyRound(activeQuestion.getMinReward()) + "$&7 et &a"
                + moneyRound(activeQuestion.getMaxReward()) + "$&7 !");
        broadcast("");

        cancelExpiry();
        expiryTask = Bukkit.getScheduler().runTaskLater(plugin, this::expireQuestion,
                ANSWER_WINDOW_SECONDS * 20L);
        return true;
    }

    private void expireQuestion() {
        TriviaQuestion expired;
        synchronized (this) {
            if (activeQuestion == null) return;
            expired = activeQuestion;
            activeQuestion = null;
        }
        String answer = expired.getAcceptedAnswers().isEmpty() ? "?" : expired.getAcceptedAnswers().get(0);
        broadcast("&cPersonne n'a trouvé à temps ! La réponse était : &f" + prettify(answer));
    }

    // ==================== RÉPONSE D'UN JOUEUR ====================

    /**
     * Appelée par {@link fr.tuto.oneblock.listeners.TriviaChatListener} pour
     * chaque message de chat. Retourne true si ce message était la bonne
     * réponse à la question active (auquel cas la récompense a déjà été
     * versée et annoncée).
     */
    public boolean tryAnswer(Player player, String rawMessage) {
        if (activeQuestion == null) return false;

        String normalized = normalize(rawMessage);

        // AsyncChatEvent s'exécute HORS du thread principal : on ne touche à
        // aucune API Bukkit ici, on se contente de vérifier/consommer l'état
        // partagé (synchronized pour éviter un double-paiement si deux
        // joueurs répondent correctement au même instant).
        TriviaQuestion answeredQuestion;
        synchronized (this) {
            if (activeQuestion == null) return false;
            if (!activeQuestion.matches(normalized)) {
                registerWrongAttempt(player);
                return false;
            }
            answeredQuestion = activeQuestion;
            activeQuestion = null;
        }

        double elapsedSeconds = (System.currentTimeMillis() - answeredQuestion.getAskedAtMillis()) / 1000.0;

        PlayerTriviaStats stats = getOrCreateStats(player.getUniqueId(), player.getName());
        int streakBeforeBonus;
        synchronized (this) {
            stats.registerCorrect();
            streakBeforeBonus = stats.getCurrentStreak();
        }
        saveStatsAsync();

        double reward = computeReward(answeredQuestion, elapsedSeconds, streakBeforeBonus);

        // Tout ce qui suit touche l'API Bukkit (commande, scheduler, envoi de
        // messages) : ça doit obligatoirement repasser sur le thread principal.
        Bukkit.getScheduler().runTask(plugin, () -> {
            cancelExpiry();

            var vault = plugin.getVaultManager();
            boolean paid = vault.isEnabled();
            if (paid) {
                long rewardRounded = Math.round(reward);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "eco give " + player.getName() + " " + rewardRounded);
            }

            String streakSuffix = streakBeforeBonus >= 3
                    ? " &6(série &f" + streakBeforeBonus + "&6 !)"
                    : "";

            if (paid) {
                broadcast("&a✔ &f" + player.getName() + " &aa trouvé la bonne réponse en &f"
                        + String.format(Locale.FRANCE, "%.1f", elapsedSeconds) + "s &aet remporte &f"
                        + moneyRound(reward) + "$ &a!" + streakSuffix);
            } else {
                broadcast("&a✔ &f" + player.getName() + " &aa trouvé la bonne réponse ! &7(aucune économie détectée, pas de gain versé)" + streakSuffix);
            }
        });
        return true;
    }

    /** Enregistre une mauvaise réponse pour ce joueur (au plus une fois par question active). */
    private void registerWrongAttempt(Player player) {
        if (!plugin.getConfig().getBoolean("trivia.track-wrong-answers", true)) return;
        boolean firstAttempt;
        synchronized (this) {
            firstAttempt = wrongAttemptsThisRound.add(player.getUniqueId());
        }
        if (!firstAttempt) return;
        PlayerTriviaStats stats = getOrCreateStats(player.getUniqueId(), player.getName());
        synchronized (this) {
            stats.registerWrong();
        }
        saveStatsAsync();
    }

    /**
     * Calcule le gain final : base aléatoire selon la difficulté, pondéré par la rapidité,
     * puis majoré d'un bonus progressif selon la série de bonnes réponses en cours, borné [min ; max].
     */
    private double computeReward(TriviaQuestion question, double elapsedSeconds, int currentStreak) {
        double base = question.getMinReward()
                + random.nextDouble() * (question.getMaxReward() - question.getMinReward());

        double clampedElapsed = Math.max(0, Math.min(ANSWER_WINDOW_SECONDS, elapsedSeconds));
        // Facteur de rapidité : 1.0 si répondu immédiatement, ~0.4 si répondu juste avant l'expiration.
        double speedFactor = 1.0 - (clampedElapsed / ANSWER_WINDOW_SECONDS) * 0.6;

        // Bonus de série : +X% par bonne réponse d'affilée (streak - 1), plafonné.
        double bonusPerStreak = plugin.getConfig().getDouble("trivia.streak-bonus-percent", 5) / 100.0;
        double maxBonus = plugin.getConfig().getDouble("trivia.streak-bonus-max-percent", 50) / 100.0;
        double streakFactor = 1.0 + Math.min(maxBonus, Math.max(0, currentStreak - 1) * bonusPerStreak);

        double reward = base * speedFactor * streakFactor;
        double min = plugin.getConfig().getDouble("trivia.min-reward", 1);
        double max = plugin.getConfig().getDouble("trivia.max-reward", 5000);
        return Math.max(min, Math.min(max, Math.round(reward)));
    }

    // ==================== GÉNÉRATION DES QUESTIONS ====================

    /**
     * Génère une question en évitant de répéter une des {@code trivia.anti-repeat-count}
     * dernières questions posées (identifiées par leur signature). Abandonne l'anti-répétition
     * au bout de quelques essais pour ne jamais bloquer (au cas où un pool serait trop petit).
     */
    private TriviaQuestion generateQuestionAvoidingRepeats() {
        int maxAttempts = 20;
        TriviaQuestion candidate = null;
        for (int i = 0; i < maxAttempts; i++) {
            candidate = generateQuestion();
            if (!recentSignatures.contains(candidate.getSignature())) break;
        }
        rememberSignature(candidate.getSignature());
        return candidate;
    }

    private void rememberSignature(String signature) {
        int historySize = Math.max(1, plugin.getConfig().getInt("trivia.anti-repeat-count", 15));
        recentSignatures.addLast(signature);
        while (recentSignatures.size() > historySize) {
            recentSignatures.removeFirst();
        }
    }

    private TriviaQuestion generateQuestion() {
        int type = random.nextInt(7);
        return switch (type) {
            case 0 -> generateMathQuestion();
            case 1 -> generateCapitalQuestion(true);
            case 2 -> generateCapitalQuestion(false);
            case 3 -> generateFactQuestion(SCIENCE_FACTS, "sciences");
            case 4 -> generateChemistryQuestion();
            case 5 -> generateFactQuestion(HISTORY_DATES, "histoire");
            default -> random.nextBoolean()
                    ? generateFactQuestion(MC_BIOMES_STRUCTURES, "mc-biomes")
                    : generateFactQuestion(MC_POTIONS_REDSTONE, "mc-potions-redstone");
        };
    }

    private TriviaQuestion generateMathQuestion() {
        TriviaQuestion.Difficulty difficulty = randomDifficulty();
        int a, b;
        String operator;
        int result;

        switch (difficulty) {
            case FACILE -> {
                boolean addSub = random.nextBoolean();
                a = 2 + random.nextInt(48); // 2-49
                b = 2 + random.nextInt(48);
                if (addSub) {
                    operator = "+";
                    result = a + b;
                } else {
                    if (b > a) { int tmp = a; a = b; b = tmp; } // évite les négatifs
                    operator = "-";
                    result = a - b;
                }
            }
            case MOYEN -> {
                if (random.nextBoolean()) {
                    a = 11 + random.nextInt(19); // 11-29
                    b = 2 + random.nextInt(8);   // 2-9
                    operator = "x";
                    result = a * b;
                } else {
                    a = 20 + random.nextInt(180); // 20-199
                    b = 20 + random.nextInt(180);
                    if (random.nextBoolean()) {
                        operator = "+";
                        result = a + b;
                    } else {
                        if (b > a) { int tmp = a; a = b; b = tmp; }
                        operator = "-";
                        result = a - b;
                    }
                }
            }
            default -> { // DIFFICILE
                if (random.nextBoolean()) {
                    a = 11 + random.nextInt(19); // 11-29
                    b = 11 + random.nextInt(19); // 11-29
                    operator = "x";
                    result = a * b;
                } else {
                    // Division exacte garantie
                    b = 2 + random.nextInt(11); // 2-12
                    result = 4 + random.nextInt(30); // quotient 4-33
                    a = b * result;
                    operator = "/";
                }
            }
        }

        String display = "&bCalcul mental &7- Combien font &f" + a + " " + operator + " " + b + " &7?";
        List<String> accepted = List.of(normalize(String.valueOf(result)));
        double[] range = rewardRangeFor(difficulty);
        String signature = "math:" + a + operator + b;
        return new TriviaQuestion(display, accepted, difficulty, range[0], range[1], signature);
    }

    private TriviaQuestion generateCapitalQuestion(boolean askCapital) {
        TriviaQuestion.Difficulty difficulty = randomDifficulty();
        String[][] pool = switch (difficulty) {
            case FACILE -> COUNTRY_CAPITALS_EASY;
            case MOYEN -> COUNTRY_CAPITALS_MEDIUM;
            default -> COUNTRY_CAPITALS_HARD;
        };
        String[] pair = pool[random.nextInt(pool.length)];
        String country = pair[0];
        String capital = pair[1];

        String display;
        List<String> accepted;
        if (askCapital) {
            display = "&bGéographie &7- Quelle est la capitale de &f" + country + " &7?";
            accepted = List.of(normalize(capital));
        } else {
            display = "&bGéographie &7- " + capital + " &7est la capitale de quel pays ?";
            accepted = List.of(normalize(country));
        }

        double[] range = rewardRangeFor(difficulty);
        String signature = "geo:" + country;
        return new TriviaQuestion(display, accepted, difficulty, range[0], range[1], signature);
    }

    /**
     * Génère une question à partir d'un pool générique {question, réponse, difficulté(F/M/D)}
     * utilisé pour les sciences, l'histoire et les catégories Minecraft.
     */
    private TriviaQuestion generateFactQuestion(String[][] pool, String poolName) {
        String[] fact = pool[random.nextInt(pool.length)];
        String question = fact[0];
        String answer = fact[1];
        TriviaQuestion.Difficulty difficulty = parseDifficulty(fact[2]);

        String prefix = switch (poolName) {
            case "sciences" -> "&bSciences &7- ";
            case "histoire" -> "&bHistoire &7- ";
            case "mc-biomes" -> "&bMinecraft &7- ";
            default -> "&bMinecraft &7- ";
        };
        String display = prefix + "&f" + question;
        List<String> accepted = List.of(normalize(answer));
        double[] range = rewardRangeFor(difficulty);
        String signature = poolName + ":" + question;
        return new TriviaQuestion(display, accepted, difficulty, range[0], range[1], signature);
    }

    /** Question de chimie : symbole -> élément, ou élément -> symbole, au hasard. */
    private TriviaQuestion generateChemistryQuestion() {
        String[] pair = CHEMICAL_SYMBOLS[random.nextInt(CHEMICAL_SYMBOLS.length)];
        String symbol = pair[0];
        String element = pair[1];
        boolean askElement = random.nextBoolean(); // true: donne le symbole, demande l'élément

        TriviaQuestion.Difficulty difficulty = randomDifficulty();
        String display;
        List<String> accepted;
        if (askElement) {
            display = "&bChimie &7- Quel element chimique correspond au symbole &f" + symbol + " &7?";
            accepted = List.of(normalize(element));
        } else {
            display = "&bChimie &7- Quel est le symbole chimique de l'element &f" + element + " &7?";
            accepted = List.of(normalize(symbol));
        }

        double[] range = rewardRangeFor(difficulty);
        String signature = "chimie:" + symbol;
        return new TriviaQuestion(display, accepted, difficulty, range[0], range[1], signature);
    }

    private TriviaQuestion.Difficulty parseDifficulty(String code) {
        return switch (code) {
            case "F" -> TriviaQuestion.Difficulty.FACILE;
            case "D" -> TriviaQuestion.Difficulty.DIFFICILE;
            default -> TriviaQuestion.Difficulty.MOYEN;
        };
    }

    private TriviaQuestion.Difficulty randomDifficulty() {
        int roll = random.nextInt(100);
        if (roll < 45) return TriviaQuestion.Difficulty.FACILE;
        if (roll < 80) return TriviaQuestion.Difficulty.MOYEN;
        return TriviaQuestion.Difficulty.DIFFICILE;
    }

    /** Fourchette de gain [min ; max] selon la difficulté, elle-même bornée par trivia.min/max-reward. */
    private double[] rewardRangeFor(TriviaQuestion.Difficulty difficulty) {
        double configMin = plugin.getConfig().getDouble("trivia.min-reward", 1);
        double configMax = plugin.getConfig().getDouble("trivia.max-reward", 5000);
        double span = configMax - configMin;
        return switch (difficulty) {
            case FACILE -> new double[]{configMin, configMin + span * 0.10};      // ex: 1 - 500
            case MOYEN -> new double[]{configMin + span * 0.08, configMin + span * 0.30}; // ex: 400 - 1500
            case DIFFICILE -> new double[]{configMin + span * 0.15, configMax};   // ex: 750 - 5000
        };
    }

    private String difficultyLabel(TriviaQuestion.Difficulty difficulty) {
        return switch (difficulty) {
            case FACILE -> "&a[Facile]";
            case MOYEN -> "&e[Moyen]";
            case DIFFICILE -> "&c[Difficile]";
        };
    }

    // ==================== UTILITAIRES ====================

    /** Normalise une réponse : minuscules, sans accents, sans article ni ponctuation, espaces compactés. */
    private String normalize(String text) {
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String lower = noAccents.toLowerCase(Locale.FRANCE).trim();
        lower = lower.replaceAll("^(le |la |les |l'|l’)", "");
        lower = lower.replaceAll("[^a-z0-9 ]", " ");
        lower = lower.replaceAll("\\s+", " ").trim();
        return lower;
    }

    /** Ré-affiche un texte normalisé de façon un peu plus lisible pour l'annonce de fin (capitale MAJ). */
    private String prettify(String normalized) {
        if (normalized.isEmpty()) return normalized;
        String[] words = normalized.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String moneyRound(double amount) {
        return String.valueOf(Math.round(amount));
    }

    private void broadcast(String text) {
        Component c = legacy.deserialize(text);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(c);
        }
    }

    // ==================== STATISTIQUES JOUEURS (/qtop) ====================

    private PlayerTriviaStats getOrCreateStats(UUID uuid, String name) {
        synchronized (this) {
            PlayerTriviaStats stats = playerStats.get(uuid);
            if (stats == null) {
                stats = new PlayerTriviaStats(name);
                playerStats.put(uuid, stats);
            } else {
                stats.setLastKnownName(name);
            }
            return stats;
        }
    }

    /** Retourne les stats d'un joueur (ou des stats vides si il n'a jamais répondu). */
    public PlayerTriviaStats getStats(OfflinePlayer player) {
        synchronized (this) {
            PlayerTriviaStats stats = playerStats.get(player.getUniqueId());
            if (stats != null) return stats;
            return new PlayerTriviaStats(player.getName() != null ? player.getName() : "?");
        }
    }

    /** Retourne le top N des joueurs classés par meilleure série (bestStreak), décroissant. */
    public synchronized List<PlayerTriviaStats> getTopByBestStreak(int limit) {
        List<PlayerTriviaStats> sorted = new ArrayList<>(playerStats.values());
        sorted.sort(Comparator.comparingInt(PlayerTriviaStats::getBestStreak).reversed()
                .thenComparing(Comparator.comparingInt(PlayerTriviaStats::getCorrectAnswers).reversed()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    private void loadStats() {
        statsFile = new File(plugin.getDataFolder(), "trivia_stats.yml");
        if (!statsFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(statsFile);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection p = section.getConfigurationSection(key);
                if (p == null) continue;
                PlayerTriviaStats stats = new PlayerTriviaStats(
                        p.getString("name", "?"),
                        p.getInt("correct", 0),
                        p.getInt("wrong", 0),
                        p.getInt("current-streak", 0),
                        p.getInt("best-streak", 0)
                );
                playerStats.put(uuid, stats);
            } catch (IllegalArgumentException ignored) {
                // clé invalide dans le fichier, on l'ignore
            }
        }
    }

    /** Sauvegarde les stats de façon asynchrone pour ne pas bloquer le thread principal (I/O disque). */
    private void saveStatsAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveStats);
    }

    public synchronized void saveStats() {
        if (statsFile == null) {
            statsFile = new File(plugin.getDataFolder(), "trivia_stats.yml");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerTriviaStats> entry : playerStats.entrySet()) {
            PlayerTriviaStats stats = entry.getValue();
            String path = "players." + entry.getKey();
            yaml.set(path + ".name", stats.getLastKnownName());
            yaml.set(path + ".correct", stats.getCorrectAnswers());
            yaml.set(path + ".wrong", stats.getWrongAnswers());
            yaml.set(path + ".current-streak", stats.getCurrentStreak());
            yaml.set(path + ".best-streak", stats.getBestStreak());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder trivia_stats.yml : " + e.getMessage());
        }
    }
}
