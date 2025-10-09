import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Implementiert den Reflex Agenten (Regel-basiert) für Santorini.
 */
public class ReflexAgent {
    private final String playerId;
    private final Random random;

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
                List<int[]> buildTargets = board.getValidBuildTargets(moveTo);

                for (int[] buildAt : buildTargets) {
                    allMoves.add(new Move(workerCoord, moveTo, buildAt));
                }
            }
        }
        return allMoves;
    }

    /**
     * Wählt den besten Zug basierend auf einer festen Prioritätenliste.
     * @return Ein Move-Objekt und eine Erklärung (Evaluation).
     */
    public MoveEvaluation chooseMove(Board board) {
        List<Move> possibleMoves = getAllPossibleMoves(board);
        if (possibleMoves.isEmpty()) {
            return new MoveEvaluation(null, "Keine legalen Züge möglich. KI ist blockiert.");
        }

        // --- Prioritätenliste ---

        // 1. Priorität: Gewinnen
        List<Move> winningMoves = new ArrayList<>();
        for (Move move : possibleMoves) {
            if (move.getBuildAt() == null) { // Gewinnzüge haben buildAt == null
                winningMoves.add(move);
            }
        }
        if (!winningMoves.isEmpty()) {
            Move move = winningMoves.get(random.nextInt(winningMoves.size()));
            return new MoveEvaluation(move, "Gewinnzug! Level 3 wird erreicht.");
        }

        // 2. Priorität: Aufsteigen (Level L -> L+1, L < 3)
        List<Move> upwardMoves = new ArrayList<>();
        for (Move move : possibleMoves) {
            int currentLevel = board.getLevel(move.getMoveFrom()[0], move.getMoveFrom()[1]);
            int targetLevel = board.getLevel(move.getMoveTo()[0], move.getMoveTo()[1]);

            if (targetLevel == currentLevel + 1 && targetLevel < 3) {
                upwardMoves.add(move);
            }
        }
        if (!upwardMoves.isEmpty()) {
            Move move = upwardMoves.get(random.nextInt(upwardMoves.size()));
            return new MoveEvaluation(move, "Aufstieg: Bringt Arbeiter auf eine höhere Ebene, um den Gewinn vorzubereiten.");
        }

        // 3. Priorität: Bauen einer Bedrohung (Level 3) oder Blockade (Kuppel)
        List<Move> tacticalMoves = new ArrayList<>();
        for (Move move : possibleMoves) {
            int[] buildAt = move.getBuildAt();
            if (buildAt != null) {
                int buildLevelBefore = board.getLevel(buildAt[0], buildAt[1]);
                // Baue auf Level 3 (wenn vorher Level 2) oder Kuppel (wenn vorher Level 3)
                if (buildLevelBefore == 2 || buildLevelBefore == 3) {
                    tacticalMoves.add(move);
                }
            }
        }
        if (!tacticalMoves.isEmpty()) {
            Move move = tacticalMoves.get(random.nextInt(tacticalMoves.size()));
            String explanation = board.getLevel(move.getBuildAt()[0], move.getBuildAt()[1]) == 3 ?
                    "Taktischer Bau: Erstellt Level 3 als direkte Bedrohung." :
                    "Taktischer Bau: Baut eine Kuppel zur Blockade.";
            return new MoveEvaluation(move, explanation);
        }

        // 4. Standard: Zufälliger gültiger Zug
        Move move = possibleMoves.get(random.nextInt(possibleMoves.size()));
        return new MoveEvaluation(move, "Standardzug: Keine direkten Vorteile oder Bedrohungen erkannt. Wählt einen zufälligen Zug.");
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
}
