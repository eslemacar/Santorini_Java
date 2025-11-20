import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Comparator;

/**
 * Implementiert den Utility-Based Reflex Agenten (Nutzer-basiert) für Santorini.
 * Der Agent bewertet Züge anhand einer gewichteten Heuristik und wählt den Zug mit dem höchsten Nutzen.
 */
public class ReflexAgent {
    private final String playerId;
    private final Random random;

    // --- HEURISTISCHE GEWICHTE ---
    private static final int W_WIN = 10000;      // Gewinnzug
    private static final int W_ADVANCE = 10;     // Aufstieg auf ein höheres Level (L < 3)
    private static final int W_BLOCK_OPP = 5;    // Gegner am Aufstieg auf Level 3 hindern (durch Kuppelbau)
    private static final int W_BUILD_THREAT = 4; // Bau von Level 3 (direkte Bedrohung)
    private static final int W_CENTER_CONTROL = 2; // Position im Zentrum
    private static final int W_MOVE_UP = 3;      // Bewegung auf eine höhere Ebene (L < 3)
    private static final int W_MOVE_DOWN = -2;   // Bewegung auf eine tiefere Ebene

    public ReflexAgent(String playerId) {
        this.playerId = playerId;
        this.random = new Random();
    }

    public String getPlayerId() {
        return playerId;
    }

    /**
     * Generiert alle legalen Züge für den Agenten.
     */
    public List<Move> getAllPossibleMoves(Board board) {
        List<Move> allMoves = new ArrayList<>();
        List<Worker> myWorkers = board.getWorkersByPlayer(playerId);

        if (myWorkers == null || myWorkers.isEmpty()) {
            return allMoves;
        }

        for (Worker worker : myWorkers) {
            int[] workerCoord = worker.getCoord();

            // 1. Mögliche Bewegungen finden
            List<int[]> moveTargets = board.getValidMoveTargets(workerCoord);

            for (int[] moveTo : moveTargets) {
                // Prüfe auf Gewinnzug (Priorität 1)
                if (board.checkWin(moveTo)) {
                    Move winMove = new Move(workerCoord, moveTo);
                    if (!allMoves.contains(winMove)) {
                        allMoves.add(winMove);
                    }
                    continue; // Ein Gewinnzug benötigt keinen Bau
                }

                // 2. Mögliche Baufelder finden (nach der hypothetischen Bewegung zu moveTo)
                List<int[]> potentialBuildTargets = board.getValidBuildTargets(moveTo);

                // Sonderfall: Bauen auf moveFrom
                if (!board.isDomed(workerCoord[0], workerCoord[1]) && !board.isOccupied(workerCoord[0], workerCoord[1])) {
                    boolean alreadyInList = false;
                    for(int[] target : potentialBuildTargets) {
                        if (target[0] == workerCoord[0] && target[1] == workerCoord[1]) {
                            alreadyInList = true;
                            break;
                        }
                    }
                    if (!alreadyInList) {
                        potentialBuildTargets.add(workerCoord);
                    }
                }

                // Füge alle vollen Züge hinzu
                for (int[] buildAt : potentialBuildTargets) {
                    allMoves.add(new Move(workerCoord, moveTo, buildAt));
                }
            }
        }
        return allMoves;
    }

    /**
     * Bewertet einen einzelnen Zug basierend auf dem Nutzen (Utility).
     */
    private int evaluateMoveUtility(Move move, Board board) {
        int utility = 0;
        int[] from = move.getMoveFrom();
        int[] to = move.getMoveTo();
        int[] build = move.getBuildAt();

        // Level-Informationen
        int currentLevel = board.getLevel(from[0], from[1]);
        int targetLevel = board.getLevel(to[0], to[1]);
        int buildLevelBefore = (build != null) ? board.getLevel(build[0], build[1]) : -1;
        int buildLevelAfter = (buildLevelBefore != -1 && buildLevelBefore < 4) ? buildLevelBefore + 1 : buildLevelBefore;

        // 1. Prio: Gewinnen
        if (move.getBuildAt() == null && board.checkWin(to)) {
            return W_WIN;
        }

        // 2. Prio: Bewegung
        if (targetLevel > currentLevel) {
            utility += W_MOVE_UP * (targetLevel - currentLevel);
        } else if (targetLevel < currentLevel) {
            utility += W_MOVE_DOWN;
        }

        // 3. Prio: Erreichen von Level 2 oder 3 (Vorbereitung)
        if (targetLevel == 2 || targetLevel == 3) {
            utility += W_ADVANCE * (targetLevel == 3 ? 3 : 1);
        }

        // 4. Prio: Bauen (falls Bauen Teil des Zugs ist)
        if (build != null) {
            // Bau einer Level-3-Bedrohung
            if (buildLevelAfter == 3) {
                utility += W_BUILD_THREAT;
            }
            // Bau einer Kuppel (Blockade)
            if (buildLevelAfter == Board.MAX_LEVEL) {
                utility += W_BLOCK_OPP;

                // Hohe Blockade, wenn der Bau in der Nähe eines gegnerischen Workers ist
                if (isBlockingOpponent(move.getMoveTo(), board)) {
                    utility += W_BLOCK_OPP * 2;
                }
            }
        }

        // 5. Prio: Positionelle Kontrolle (Zentrum)
        int distCenter = Math.abs(to[0] - 2) + Math.abs(to[1] - 2);
        if (distCenter <= 1) {
            utility += W_CENTER_CONTROL * (2 - distCenter);
        }

        // 6. Prio: Defensives Bauen (Gegner blockieren/hindern)
        if (build != null) {
            if (isBuildNearOpponent(build, board)) {
                utility += 1;
            }
        }

        return utility;
    }

    /**
     * Hilfsfunktion: Prüft, ob der Bau in der Nähe eines Gegners erfolgt.
     */
    private boolean isBuildNearOpponent(int[] buildCoord, Board board) {
        for (int[] neighbor : board.getNeighbors(buildCoord)) {
            String workerId = board.getWorkerIdAt(neighbor[0], neighbor[1]);
            if (workerId != null && !workerId.equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hilfsfunktion: Prüft, ob der Worker den Gegner direkt blockiert (zum Beispiel durch Kuppel).
     */
    private boolean isBlockingOpponent(int[] newWorkerCoord, Board board) {
        for (String pid : board.getPlayerIds()) {
            if (pid.equals(playerId)) continue;

            for (Worker w : board.getWorkersByPlayer(pid)) {
                int[] oppCoord = w.getCoord();
                // Prüfe, ob der Gegner durch diesen Zug keine Züge mehr hat
                if (board.getValidMoveTargets(oppCoord).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Wählt den besten Zug basierend auf der Utility-Funktion.
     */
    public MoveEvaluation chooseMove(Board board) {
        List<Move> possibleMoves = getAllPossibleMoves(board);
        if (possibleMoves.isEmpty()) {
            return new MoveEvaluation(null, "Keine legalen Züge möglich. KI ist blockiert.");
        }

        List<MoveScore> scoredMoves = new ArrayList<>();
        int maxUtility = Integer.MIN_VALUE;

        // 1. Alle Züge bewerten
        for (Move move : possibleMoves) {
            int utility = evaluateMoveUtility(move, board);
            scoredMoves.add(new MoveScore(move, utility));
            maxUtility = Math.max(maxUtility, utility);
        }

        // 2. Nur die besten Züge auswählen
        List<Move> bestMoves = new ArrayList<>();
        for (MoveScore score : scoredMoves) {
            if (score.utility == maxUtility) {
                bestMoves.add(score.move);
            }
        }

        // 3. Zufälligen Zug aus den besten auswählen
        Move finalMove = bestMoves.get(random.nextInt(bestMoves.size()));

        // 4. Erklärende Bewertung generieren
        String explanation = generateExplanation(finalMove, maxUtility, board); // board übergeben

        return new MoveEvaluation(finalMove, explanation);
    }

    // --- NEUE HILFSFUNKTIONEN FÜR MENSCHLICHE ERKLÄRUNG ---

    /**
     * Führt die detaillierte, menschenlesbare Erklärung des Zuges aus.
     */
    private String generateExplanation(Move move, int utility, Board board) {
        if (utility == W_WIN) return "ULTIMATE UTILITY: Gewinnzug! Das Spiel wird beendet.";

        StringBuilder sb = new StringBuilder("UTILITY-SCORE: " + utility + ". ");

        // --- 1. Allgemeine Bewertung (basierend auf Score) ---
        if (utility >= 12) {
            sb.append(">>> EXZELLENTER ZUG <<< Starke Offensive und Defensive kombiniert.");
        } else if (utility >= 8) {
            sb.append("Guter Zug: Erzielt mehrere taktische Vorteile.");
        } else if (utility >= 4) {
            sb.append("Solider Zug: Verbessert die Position oder setzt eine kleine Bedrohung.");
        } else {
            sb.append("Standardzug: Minimale Verbesserung oder nur Positionierung.");
        }

        // --- 2. Detailanalyse der Bewegung und des Baus ---

        int[] from = move.getMoveFrom();
        int[] to = move.getMoveTo();
        int[] build = move.getBuildAt();

        int currentLevel = board.getLevel(from[0], from[1]);
        int targetLevel = board.getLevel(to[0], to[1]);
        int buildLevelBefore = (build != null) ? board.getLevel(build[0], build[1]) : -1;
        int buildLevelAfter = (buildLevelBefore != -1 && buildLevelBefore < 4) ? buildLevelBefore + 1 : buildLevelBefore;

        sb.append(" Details: ");

        // A. Bewegungsvorteil
        if (targetLevel > currentLevel) {
            sb.append(" [AUFSTIEG] Bewegt sich auf Level ").append(targetLevel).append(". ");
        } else if (targetLevel < currentLevel) {
            sb.append(" [ABSTIEG] Nimmt eine niedrigere, sicherere Position ein. ");
        }

        // B. Taktischer Bau
        if (build != null) {
            if (buildLevelAfter == Board.MAX_LEVEL) {
                // Wenn der Bau auf Level 4 geht (Kuppel)
                sb.append(" [KUPPELBAU] Blockiert Feld ").append(coordToNotation(build)).append(" permanent.");
                if (isBuildNearOpponent(build, board)) {
                    sb.append(" Blockiert einen gegnerischen Aufstieg. ");
                }
            } else if (buildLevelAfter == 3) {
                // Wenn der Bau auf Level 3 geht
                sb.append(" [BEDROHUNG] Erstellt Level 3 auf ").append(coordToNotation(build)).append(". ");
            }
        }

        // C. Positioneller Vorteil
        int distCenter = Math.abs(to[0] - 2) + Math.abs(to[1] - 2);
        if (distCenter <= 1) {
            sb.append(" [ZENTRUM] Kontrolliert das Zentrum. ");
        }

        // D. Gegner blockiert (Heuristik)
        // Hinweis: Wir verwenden die isBlockingOpponent-Logik erneut,
        // obwohl dies bereits im Utility-Score enthalten ist, um die Erklärung zu vereinfachen.
        if (isBlockingOpponent(move.getMoveTo(), board)) {
            sb.append(" [BLOCKADE] Der Zug behindert gegnerische Bewegungen. ");
        }

        return sb.toString();
    }

    /**
     * HILFSFUNKTION: Konvertiert Koordinaten-Array in menschenlesbare Notation (a1-e5).
     * Muss hier eingefügt werden, da sie in generateExplanation verwendet wird.
     */
    private String coordToNotation(int[] coord) {
        if (coord == null || coord.length < 2) return "N/A";
        char colChar = (char) ('a' + coord[0]);
        // Santorini-Koordinaten (0=Reihe 1, 4=Reihe 5)
        char rowChar = (char) ('1' + coord[1]);
        return String.valueOf(colChar) + rowChar;
    }


    /**
     * Innere Klasse zur Rückgabe von Zug und Bewertung.
     */
    public static class MoveEvaluation {
        public final Move move;
        public final String evaluation;

        public MoveEvaluation(Move move, String evaluation) {
            this.move = move;
            this.evaluation = evaluation;
        }
    }

    /**
     * Innere Klasse zur Speicherung von Zug und dessen Utility.
     */
    private static class MoveScore {
        final Move move;
        final int utility;

        MoveScore(Move move, int utility) {
            this.move = move;
            this.utility = utility;
        }
    }
}