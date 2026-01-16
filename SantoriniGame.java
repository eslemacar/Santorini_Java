import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Hauptklasse, die den Spielablauf steuert und die Konsole für die Züge verwendet.
 * Behebt alle bekannten Fehler (Scanner-Close, Notations-Typfehler und Bau-Validierung).
 * HINWEIS: Diese Klasse ist für die Konsolensteuerung. Für die GUI verwenden Sie SantoriniGUI.java.
 */
public class SantoriniGame {
    private final Board board;
    private final Map<String, ReflexAgent> agents;
    private final List<String> playerIds;
    private int currentPlayerIndex;
    private boolean gameOver = false;
    private String winnerId = null;
    private final Map<String, Double> timeBank; // Zeitpolster

    // Der Scanner wird nur einmal für System.in initialisiert und übergeben
    private final Scanner scanner;

    public SantoriniGame(int totalPlayers, Scanner existingScanner) {
        this.scanner = existingScanner;
        this.playerIds = new ArrayList<>();
        this.agents = new HashMap<>();
        this.timeBank = new HashMap<>();

        // Spieler anlegen
        for (int i = 1; i <= totalPlayers; i++) {
            String pid = "P" + i;
            playerIds.add(pid);

            // Nur der letzte Spieler ist KI
            if (i == totalPlayers) {
                ReflexAgent agent = new ReflexAgent(pid);
                agents.put(pid, agent);
                timeBank.put(pid, 120.0);
            }
        }

        this.board = new Board(playerIds);
    }


    //  Spielsteuerung

    public void run() {
        System.out.println("Willkommen bei Santorini (mit " + playerIds.size() + " Spielern)! P1 ist Mensch.");

        // 1. Arbeiter platzieren
        for (String pid : playerIds) {
            setupWorkers(pid);
        }

        // 2. Haupt-Spielschleife
        currentPlayerIndex = 0;
        while (!gameOver) {
            String currentPlayerId = playerIds.get(currentPlayerIndex);

            // 3. Auf Blockade prüfen
            if (checkBlockade()) {
                break;
            }

            board.display(currentPlayerId);
            playTurn(currentPlayerId);

            if (!gameOver) {
                currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
            }
        }

        // Spielende
        System.out.println("\n---------------- SPIEL ENDE ----------------");
        if (winnerId != null) {
            System.out.println("Der GEWINNER ist: " + winnerId + "!");
        } else {
            System.out.println("Das Spiel endete abrupt.");
        }
    }

    private void setupWorkers(String playerId) {
        System.out.println("\n--- " + playerId + " Platzierungsphase ---");

        for (int i = 1; i <= 2; i++) { // 2 Arbeiter pro Spieler
            boolean placed = false;
            while (!placed) {
                if (agents.containsKey(playerId)) {
                    // KI-Platzierung (Zufällig)
                    Random random = new Random();
                    int col, row;
                    do {
                        col = random.nextInt(Board.BOARD_SIZE);
                        row = random.nextInt(Board.BOARD_SIZE);
                    } while (board.isOccupied(col, row));

                    board.placeWorker(playerId, i, col, row);
                    System.out.println("KI " + playerId + " platziert Arbeiter " + i + ": " + coordToNotation(col, row));
                    placed = true;

                } else {
                    // Menschliche Platzierung
                    System.out.print("Geben Sie die Position (z.B. a1) für Arbeiter " + i + " von " + playerId + " ein: ");
                    String notation = scanner.nextLine().trim().toLowerCase();
                    int[] coord = notationToCoord(notation);

                    if (coord != null && board.isValidCoord(coord[0], coord[1]) && !board.isOccupied(coord[0], coord[1])) {
                        board.placeWorker(playerId, i, coord[0], coord[1]);
                        placed = true;
                    } else {
                        System.out.println("Ungültige Position oder Feld bereits belegt. Bitte erneut versuchen.");
                    }
                }
            }
        }
    }

    private void playTurn(String playerId) {
        if (agents.containsKey(playerId)) {
            // KI-Zug
            handleAiTurn(playerId);
        } else {
            // Menschlicher Zug
            handleHumanTurn(playerId);
        }
    }


    private void handleAiTurn(String playerId) {
        System.out.println("\n--- " + playerId + " (KI) ist am Zug ---");
        ReflexAgent agent = agents.get(playerId);

        //  Simulation einer Bedenkzeit
        long startTime = System.nanoTime();
        try {
            TimeUnit.MILLISECONDS.sleep(new Random().nextInt(500) + 200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Agent wählt den Zug und liefert die Bewertung
        ReflexAgent.MoveEvaluation evaluation = agent.chooseMove(board);
        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;

        // 1. ZUG-ZEIT-REGEL PRÜFEN
        System.out.println("KI-Bedenkzeit: " + String.format("%.2f", elapsedSeconds) + "s");

        if (elapsedSeconds > 10.0) {
            double penalty = elapsedSeconds - 10.0;
            timeBank.put(playerId, timeBank.get(playerId) - penalty);
            System.out.println("WARNUNG: Zug hat " + String.format("%.2f", elapsedSeconds) + "s gedauert. Strafe: " + String.format("%.2f", penalty) + "s.");

            if (timeBank.get(playerId) < 0) {
                System.out.println("ZEITPOLSTER AUFGEBRAUCHT. " + playerId + " verzichtet und verliert.");
                gameOver = true;
                winnerId = playerIds.get((currentPlayerIndex - 1 + playerIds.size()) % playerIds.size());
                return;
            }
        }

        Move move = evaluation.move;
        if (move == null) {
            System.out.println("FEHLER: KI findet keinen legalen Zug. KI ist blockiert und verliert.");
            gameOver = true;
            winnerId = playerIds.get((currentPlayerIndex - 1 + playerIds.size()) % playerIds.size());
            return;
        }

        // 2. Zug ausführen
        executeMove(playerId, move);

        // 3. Zugbewertung
        System.out.println("\n--- ZUGBEWERTUNG (KI-Sicht) ---");
        System.out.println(evaluation.evaluation); // Gibt den detaillierten Utility-String aus
        System.out.println("-------------------------------\n");
    }

// ... (Rest der Klasse bleibt unverändert)2

    private void handleHumanTurn(String playerId) {
        boolean validMove = false;
        while (!validMove) {
            System.out.print("\n" + playerId + " (Mensch) - Geben Sie Ihren Zug ein (z.B. a1-b2,c3 oder c3-c4): ");
            String notation = scanner.nextLine().trim().toLowerCase();

            Move move = parseMoveNotation(notation);

            if (move != null) {
                if (isValidMoveAction(playerId, move)) {
                    executeMove(playerId, move);
                    validMove = true;
                }
            } else {
                System.out.println("Ungültiges Notationsformat. Bitte verwenden Sie z.B. a1-b2,c3 oder c3-c4.");
            }
        }
    }

    private void executeMove(String playerId, Move move) {
        // 1. Bewegung ausführen
        board.moveWorker(playerId, move.getMoveFrom(), move.getMoveTo());

        // 2. Gewinn prüfen
        if (board.checkWin(move.getMoveTo())) {
            gameOver = true;
            winnerId = playerId;
            System.out.println("\n!!! " + playerId + " GEWINNT durch Erreichen von Level 3. !!!");
            return;
        }

        // 3. Bauen ausführen
        board.buildStructure(move.getBuildAt());

        // Notationsprotokoll (Anforderung 1d)
        System.out.println("Zug ausgeführt: " + formatMoveNotation(move));
    }

    private boolean checkBlockade() {
        String currentPlayerId = playerIds.get(currentPlayerIndex);
        ReflexAgent agent = agents.get(currentPlayerId);

        if (agent != null) {
            List<Move> possibleMoves = agent.getAllPossibleMoves(board);
            if (possibleMoves.isEmpty()) {
                gameOver = true;
                winnerId = playerIds.get((currentPlayerIndex - 1 + playerIds.size()) % playerIds.size());
                System.out.println("\n!!! " + currentPlayerId + " ist blockiert und kann nicht ziehen. " + winnerId + " gewinnt. !!!");
                return true;
            }
        }
        return false;
    }

    //  Validierung und Parsing

    private boolean isValidMoveAction(String playerId, Move move) {
        int[] moveFrom = move.getMoveFrom();
        int[] moveTo = move.getMoveTo();
        int[] buildAt = move.getBuildAt();

        // 1. Arbeiter muss dem Spieler gehören
        if (!playerId.equals(board.getWorkerIdAt(moveFrom[0], moveFrom[1]))) {
            System.out.println("Fehler: Auf " + coordToNotation(moveFrom) + " steht kein Arbeiter von Ihnen (" + playerId + ").");
            return false;
        }

        // 2. Ziel muss ein gültiges Bewegungsziel sein
        boolean isValidMoveTarget = board.getValidMoveTargets(moveFrom).stream()
                .anyMatch(coord -> Arrays.equals(coord, moveTo));

        if (!isValidMoveTarget) {
            System.out.println("Fehler: " + coordToNotation(moveTo) + " ist kein gültiges Zielfeld für die Bewegung.");
            return false;
        }

        // 3. Gewinnzug: Muss kein Baufeld haben
        if (board.checkWin(moveTo)) {
            if (buildAt != null) {
                System.out.println("Fehler: Bei einem Gewinnzug darf nicht gebaut werden.");
                return false;
            }
            return true;
        }

        // 4. Normalzug: Muss ein Baufeld haben
        if (buildAt == null) {
            System.out.println("Fehler: Nach einem Nicht-Gewinnzug muss gebaut werden.");
            return false;
        }

        // 5. Ist das Baufeld ein Nachbar des NEUEN Standorts (moveTo)?
        boolean isNeighborOfMoveTo = board.getNeighbors(moveTo).stream()
                .anyMatch(coord -> Arrays.equals(coord, buildAt));

        if (!isNeighborOfMoveTo) {
            System.out.println("Fehler: " + coordToNotation(buildAt) + " ist kein Nachbarfeld von " + coordToNotation(moveTo) + ".");
            return false;
        }

        // 6. Darf das Baufeld bebaut werden (keine Kuppel)?
        if (board.isDomed(buildAt[0], buildAt[1])) {
            System.out.println("Fehler: Auf " + coordToNotation(buildAt) + " steht bereits eine Kuppel.");
            return false;
        }

        // 7. Darf das Baufeld nicht durch EINEN ANDEREN Arbeiter besetzt sein?
        String workerAtBuild = board.getWorkerIdAt(buildAt[0], buildAt[1]);

        // Wenn das Baufeld besetzt ist UND es NICHT das moveFrom-Feld ist (wo der eigene Arbeiter VOR der Bewegung steht),
        // ist der Zug ungültig.
        if (workerAtBuild != null && !Arrays.equals(buildAt, moveFrom)) {
            // Wenn der Arbeiter, der dort steht, nicht unser eigener ist, ist es ungültig.
            if (!playerId.equals(workerAtBuild)) {
                System.out.println("Fehler: " + coordToNotation(buildAt) + " ist bereits durch einen gegnerischen Arbeiter besetzt.");
                return false;
            }
            // Wenn der Arbeiter der eigene ist, kann es nur das moveFrom-Feld sein (da sonst der moveTo-Zug ungültig wäre).

            // Wenn es besetzt ist und es nicht das Feld ist, ist es ungültig.
            System.out.println("Fehler: " + coordToNotation(buildAt) + " ist bereits besetzt.");
            return false;
        }

        return true;
    }

    private Move parseMoveNotation(String notation) {
        String movePart, buildPart = null;
        int[] moveFrom, moveTo, buildAt = null;

        if (notation.contains(",")) {
            // Normalzug: move-build (z.B. a1-a2,b2)
            String[] parts = notation.split(",");
            if (parts.length != 2) return null;
            movePart = parts[0];
            buildPart = parts[1];
            buildAt = notationToCoord(buildPart);
        } else if (notation.contains("-")) {
            // Gewinnzug oder Nur-Bewegung (z.B. c3-c4)
            movePart = notation;
        } else {
            return null;
        }

        String[] moveCoords = movePart.split("-");
        if (moveCoords.length != 2) return null;

        moveFrom = notationToCoord(moveCoords[0]);
        moveTo = notationToCoord(moveCoords[1]);

        if (moveFrom == null || moveTo == null) return null;

        if (buildAt != null) {
            return new Move(moveFrom, moveTo, buildAt);
        } else {
            return new Move(moveFrom, moveTo);
        }
    }

    private String formatMoveNotation(Move move) {
        String fromNot = coordToNotation(move.getMoveFrom());
        String toNot = coordToNotation(move.getMoveTo());

        if (move.getBuildAt() == null) {
            return fromNot + "-" + toNot;
        } else {
            String buildNot = coordToNotation(move.getBuildAt());
            return fromNot + "-" + toNot + "," + buildNot;
        }
    }

    //  Notationshelfer

    private int[] notationToCoord(String notation) {
        if (notation == null || notation.length() != 2) return null;
        char colChar = notation.charAt(0);
        char rowChar = notation.charAt(1);

        if (colChar < 'a' || colChar > 'e' || rowChar < '1' || rowChar > '5') return null;

        int col = colChar - 'a';
        int row = rowChar - '1';
        return new int[]{col, row};
    }

    private String coordToNotation(int[] coord) {
        if (coord == null || !board.isValidCoord(coord[0], coord[1])) return "N/A";
        char colChar = (char) ('a' + coord[0]);
        char rowChar = (char) ('1' + coord[1]);
        return String.valueOf(colChar) + rowChar;
    }

    private String coordToNotation(int col, int row) {
        return coordToNotation(new int[]{col, row});
    }

    //  Hauptmethode zum Starten des Spiels

    public static void main(String[] args) {
        Scanner sharedScanner = new Scanner(System.in);
        int numHumans = 0;

        // Anzahl menschlicher Spieler auswählen
        while (numHumans != 1 && numHumans != 2) {
            System.out.print("Möchten Sie mit 1 oder 2 menschlichen Mitspielern spielen (insgesamt also 2 oder 3 Spieler mit KI)? Geben Sie 1 oder 2 ein: ");
            if (sharedScanner.hasNextInt()) {
                numHumans = sharedScanner.nextInt();
                sharedScanner.nextLine(); // newline entfernen
                if (numHumans != 1 && numHumans != 2) {
                    System.out.println("Ungültige Eingabe. Bitte 1 oder 2 eingeben.");
                }
            } else {
                System.out.println("Ungültige Eingabe. Bitte eine Zahl eingeben.");
                sharedScanner.nextLine(); // ungültige Eingabe entfernen
            }
        }

        // Immer eine KI zusätzlich
        int totalPlayers = numHumans + 1;

        SantoriniGame game = new SantoriniGame(totalPlayers, sharedScanner);
        game.run();
    }

}
