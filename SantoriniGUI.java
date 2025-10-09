import processing.core.PApplet;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Grafische Benutzeroberfläche (GUI) für Santorini unter Verwendung der Processing-Bibliothek (PApplet).
 * Erlaubt das Spielen über Mausklicks und visualisiert die Züge des Reflex Agenten.
 */
public class SantoriniGUI extends PApplet {

    // --- GUI-Konstanten ---
    private static final int WINDOW_SIZE = 800;
    private static final int BOARD_OFFSET = 50;
    private static final int CELL_SIZE = (WINDOW_SIZE - 2 * BOARD_OFFSET) / Board.BOARD_SIZE;

    // --- Spiel-Status ---
    private Board board;
    private Map<String, ReflexAgent> agents;
    private List<String> playerIds;
    private int currentPlayerIndex;
    private String currentPlayerId;
    private boolean gameOver = false;
    private boolean placingWorkers = true;
    private String winnerId = null;
    private int placedWorkersCount = 0;
    private int workersToPlace = 2; // Zwei Arbeiter pro Spieler
    private int numOpponents = 1; // Standard: 2 Spieler

    // --- Interaktions-Status ---
    private int[] selectedWorkerCoord = null; // [col, row]
    private int[] firstClickCoord = null; // Speichert das erste Feld, das angeklickt wurde
    private GamePhase phase = GamePhase.MOVE_WORKER;
    private boolean aiThinking = false;
    private String moveEvaluation = "Willkommen bei Santorini! Wählen Sie die Spieleranzahl.";

    private String logMessage = "";

    private enum GamePhase {
        MOVE_WORKER,
        CHOOSE_BUILD_TARGET,
        WAIT_FOR_AI,
        SETUP_OPPONENTS
    }

    // --- Farben ---
    private int C_BACKGROUND;
    private int C_GRID;
    private int C_P1;
    private int C_P2;
    private int C_P3;
    private int C_MOVE_TARGET;
    private int C_BUILD_TARGET;
    private int C_SELECTED;
    private int C_LEVEL_1;
    private int C_LEVEL_2;
    private int C_LEVEL_3;
    private int C_DOME;


    @Override
    public void settings() {
        size(WINDOW_SIZE + 300, WINDOW_SIZE + 50); // Zusätzlicher Platz für UI-Elemente
    }

    @Override
    public void setup() {
        // Farben initialisieren
        C_BACKGROUND = color(245, 245, 245);
        C_GRID = color(100, 100, 100);
        C_P1 = color(220, 50, 50); // Rot
        C_P2 = color(50, 50, 220); // Blau
        C_P3 = color(50, 180, 50); // Grün
        C_MOVE_TARGET = color(150, 150, 255, 150); // Helles Blau
        C_BUILD_TARGET = color(255, 180, 100, 150); // Helles Orange
        C_SELECTED = color(255, 255, 0, 150);
        C_LEVEL_1 = color(200, 200, 200);
        C_LEVEL_2 = color(150, 150, 150);
        C_LEVEL_3 = color(100, 100, 100);
        C_DOME = color(50, 50, 50);

        textAlign(CENTER, CENTER);
        textSize(16);

        // Initialer Setup-Modus
        phase = GamePhase.SETUP_OPPONENTS;
    }

    /**
     * Startet das Spiel mit der gewählten Anzahl von KI-Gegnern.
     */
    public void initializeGame(int opponents) {
        this.numOpponents = opponents;
        this.playerIds = new ArrayList<>();
        this.playerIds.add("P1");
        for (int i = 1; i <= opponents; i++) {
            this.playerIds.add("P" + (i + 1));
        }

        this.board = new Board(playerIds);
        this.agents = new HashMap<>();

        // KI-Agenten initialisieren
        for (int i = 1; i < playerIds.size(); i++) {
            String pid = playerIds.get(i);
            // HIER wird der ReflexAgent verwendet. Später hier UtilityAgent einfügen.
            agents.put(pid, new ReflexAgent(pid));
        }

        this.currentPlayerIndex = 0;
        this.currentPlayerId = playerIds.get(currentPlayerIndex);
        this.placingWorkers = true;
        this.phase = GamePhase.MOVE_WORKER; // Wechselt direkt zur Platzierung
        this.moveEvaluation = "Platzierung P1. Klicke 2 freie Felder.";
    }

    @Override
    public void draw() {
        background(C_BACKGROUND);

        if (board == null) {
            drawSetupScreen();
            return;
        }

        drawBoard();
        drawLevels();
        drawWorkers();
        drawInteractiveElements();
        drawUI();

        if (gameOver) {
            drawGameOverScreen();
        } else if (aiThinking) {
            runAiTurn();
        }
    }

    private void drawSetupScreen() {
        fill(0);
        textSize(24);
        text("Willkommen bei Santorini!", WINDOW_SIZE / 2, WINDOW_SIZE / 2 - 100);
        textSize(18);
        text("Wählen Sie die Anzahl der KI-Gegner:", WINDOW_SIZE / 2, WINDOW_SIZE / 2 - 40);

        // Button 1 Gegner
        drawButton(WINDOW_SIZE / 2 - 100, WINDOW_SIZE / 2, 80, 40, "1 Gegner (2 Spieler)", 1);

        // Button 2 Gegner
        drawButton(WINDOW_SIZE / 2 + 50, WINDOW_SIZE / 2, 80, 40, "2 Gegner (3 Spieler)", 2);
    }

    private void drawButton(int x, int y, int w, int h, String label, int value) {
        if (mouseX > x - w / 2 && mouseX < x + w / 2 && mouseY > y - h / 2 && mouseY < y + h / 2) {
            fill(C_P1);
        } else {
            fill(C_P2);
        }
        rect(x, y, w, h, 8);

        fill(255);
        textSize(16);
        text(label, x, y);
    }

    private void drawBoard() {
        noStroke();
        fill(200, 230, 255);
        rect(BOARD_OFFSET, BOARD_OFFSET, Board.BOARD_SIZE * CELL_SIZE, Board.BOARD_SIZE * CELL_SIZE);

        stroke(C_GRID);
        strokeWeight(2);
        for (int c = 0; c <= Board.BOARD_SIZE; c++) {
            line(BOARD_OFFSET + c * CELL_SIZE, BOARD_OFFSET, BOARD_OFFSET + c * CELL_SIZE, BOARD_OFFSET + Board.BOARD_SIZE * CELL_SIZE);
        }
        for (int r = 0; r <= Board.BOARD_SIZE; r++) {
            line(BOARD_OFFSET, BOARD_OFFSET + r * CELL_SIZE, BOARD_OFFSET + Board.BOARD_SIZE * CELL_SIZE, BOARD_OFFSET + r * CELL_SIZE);
        }
    }

    private void drawLevels() {
        for (int c = 0; c < Board.BOARD_SIZE; c++) {
            for (int r = 0; r < Board.BOARD_SIZE; r++) {
                int level = board.getLevel(c, r);
                if (level > 0) {
                    drawStructure(c, r, level);
                }
            }
        }
    }

    private void drawStructure(int col, int row, int level) {
        int x = BOARD_OFFSET + col * CELL_SIZE;
        int y = WINDOW_SIZE - BOARD_OFFSET - (row + 1) * CELL_SIZE;
        int w = CELL_SIZE;
        int h = CELL_SIZE;

        // Visualisiere die 3D-Struktur (vereinfacht)
        for (int l = 1; l <= level; l++) {
            int cx = x + w / 2;
            int cy = y + h / 2;
            int s = (int) (w * (1.0 - 0.1 * (l - 1)));

            // Wähle Farbe basierend auf Level
            if (l == 1) fill(C_LEVEL_1);
            else if (l == 2) fill(C_LEVEL_2);
            else if (l == 3) fill(C_LEVEL_3);
            else if (l == Board.MAX_LEVEL) fill(C_DOME);

            // Kuppel oder Level
            if (l == Board.MAX_LEVEL) {
                ellipse(cx, cy, s, s);
                fill(255);
                textSize(14);
                text("D", cx, cy);
            } else {
                rectMode(CORNER);
                // Rechtecke von unten nach oben zeichnen
                noStroke();
                float offset = (float) (w - s) / 2;
                rect(x + offset, y + offset, s, s, 5);
            }
        }
    }

    private void drawWorkers() {
        for (String pid : playerIds) {
            int color = getColorForPlayer(pid);
            for (Worker worker : board.getWorkersByPlayer(pid)) {
                int c = worker.getCoord()[0];
                int r = worker.getCoord()[1];

                int x = BOARD_OFFSET + c * CELL_SIZE + CELL_SIZE / 2;
                int y = WINDOW_SIZE - BOARD_OFFSET - r * CELL_SIZE - CELL_SIZE / 2;

                // Arbeiter zeichnen (Kreis)
                fill(color);
                ellipse(x, y, CELL_SIZE * 0.4f, CELL_SIZE * 0.4f);

                // Arbeiter-ID zeichnen
                fill(255);
                textSize(16);
                text(pid.substring(1) + worker.getWorkerId(), x, y);
            }
        }
    }

    private int getColorForPlayer(String playerId) {
        switch (playerId) {
            case "P1": return C_P1;
            case "P2": return C_P2;
            case "P3": return C_P3;
            default: return color(0);
        }
    }

    private void drawInteractiveElements() {
        if (gameOver || placingWorkers || phase == GamePhase.WAIT_FOR_AI || agents.containsKey(currentPlayerId)) return;

        int[] mouseCoord = getCoordFromMouse();

        // 1. Hervorhebung des ausgewählten Arbeiters
        if (selectedWorkerCoord != null) {
            highlightCell(selectedWorkerCoord[0], selectedWorkerCoord[1], C_SELECTED);
        }

        // 2. Hervorhebung gültiger Ziele
        if (selectedWorkerCoord != null && phase == GamePhase.MOVE_WORKER) {
            List<int[]> moveTargets = board.getValidMoveTargets(selectedWorkerCoord);
            for (int[] target : moveTargets) {
                highlightCell(target[0], target[1], C_MOVE_TARGET);
            }
        }

        if (firstClickCoord != null && phase == GamePhase.CHOOSE_BUILD_TARGET) {
            List<int[]> buildTargets = board.getValidBuildTargets(firstClickCoord);
            for (int[] target : buildTargets) {
                highlightCell(target[0], target[1], C_BUILD_TARGET);
            }
        }

        // 3. Hover-Hervorhebung
        if (mouseCoord != null && !isOccupiedByMe(mouseCoord)) {
            highlightCell(mouseCoord[0], mouseCoord[1], color(255, 255, 255, 100));
        }
    }

    private void highlightCell(int c, int r, int highlightColor) {
        int x = BOARD_OFFSET + c * CELL_SIZE;
        int y = WINDOW_SIZE - BOARD_OFFSET - (r + 1) * CELL_SIZE;

        noStroke();
        fill(highlightColor);
        rectMode(CORNER);
        rect(x, y, CELL_SIZE, CELL_SIZE);
    }

    private void drawUI() {
        fill(0);
        textSize(20);
        text("Santorini Spiel", WINDOW_SIZE + BOARD_OFFSET + 100, 30);

        textSize(18);
        text("Am Zug: " + currentPlayerId + (agents.containsKey(currentPlayerId) ? " (KI)" : " (Mensch)"),
                WINDOW_SIZE + BOARD_OFFSET + 100, 80);

        // Zugbewertung
        fill(255);
        stroke(0);
        rect(WINDOW_SIZE + BOARD_OFFSET, 120, 200, 100, 5);

        fill(0);
        textSize(14);
        textAlign(LEFT, TOP);
        text("Zugbewertung/Nachricht:", WINDOW_SIZE + BOARD_OFFSET + 5, 125);
        textSize(12);
        text(moveEvaluation, WINDOW_SIZE + BOARD_OFFSET + 5, 145, 190, 70);

        // Spiel-Logbuch
        fill(255);
        stroke(0);
        rect(WINDOW_SIZE + BOARD_OFFSET, 250, 200, 500, 5);
        fill(0);
        textSize(14);
        textAlign(LEFT, TOP);
        text("Spiel-Logbuch (Notation):", WINDOW_SIZE + BOARD_OFFSET + 5, 255);
        textSize(12);
        text(logMessage, WINDOW_SIZE + BOARD_OFFSET + 5, 275, 190, 480);
    }

    private void drawGameOverScreen() {
        fill(0, 150);
        rect(0, 0, width, height);

        fill(255);
        textSize(48);
        text("SPIEL VORBEI", WINDOW_SIZE / 2, WINDOW_SIZE / 2 - 50);

        textSize(32);
        text("Gewinner: " + winnerId, WINDOW_SIZE / 2, WINDOW_SIZE / 2 + 20);
    }

    // --- Maus- und Klick-Logik ---

    @Override
    public void mouseClicked() {
        if (gameOver) return;

        // --- SETUP-SCREEN KLICK ---
        if (board == null) {
            int x1 = WINDOW_SIZE / 2 - 100;
            int y1 = WINDOW_SIZE / 2;
            int w = 80;
            int h = 40;

            // 1 Gegner Button
            if (mouseX > x1 - w / 2 && mouseX < x1 + w / 2 && mouseY > y1 - h / 2 && mouseY < y1 + h / 2) {
                initializeGame(1);
            }
            // 2 Gegner Button
            int x2 = WINDOW_SIZE / 2 + 50;
            if (mouseX > x2 - w / 2 && mouseX < x2 + w / 2 && mouseY > y1 - h / 2 && mouseY < y1 + h / 2) {
                initializeGame(2);
            }
            return;
        }


        int[] coord = getCoordFromMouse();
        if (coord == null) return;
        int c = coord[0];
        int r = coord[1];

        if (placingWorkers) {
            handlePlacementClick(c, r);
        } else if (agents.containsKey(currentPlayerId)) {
            // KI-Runde, Klick ignorieren
        } else {
            // Menschliche Runde
            handleGameClick(c, r);
        }
    }

    private void handlePlacementClick(int c, int r) {
        if (!board.isOccupied(c, r)) {
            board.placeWorker(currentPlayerId, placedWorkersCount + 1, c, r);
            logMessage += "\n" + currentPlayerId + " platziert Arbeiter " + (placedWorkersCount + 1) + ": " + coordToNotation(c, r);
            placedWorkersCount++;

            if (placedWorkersCount == workersToPlace) {
                // Nächster Spieler ist dran
                currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
                currentPlayerId = playerIds.get(currentPlayerIndex);
                placedWorkersCount = 0;

                if (currentPlayerId.equals("P1")) {
                    placingWorkers = false; // Platzierung beendet
                    moveEvaluation = "Start P1. Wähle einen Arbeiter.";
                    phase = GamePhase.MOVE_WORKER;
                } else {
                    // KI-Platzierung (wird hier nur visuell angezeigt, Logik ist in runAiPlacement)
                    runAiPlacement();
                }
            } else {
                moveEvaluation = "Platzierung " + currentPlayerId + ". Klicke noch " + (workersToPlace - placedWorkersCount) + " freie Felder.";
            }
        }
    }

    private void runAiPlacement() {
        // KI-Platzierung muss synchron erfolgen, damit das UI nicht blockiert
        String aiId = currentPlayerId;
        for (int i = 1; i <= workersToPlace; i++) {
            Random random = new Random();
            int col, row;
            do {
                col = random.nextInt(Board.BOARD_SIZE);
                row = random.nextInt(Board.BOARD_SIZE);
            } while (board.isOccupied(col, row));

            board.placeWorker(aiId, i, col, row);
            logMessage += "\n" + aiId + " platziert Arbeiter " + i + ": " + coordToNotation(col, row);
        }

        // Zum nächsten Spieler wechseln
        currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        currentPlayerId = playerIds.get(currentPlayerIndex);

        if (currentPlayerId.equals("P1")) {
            placingWorkers = false;
            moveEvaluation = "Start P1. Wähle einen Arbeiter.";
            phase = GamePhase.MOVE_WORKER;
        }
    }

    private void handleGameClick(int c, int r) {
        int[] clickedCoord = new int[]{c, r};

        if (phase == GamePhase.MOVE_WORKER) {
            String workerIdAt = board.getWorkerIdAt(c, r);

            if (workerIdAt != null && workerIdAt.equals(currentPlayerId)) {
                // 1. Klick auf eigenen Arbeiter: Auswählen
                selectedWorkerCoord = clickedCoord;
                moveEvaluation = "Arbeiter gewählt. Klicke Bewegungsziel (blau).";
                firstClickCoord = null; // Reset für den nächsten Zug

            } else if (selectedWorkerCoord != null) {
                // 2. Klick auf Bewegungsziel (Blau)
                List<int[]> moveTargets = board.getValidMoveTargets(selectedWorkerCoord);

                if (targetsContain(moveTargets, clickedCoord)) {
                    // WINNING MOVE CHECK
                    if (board.checkWin(clickedCoord)) {
                        Move move = new Move(selectedWorkerCoord, clickedCoord);
                        executeMove(currentPlayerId, move);
                        return; // Spiel beendet oder KI ist dran
                    }

                    // Normaler Zug: Bewegung speichern und zur Bau-Phase wechseln
                    firstClickCoord = selectedWorkerCoord; // Ursprünglicher Standort
                    selectedWorkerCoord = clickedCoord; // Neuer Standort
                    phase = GamePhase.CHOOSE_BUILD_TARGET;
                    moveEvaluation = "Bewegt nach " + coordToNotation(clickedCoord) + ". Klicke Bauziel (orange).";
                } else {
                    // Ungültiges Ziel
                    moveEvaluation = "Ungültiger Zug. Wähle ein gültiges Bewegungsziel (blau) oder einen anderen Arbeiter.";
                }
            } else {
                moveEvaluation = "Bitte wähle zuerst einen deiner Arbeiter.";
            }

        } else if (phase == GamePhase.CHOOSE_BUILD_TARGET) {
            // 3. Klick auf Bauziel (Orange)
            List<int[]> buildTargets = board.getValidBuildTargets(selectedWorkerCoord);

            // Wenn der Bau auf das gerade geräumte Feld (firstClickCoord) erfolgen soll, 
            // muss die Validierung sicherstellen, dass firstClickCoord auch in buildTargets enthalten ist.
            // Die KI-Validierung in Board.java ist korrekt, wenn wir annehmen, dass der Arbeiter bereits versetzt ist.

            boolean isValidBuild = targetsContain(buildTargets, clickedCoord);

            if (isValidBuild) {
                // Zug ausführen (Bewegung war bereits in selectedWorkerCoord gespeichert)
                Move move = new Move(firstClickCoord, selectedWorkerCoord, clickedCoord);
                executeMove(currentPlayerId, move);

                // Zug beendet, KI ist dran (oder P1 bleibt dran)
                selectedWorkerCoord = null;
                firstClickCoord = null;
                moveEvaluation = "Bauen beendet. Nächster Spieler (" + playerIds.get(currentPlayerIndex) + ") ist am Zug.";
                phase = GamePhase.MOVE_WORKER;

                // KI-Runde vorbereiten
                if (agents.containsKey(currentPlayerId)) {
                    aiThinking = true;
                    phase = GamePhase.WAIT_FOR_AI;
                }
            } else {
                moveEvaluation = "Ungültiges Bauziel. Wähle ein gültiges Feld (orange).";
            }
        }
    }

    private void executeMove(String playerId, Move move) {
        String notation = formatMoveNotation(move);
        logMessage += "\n" + playerId + ": " + notation;

        // 1. Bewegung ausführen
        board.moveWorker(playerId, move.getMoveFrom(), move.getMoveTo());

        // 2. Gewinn prüfen
        if (board.checkWin(move.getMoveTo())) {
            gameOver = true;
            winnerId = playerId;
            moveEvaluation = "GEWONNEN! " + playerId + " hat Level 3 erreicht.";
            return;
        }

        // 3. Bauen ausführen
        board.buildStructure(move.getBuildAt());

        // Zum nächsten Spieler wechseln
        currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        currentPlayerId = playerIds.get(currentPlayerIndex);

        // KI-Runde vorbereiten
        if (agents.containsKey(currentPlayerId) && !gameOver) {
            aiThinking = true;
            phase = GamePhase.WAIT_FOR_AI;
        } else if (!gameOver) {
            moveEvaluation = "Nächster Zug P1. Wähle einen Arbeiter.";
            phase = GamePhase.MOVE_WORKER;
        }
    }

    private void runAiTurn() {
        // KI-Logik ausführen (synchron für die Darstellung)
        ReflexAgent agent = agents.get(currentPlayerId);

        // Kurze Pause simulieren
        try {
            TimeUnit.MILLISECONDS.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ReflexAgent.MoveEvaluation evaluation = agent.chooseMove(board);
        Move move = evaluation.move;

        if (move == null) {
            gameOver = true;
            winnerId = playerIds.get((currentPlayerIndex - 1 + playerIds.size()) % playerIds.size());
            moveEvaluation = currentPlayerId + " ist blockiert und verliert.";
            aiThinking = false;
            return;
        }

        // Zug anzeigen und ausführen
        moveEvaluation = evaluation.evaluation;

        aiThinking = false;

        // Führe den Zug aus
        executeMove(currentPlayerId, move);

        // Wenn nach dem KI-Zug P1 am Zug ist, wechsel zur menschlichen Phase
        if (currentPlayerId.equals("P1") && !gameOver) {
            moveEvaluation = "Dein Zug! Wähle einen Arbeiter.";
            phase = GamePhase.MOVE_WORKER;
        } else if (agents.containsKey(currentPlayerId) && !gameOver) {
            // Nächste KI-Runde direkt starten
            aiThinking = true;
            phase = GamePhase.WAIT_FOR_AI;
        }
    }

    // --- Hilfsmethoden ---

    private int[] getCoordFromMouse() {
        if (mouseX < BOARD_OFFSET || mouseX > BOARD_OFFSET + Board.BOARD_SIZE * CELL_SIZE ||
                mouseY < BOARD_OFFSET || mouseY > BOARD_OFFSET + Board.BOARD_SIZE * CELL_SIZE) {
            return null;
        }

        int c = (mouseX - BOARD_OFFSET) / CELL_SIZE;
        // Invertiere die Y-Achse, da Processing (0,0) oben links hat, Santorini (0,0) unten links
        int r = Board.BOARD_SIZE - 1 - (mouseY - BOARD_OFFSET) / CELL_SIZE;

        return new int[]{c, r};
    }

    private boolean isOccupiedByMe(int[] coord) {
        String workerId = board.getWorkerIdAt(coord[0], coord[1]);
        return workerId != null && workerId.equals(currentPlayerId);
    }

    private boolean targetsContain(List<int[]> targets, int[] coord) {
        for (int[] target : targets) {
            if (target[0] == coord[0] && target[1] == coord[1]) {
                return true;
            }
        }
        return false;
    }
    /**
     * Konvertiert int-Array [col, row] zu Notation (z.B. "a1", "c5").
     * Diese Methode wird benötigt, da Move-Objekte int[] verwenden.
     */
    private String coordToNotation(int[] coord) {
        if (coord == null || !board.isValidCoord(coord[0], coord[1])) return "N/A";
        char colChar = (char) ('a' + coord[0]);
        char rowChar = (char) ('1' + coord[1]);
        return String.valueOf(colChar) + rowChar;
    }

    /**
     * Überladene Methode: Konvertiert zwei int-Werte (col, row) zu Notation.
     * (Die ursprünglich in SantoriniGUI implementierte Methode)
     */
    private String coordToNotation(int col, int row) {
        return coordToNotation(new int[]{col, row});
    }

    private String formatMoveNotation(Move move) {
        String fromNot = coordToNotation(move.getMoveFrom()[0], move.getMoveFrom()[1]);
        String toNot = coordToNotation(move.getMoveTo()[0], move.getMoveTo()[1]);

        if (move.getBuildAt() == null) {
            return fromNot + "-" + toNot;
        } else {
            String buildNot = coordToNotation(move.getBuildAt()[0], move.getBuildAt()[1]);
            return fromNot + "-" + toNot + "," + buildNot;
        }
    }

    // --- Main Startpunkt ---
    public static void main(String[] passedArgs) {
        String[] appletArgs = new String[] { "SantoriniGUI" };
        PApplet.main(appletArgs);
    }
}
