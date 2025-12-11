import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lernfähiger ReflexAgent mit:
 * - persistierbaren Gewichten (save/load)
 * - einfacher Batch-Update-Funktion am Spielende (delta = finalReward - predicted)
 * - Speicherung der Feature-History pro Spiel
 *
 * Hinweis: Datei-Speicherung erfolgt als CSV: weights_<playerId>.csv
 */
public class ReflexAgent {
    private final String playerId;
    private final Random random;

    // --- LERNPARAMETER ---
    private static double ALPHA = 0.005;   // Lernrate
    private static double EPSILON = 0.15;  // Explorationsrate (0 = keine Exploration)

    // --- DYNAMISCHE GEWICHTE ---
    private final double[] weights;
    private static final int W_WIN_IDX = 0;
    private static final int W_ADVANCE_IDX = 1;
    private static final int W_BLOCK_OPP_IDX = 2;
    private static final int W_BUILD_THREAT_IDX = 3;
    private static final int W_CENTER_CONTROL_IDX = 4;
    private static final int W_MOVE_UP_IDX = 5;
    private static final int W_MOVE_DOWN_IDX = 6;
    private static final int NUM_WEIGHTS = 7;

    // --- HISTORY für Learning (pro Spiel) ---
    // speichert Feature-Vektoren, die während des Spiels gewählt wurden
    private final List<double[]> featureHistory;

    public ReflexAgent(String playerId) {
        this.playerId = playerId;
        this.random = new Random();
        this.weights = new double[NUM_WEIGHTS];
        this.featureHistory = new ArrayList<>();

        // Standardinitialisierung (wie zuvor)
        weights[W_WIN_IDX] = 50000;
        weights[W_ADVANCE_IDX] = 30;
        weights[W_BLOCK_OPP_IDX] = 8;
        weights[W_BUILD_THREAT_IDX] = 8;
        weights[W_CENTER_CONTROL_IDX] =6;
        weights[W_MOVE_UP_IDX] = 50;
        weights[W_MOVE_DOWN_IDX] = -10;

        // Versuche beim Erzeugen Gewichte zu laden (falls vorhanden)
        try {
            loadWeights();
        } catch (Exception e) {
            // Wenn Laden fehlschlägt, bleiben die Default-Gewichte erhalten
        }
    }

    public String getPlayerId() {
        return playerId;
    }

    // --- Datei-Pfade für Gewichtsspeicherung ---
    private Path getWeightsPath() {
        String filename = "weights_" + playerId + ".csv";
        return Paths.get(filename);
    }

    /**
     * Speichert die aktuellen Gewichte in eine CSV-Datei.
     */
    public synchronized void saveWeights() {
        Path p = getWeightsPath();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < weights.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(Double.toString(weights[i]));
        }
        try {
            Files.write(p, sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("ReflexAgent " + playerId + ": Gewichte gespeichert -> " + p.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("ReflexAgent " + playerId + ": Fehler beim Speichern der Gewichte: " + e.getMessage());
        }
    }

    /**
     * Lädt Gewichte aus CSV, falls die Datei existiert.
     */
    public synchronized void loadWeights() throws IOException {
        Path p = getWeightsPath();
        if (!Files.exists(p)) return;
        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) return;
        String[] parts = content.split(",");
        int n = Math.min(parts.length, weights.length);
        for (int i = 0; i < n; i++) {
            try {
                weights[i] = Double.parseDouble(parts[i]);
            } catch (NumberFormatException ex) {
                // safe fallback: beibehalten
            }
        }
        System.out.println("ReflexAgent " + playerId + ": Gewichte geladen.");
    }

    /**
     * Generiert alle legalen Züge für den Agenten.
     * (bleibt inhaltlich wie in deiner Version)
     */
    public List<Move> getAllPossibleMoves(Board board) {
        List<Move> allMoves = new ArrayList<>();
        List<Worker> myWorkers = board.getWorkersByPlayer(playerId);

        if (myWorkers == null || myWorkers.isEmpty()) {
            return allMoves;
        }

        for (Worker worker : myWorkers) {
            int[] workerCoord = worker.getCoord();

            List<int[]> moveTargets = board.getValidMoveTargets(workerCoord);

            for (int[] moveTo : moveTargets) {
                if (board.checkWin(moveTo)) {
                    Move winMove = new Move(workerCoord, moveTo);
                    if (!allMoves.contains(winMove)) {
                        allMoves.add(winMove);
                    }
                    continue;
                }

                List<int[]> potentialBuildTargets = board.getValidBuildTargets(moveTo);

                // optional: erlaubt Bauen auf dem Feld, das gerade verlassen wurde
                boolean containsFrom = false;
                for (int[] t : potentialBuildTargets) {
                    if (t[0] == workerCoord[0] && t[1] == workerCoord[1]) { containsFrom = true; break;}
                }
                if (!containsFrom) {
                    // (nur hinzufügen wenn nicht domed/occupied — Board.getValidBuildTargets hat das schon geprüft)
                    potentialBuildTargets.add(workerCoord);
                }

                for (int[] buildAt : potentialBuildTargets) {
                    allMoves.add(new Move(workerCoord, moveTo, buildAt));
                }
            }
        }
        return allMoves;
    }

    /**
     * Extrahiert Features (Vektor) für einen Move auf dem gegebenen Board.
     */
    private double[] extractFeatures(Move move, Board board) {
        double[] features = new double[NUM_WEIGHTS];
        int[] from = move.getMoveFrom();
        int[] to = move.getMoveTo();
        int[] build = move.getBuildAt();

        // Level-Informationen
        int currentLevel = board.getLevel(from[0], from[1]);
        int targetLevel = board.getLevel(to[0], to[1]);
        int buildLevelBefore = (build != null) ? board.getLevel(build[0], build[1]) : -1;
        int buildLevelAfter = (buildLevelBefore != -1 && buildLevelBefore < Board.MAX_LEVEL) ? buildLevelBefore + 1 : buildLevelBefore;

        // 0. W_WIN
        if (move.getBuildAt() == null && board.checkWin(to)) {
            features[W_WIN_IDX] = 1.0;
        }

        // 1. W_ADVANCE (Vorbereitung auf Level 3)
        if (targetLevel == 2 || targetLevel == 3) {
            features[W_ADVANCE_IDX] = (targetLevel == 3 ? 3.0 : 1.0);
        }

        // 2. W_BLOCK_OPP / W_BUILD_THREAT
        if (build != null) {
            if (buildLevelAfter == 3) {
                features[W_BUILD_THREAT_IDX] = 1.0;
            }
            if (buildLevelAfter == Board.MAX_LEVEL) {
                features[W_BLOCK_OPP_IDX] = 1.0;
            }
        }

        // 3. W_CENTER_CONTROL
        int distCenter = Math.abs(to[0] - 2) + Math.abs(to[1] - 2);
        if (distCenter <= 1) {
            features[W_CENTER_CONTROL_IDX] = (2 - distCenter);
        }

        // 4. W_MOVE_UP / W_MOVE_DOWN
        if (targetLevel > currentLevel) {
            features[W_MOVE_UP_IDX] = (targetLevel - currentLevel);
        } else if (targetLevel < currentLevel) {
            features[W_MOVE_DOWN_IDX] = 1.0;
        }

        return features;
    }

    /**
     * Utility: Skalarprodukt w^T * features
     */
    private double calculateUtility(Move move, Board board) {
        double utility = 0;
        double[] features = extractFeatures(move, board);

        for (int i = 0; i < NUM_WEIGHTS; i++) {
            utility += weights[i] * features[i];
        }

        return utility;
    }

    /**
     * Update-Gewichte am Spielende.
     * Einfache Form: für jeden gespeicherten Feature-Vektor x:
     *   pred = w^T x
     *   delta = finalReward - pred
     *   w += alpha * delta * x
     *
     * Danach wird die Feature-History geleert.
     */
    public synchronized void updateWeights(double finalReward) {
        if (featureHistory.isEmpty()) {
            // Falls leer (z. B. KI spielte gar nicht), trotzdem kleine Anpassung (optional)
            return;
        }

        for (double[] x : featureHistory) {
            double pred = 0;
            for (int i = 0; i < NUM_WEIGHTS; i++) pred += weights[i] * x[i];
            double delta = finalReward - pred;
            // Update
            for (int i = 0; i < NUM_WEIGHTS; i++) {
                weights[i] += ALPHA * delta * x[i];
            }
        }

        // History leeren
        featureHistory.clear();
        System.out.println("ReflexAgent " + playerId + ": Gewichte aktualisiert (reward=" + finalReward + ")");
    }

    /**
     * Wählt den besten Zug.
     * - epsilon-greedy: mit Wahrscheinlichkeit EPSILON zufällig
     * - speichert das Feature-Vector des tatsächlich gewählten Zuges in featureHistory
     */
    public MoveEvaluation chooseMove(Board board) {
        List<Move> possibleMoves = getAllPossibleMoves(board);
        if (possibleMoves.isEmpty()) {
            return new MoveEvaluation(null, "Keine legalen Züge möglich. KI ist blockiert.");
        }

        // Exploration
        if (EPSILON > 0 && random.nextDouble() < EPSILON) {
            Move rnd = possibleMoves.get(random.nextInt(possibleMoves.size()));
            // speichere Feature für Training
            double[] feat = extractFeatures(rnd, board);
            featureHistory.add(feat);
            String expl = "Explorativ zufälliger Zug.";
            return new MoveEvaluation(rnd, expl);
        }

        double maxUtility = Double.NEGATIVE_INFINITY;
        List<Move> bestMoves = new ArrayList<>();

        for (Move move : possibleMoves) {
            double utility = calculateUtility(move, board);
            if (utility > maxUtility) {
                maxUtility = utility;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (utility == maxUtility) {
                bestMoves.add(move);
            }
        }

        Move finalMove = bestMoves.get(random.nextInt(bestMoves.size()));

        // speichere Features der gewählten Aktion (für späteres Lernen)
        double[] chosenFeatures = extractFeatures(finalMove, board);
        featureHistory.add(chosenFeatures);

        // erklärung
        String explanation = generateExplanation(finalMove, (int)Math.round(maxUtility), board);

        return new MoveEvaluation(finalMove, explanation);
    }

    // --- Hilfsfunktionen (wie vorher) ---

    private String generateExplanation(Move move, int utility, Board board) {
        if (utility == (int)weights[W_WIN_IDX]) return "ULTIMATE UTILITY: Gewinnzug! Das Spiel wird beendet.";

        StringBuilder sb = new StringBuilder("UTILITY-SCORE: " + utility + ". ");

        if (utility >= 12) {
            sb.append(">>> EXZELLENTER ZUG <<< Starke Offensive und Defensive kombiniert.");
        } else if (utility >= 8) {
            sb.append("Guter Zug: Erzielt mehrere taktische Vorteile.");
        } else if (utility >= 4) {
            sb.append("Solider Zug: Verbessert die Position oder setzt eine kleine Bedrohung.");
        } else {
            sb.append("Standardzug: Minimale Verbesserung oder nur Positionierung.");
        }

        int[] from = move.getMoveFrom();
        int[] to = move.getMoveTo();
        int[] build = move.getBuildAt();

        int currentLevel = board.getLevel(from[0], from[1]);
        int targetLevel = board.getLevel(to[0], to[1]);
        int buildLevelBefore = (build != null) ? board.getLevel(build[0], build[1]) : -1;
        int buildLevelAfter = (buildLevelBefore != -1 && buildLevelBefore < Board.MAX_LEVEL) ? buildLevelBefore + 1 : buildLevelBefore;

        sb.append(" Details: ");
        if (targetLevel > currentLevel) {
            sb.append(" [AUFSTIEG] Bewegt sich auf Level ").append(targetLevel).append(". ");
        } else if (targetLevel < currentLevel) {
            sb.append(" [ABSTIEG] Nimmt eine niedrigere, sicherere Position ein. ");
        }

        if (build != null) {
            if (buildLevelAfter == Board.MAX_LEVEL) {
                sb.append(" [KUPPELBAU] Blockiert Feld ").append(coordToNotation(build)).append(" permanent.");
                if (isBuildNearOpponent(build, board)) {
                    sb.append(" Blockiert einen gegnerischen Aufstieg. ");
                }
            } else if (buildLevelAfter == 3) {
                sb.append(" [BEDROHUNG] Erstellt Level 3 auf ").append(coordToNotation(build)).append(". ");
            }
        }

        int distCenter = Math.abs(to[0] - 2) + Math.abs(to[1] - 2);
        if (distCenter <= 1) {
            sb.append(" [ZENTRUM] Kontrolliert das Zentrum. ");
        }

        return sb.toString();
    }


    private String coordToNotation(int[] coord) {
        if (coord == null || coord.length < 2) return "N/A";
        char colChar = (char) ('a' + coord[0]);
        // Row 0 (unten) -> '1', Row 4 (oben) -> '5'
        char rowChar = (char) ('1' + coord[1]);
        return String.valueOf(colChar) + rowChar;
    }

    private boolean isBuildNearOpponent(int[] buildCoord, Board board) {
        for (int[] neighbor : board.getNeighbors(buildCoord)) {
            String workerId = board.getWorkerIdAt(neighbor[0], neighbor[1]);
            if (workerId != null && !workerId.equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    // --- Get/Set Hilfen (nützlich für UI / Debug) ---
    public double[] getWeightsCopy() {
        double[] copy = new double[weights.length];
        System.arraycopy(weights, 0, copy, 0, weights.length);
        return copy;
    }

    public static void setAlpha(double a) { ALPHA = a; }
    public static void setEpsilon(double e) { EPSILON = e; }

    /**
     * Inner class for returning move + explanation
     */
    public static class MoveEvaluation {
        public final Move move;
        public final String evaluation;

        public MoveEvaluation(Move move, String evaluation) {
            this.move = move;
            this.evaluation = evaluation;
        }
    }
}
