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

        // die Episoden-Zahl nur für runTraining()
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
        currentPlayer = playerIds.get(currentIdx);

        boolean gameOver = false;
        String winner = null;
        int moves = 0;

        while (!gameOver && moves < maxMovesPerGame) {
            SmartAgent agent = agents.get(currentIdx);
            ReflexAgent.MoveEvaluation eval = agent.chooseMove(board);
            Move move = eval.move;

            if (move == null) {
                // Blockade -> Gewinner ist der Gegner
                gameOver = true;
                // Gewinner ist der Spieler VOR dem blockierten Spieler
                winner = playerIds.get((currentIdx + 1) % 2);
                break;
            } else {
                board.moveWorker(currentPlayer, move.getMoveFrom(), move.getMoveTo());
                if (move.getBuildAt() != null) board.buildStructure(move.getBuildAt());

                if (board.checkWin(move.getMoveTo())) {
                    gameOver = true;
                    winner = currentPlayer;
                }
            }

            // nur weiter zum nächsten Spieler, wenn das Spiel nicht vorbei ist
            if (!gameOver) {
                currentIdx = (currentIdx + 1) % 2;
                currentPlayer = playerIds.get(currentIdx);
                moves++;
            }
        }

        //  LERNSCHRITT NACH ENDE DER EPISODE
        double rewardP1 = 0.0;
        double rewardP2 = 0.0;

        if (winner != null) {
            if (winner.equals("P1")) {
                rewardP1 = 1000.0;
                rewardP2 = -1000.0;
            } else if (winner.equals("P2")) {
                rewardP1 = -1000.0;
                rewardP2 = 1000.0;
            }
        }

        // Benachrichtige Agenten nur einmal am Ende
        agents.get(0).notifyGameEnd(rewardP1); // P1
        agents.get(1).notifyGameEnd(rewardP2); // P2

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
