import java.util.*;

public class Trainer {
    private final int maxMovesPerGame;
    private final Random random = new Random();
    private List<String> playerIds;
    private SmartAgent a1;
    private SmartAgent a2;
    private Board board;
    private int currentIdx;
    private String currentPlayer;
    private List<SmartAgent> agents;

    public Trainer(int episodes, int maxMovesPerGame) {
        this.maxMovesPerGame = maxMovesPerGame;

        // Wir brauchen die Episoden-Zahl nur für runTraining(), nicht für einzelne Episode
    }

    /** Vollständiges Training ausführen */
    public void runTraining(int episodes) {
        for (int ep = 1; ep <= episodes; ep++) {
            runOneEpisode();
            if (ep % Math.max(1, episodes / 10) == 0) {
                System.out.println("Episode " + ep + "/" + episodes + " abgeschlossen.");
            }
        }
        System.out.println("Training abgeschlossen.");
    }

    /** Eine einzelne Episode ausführen (für GUI oder Schritt-für-Schritt Training) */
    public String runOneEpisode() {
        // Initialisiere Spieler und Board
        playerIds = Arrays.asList("P1", "P2");
        board = new Board(playerIds);

        a1 = new SmartAgent("P1");
        a2 = new SmartAgent("P2");
        agents = Arrays.asList(a1, a2);

        // Zufällige Platzierung der Arbeiter
        randomPlacement(board, "P1");
        randomPlacement(board, "P2");

        // Startspieler zufällig wählen
        currentIdx = random.nextInt(2);
        currentPlayer = currentIdx == 0 ? "P1" : "P2";

        boolean gameOver = false;
        String winner = null;
        int moves = 0;

        while (!gameOver && moves < maxMovesPerGame) {
            SmartAgent agent = agents.get(currentIdx);
            ReflexAgent.MoveEvaluation eval = agent.chooseMove(board);
            Move move = eval.move;

            if (move == null) {
                // Unmöglicher Zug -> neutral
                for (SmartAgent sa : agents) sa.notifyGameEnd(0.0);
                gameOver = true;
                winner = null;
            } else {
                board.moveWorker(currentPlayer, move.getMoveFrom(), move.getMoveTo());
                if (move.getBuildAt() != null) board.buildStructure(move.getBuildAt());

                if (board.checkWin(move.getMoveTo())) {
                    gameOver = true;
                    winner = currentPlayer;
                }
            }

            // Belohnung
            for (SmartAgent sa : agents) {
                if (winner == null) sa.notifyGameEnd(0.0);
                else if (sa.getPlayerId().equals(winner)) sa.notifyGameEnd(1.0);
                else sa.notifyGameEnd(-1.0);
            }

            currentIdx = (currentIdx + 1) % 2;
            currentPlayer = playerIds.get(currentIdx);
            moves++;
        }

        return winner == null ? "Unentschieden" : winner;
    }

    /** Zufällige Platzierung von Arbeitern auf dem Board */
    private void randomPlacement(Board board, String pid) {
        int placed = 0;
        int tries = 0;
        while (placed < 2 && tries < 1000) {
            int c = random.nextInt(Board.BOARD_SIZE);
            int r = random.nextInt(Board.BOARD_SIZE);
            if (!board.isOccupied(c, r)) {
                board.placeWorker(pid, placed + 1, c, r);
                placed++;
            }
            tries++;
        }
        if (placed < 2) throw new IllegalStateException("Konnte Arbeiter nicht zufällig platzieren");
    }

    /** Main für CLI-Training */
    public static void main(String[] args) {
        int episodes = 1000;
        int maxMoves = 200;

        if (args.length >= 1) episodes = Integer.parseInt(args[0]);
        if (args.length >= 2) maxMoves = Integer.parseInt(args[1]);

        Trainer trainer = new Trainer(episodes, maxMoves);
        trainer.runTraining(episodes);
    }
}
