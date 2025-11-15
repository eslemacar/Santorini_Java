import processing.core.PApplet;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Santorini GUI (Processing) — überarbeitete Version mit korrekter Startspieler- und Platzierungs-Logik.
 * Benötigt: Board.java, Worker.java, Move.java, ReflexAgent.java
 */
public class SantoriniGUI extends PApplet {

    // --- GUI-Konstanten ---
    private static final int WINDOW_SIZE = 800;
    private static final int BOARD_OFFSET = 50;
    private static final int CELL_SIZE = (WINDOW_SIZE - 2 * BOARD_OFFSET) / Board.BOARD_SIZE;
    private static final int UI_PANEL_WIDTH = 300;

    // --- Spiel-Status ---
    private Board board;
    private Map<String, ReflexAgent> agents;
    private List<String> playerIds;
    private int currentPlayerIndex = 0;
    private String currentPlayerId = null;
    private String startPlayerId = null; // gewählter Startspieler
    private boolean gameOver = false;
    private boolean placingWorkers = true;
    private String winnerId = null;
    private int placedWorkersCount = 0; // zählt, wie viele Worker der aktuell menschliche Spieler bereits gesetzt hat
    private final int workersToPlace = 2; // Zwei Arbeiter pro Spieler

    // --- Interaktions-Status ---
    private int[] selectedWorkerCoord = null; // [col, row]
    private int[] moveFromCoord = null;
    private int[] moveToCoord = null;
    private GamePhase phase = GamePhase.SETUP_OPPONENTS;
    private boolean aiThinking = false;
    private String moveEvaluation = "Willkommen bei Santorini! Wähle Spieleranzahl.";

    private String logMessage = "";

    private enum GamePhase {
        MOVE_WORKER,
        CHOOSE_BUILD_TARGET,
        WAIT_FOR_AI,
        SETUP_OPPONENTS,
        SETUP_START_PLAYER
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
    private int C_LEVEL_1_L; // Level 1 (top)
    private int C_LEVEL_1_D; // Level 1 (side)
    private int C_LEVEL_2_L;
    private int C_LEVEL_3_L;
    private int C_DOME;

    @Override
    public void settings() {
        size(WINDOW_SIZE + UI_PANEL_WIDTH, WINDOW_SIZE + 50);
    }

    @Override
    public void setup() {
        // Farben initialisieren
        C_BACKGROUND = color(255, 240, 250);
        C_GRID = color(200, 150, 200);
        C_P1 = color(255, 105, 180);
        C_P2 = color(255, 182, 193);
        C_P3 = color(219, 112, 147);

        C_MOVE_TARGET = color(0, 200, 0, 100);
        C_BUILD_TARGET = color(255, 200, 0, 100);
        C_SELECTED = color(255, 192, 203, 180);

        C_LEVEL_1_L = color(255, 220, 255);
        C_LEVEL_1_D = color(220, 190, 220);
        C_LEVEL_2_L = color(255, 180, 230);
        C_LEVEL_3_L = color(255, 140, 210);
        C_DOME = color(180, 50, 120);

        textAlign(CENTER, CENTER);
        textSize(16);

        phase = GamePhase.SETUP_OPPONENTS;
        logMessage = "Starte Spiel...";
    }

    /**
     * Initialisiert Spielstruktur (Spielerliste + KI-Objekte) — ruft danach Setup-Phase für Startspielerwahl auf.
     */
    public void setupGameStructure(int opponents) {
        this.playerIds = new ArrayList<>();
        this.agents = new HashMap<>();
        this.playerIds.add("P1");

        if (opponents == 1) {
            this.playerIds.add("P2");
            agents.put("P2", new ReflexAgent("P2"));
        } else if (opponents == 2) {
            this.playerIds.add("P2");
            this.playerIds.add("P3");
            agents.put("P3", new ReflexAgent("P3"));
        }

        this.board = new Board(playerIds);
        this.phase = GamePhase.SETUP_START_PLAYER;
        this.moveEvaluation = "Wähle den Startspieler für die Platzierungsphase.";
        this.logMessage = "Spielmodus gewählt (" + playerIds.size() + " Spieler).";
    }

    /**
     * Startet Platzierungsphase mit dem gewählten Startspieler.
     */
    public void initializePlacement(String startingPlayerId) {
        this.startPlayerId = startingPlayerId;
        this.currentPlayerId = startingPlayerId;
        this.currentPlayerIndex = playerIds.indexOf(startingPlayerId);

        this.placingWorkers = true;
        this.phase = GamePhase.MOVE_WORKER;
        this.moveEvaluation = "Platzierung " + currentPlayerId + ". Klicke " + workersToPlace + " freie Felder.";
        this.logMessage += "\nStartspieler: " + startingPlayerId;

        // Wenn Startspieler KI ist, platziere automatisiert (und ggf. weitere KIs)
        if (agents.containsKey(currentPlayerId)) {
            runAiPlacementLoop();
        } else {
            // mensch beginnt Platzierung: ensure placedWorkersCount reset
            placedWorkersCount = 0;
        }
    }

    @Override
    public void draw() {
        background(C_BACKGROUND);

        // Setup-Screens
        if (phase == GamePhase.SETUP_OPPONENTS || phase == GamePhase.SETUP_START_PLAYER) {
            drawSetupScreen();
            return;
        }

        // Haupt-Rendering
        drawBoard();
        drawLevels();
        drawBoardLabels();
        drawWorkers();
        drawInteractiveElements();
        drawUI();

        if (gameOver) {
            drawGameOverScreen();
        } else if (aiThinking) {
            // KI-Zug synchron ausführen (Processing-thread-sensibel)
            runAiTurn();
        }
    }

    private void drawSetupScreen() {
        textAlign(CENTER, CENTER);
        fill(0);
        textSize(24);
        text("Willkommen bei Santorini!", WINDOW_SIZE / 2, WINDOW_SIZE / 2 - 100);
        textSize(18);

        if (phase == GamePhase.SETUP_OPPONENTS) {
            text("Wähle Spielmodus:", WINDOW_SIZE / 2, WINDOW_SIZE / 2 - 40);
            drawButton(WINDOW_SIZE / 2 - 120, WINDOW_SIZE / 2, 200, 40, "1 Mensch vs. 1 KI", 1);
            drawButton(WINDOW_SIZE / 2 + 120, WINDOW_SIZE / 2, 200, 40, "2 Menschen vs. 1 KI", 2);
        } else if (phase == GamePhase.SETUP_START_PLAYER) {
            text("Wähle den Startspieler:", WINDOW_SIZE / 2, WINDOW_SIZE / 2 - 40);
            int offset = 120;
            int buttonWidth = 120;
            for (int i = 0; i < playerIds.size(); i++) {
                String pid = playerIds.get(i);
                int x = WINDOW_SIZE / 2 - offset * (playerIds.size() - 1) / 2 + offset * i;
                drawButton(x, WINDOW_SIZE / 2, buttonWidth, 40, pid + (agents.containsKey(pid) ? " (KI)" : " (Mensch)"), pid);
            }
        }
    }

    private void drawButton(int x, int y, int w, int h, String label, Object value) {
        if (mouseX > x - w / 2 && mouseX < x + w / 2 && mouseY > y - h / 2 && mouseY < y + h / 2) {
            fill(C_P1);
        } else {
            fill(C_P2);
        }
        rectMode(CENTER);
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

    private void drawBoardLabels() {
        fill(120);
        textSize(16);
        textAlign(CENTER, CENTER);
        for (int c = 0; c < Board.BOARD_SIZE; c++) {
            char colLabel = (char) ('a' + c);
            int x = BOARD_OFFSET + c * CELL_SIZE + CELL_SIZE / 2;
            int y = BOARD_OFFSET - 20;
            text(String.valueOf(colLabel), x, y);
        }
        for (int r = 0; r < Board.BOARD_SIZE; r++) {
            int rowLabel = Board.BOARD_SIZE - r;
            int x = BOARD_OFFSET - 20;
            int y = WINDOW_SIZE - BOARD_OFFSET - r * CELL_SIZE - CELL_SIZE / 2;
            text(String.valueOf(rowLabel), x, y);
        }
    }

    private void drawLevels() {
        for (int c = 0; c < Board.BOARD_SIZE; c++) {
            for (int r = 0; r < Board.BOARD_SIZE; r++) {
                int level = board.getLevel(c, r);
                if (level > 0) drawStructure(c, r, level);
            }
        }
    }

    private void drawStructure(int col, int row, int level) {
        int x = BOARD_OFFSET + col * CELL_SIZE;
        int y = WINDOW_SIZE - BOARD_OFFSET - (row + 1) * CELL_SIZE;
        int w = CELL_SIZE;
        for (int l = 1; l <= level; l++) {
            int s = (int) (w * (1.0 - 0.1 * (l - 1)));
            float offset = (w - s) / 2.0f;
            noStroke();
            rectMode(CORNER);
            if (l < Board.MAX_LEVEL) {
                int sideColor = (l == 1) ? C_LEVEL_1_D : (l == 2) ? color(red(C_LEVEL_2_L) * 0.8f, green(C_LEVEL_2_L) * 0.8f, blue(C_LEVEL_2_L) * 0.8f) : color(red(C_LEVEL_3_L) * 0.8f, green(C_LEVEL_3_L) * 0.8f, blue(C_LEVEL_3_L) * 0.8f);
                fill(sideColor);
                rect(x + offset + 1, y + offset + 3, s - 2, s - 2, 4);
                int topColor = (l == 1) ? C_LEVEL_1_L : (l == 2) ? C_LEVEL_2_L : C_LEVEL_3_L;
                fill(topColor);
                rect(x + offset, y + offset, s - 2, s - 2, 4);
            } else {
                int cx = x + w / 2;
                int cy = y + w / 2;
                fill(C_DOME);
                ellipse(cx, cy, s, s);
                fill(255);
                textSize(14);
                text("D", cx, cy);
            }
        }
    }

    private void drawWorkers() {
        for (String pid : playerIds) {
            int color = getColorForPlayer(pid);
            List<Worker> list = board.getWorkersByPlayer(pid);
            if (list == null) continue;
            for (Worker worker : list) {
                int c = worker.getCoord()[0];
                int r = worker.getCoord()[1];
                int x = BOARD_OFFSET + c * CELL_SIZE + CELL_SIZE / 2;
                int y = WINDOW_SIZE - BOARD_OFFSET - r * CELL_SIZE - CELL_SIZE / 2;
                fill(color);
                stroke(255);
                strokeWeight(2);
                ellipse(x, y, CELL_SIZE * 0.4f, CELL_SIZE * 0.4f);
                fill(255);
                noStroke();
                textSize(16);
                text(String.valueOf(worker.getWorkerId()), x, y);
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

        if (selectedWorkerCoord != null && phase == GamePhase.MOVE_WORKER) {
            List<int[]> moveTargets = board.getValidMoveTargets(selectedWorkerCoord);
            for (int[] target : moveTargets) highlightCell(target[0], target[1], C_MOVE_TARGET, color(0,200,0), 4);
        }

        if (moveToCoord != null && phase == GamePhase.CHOOSE_BUILD_TARGET) {
            List<int[]> buildTargets = board.getValidBuildTargets(moveToCoord);
            for (int[] target : buildTargets) {
                boolean valid = isBuildTargetValid(moveToCoord, moveFromCoord, target);
                if (valid) highlightCell(target[0], target[1], C_BUILD_TARGET, color(255,200,0), 4);
            }
            if (moveFromCoord != null && isBuildTargetValid(moveToCoord, moveFromCoord, moveFromCoord)) {
                highlightCell(moveFromCoord[0], moveFromCoord[1], C_BUILD_TARGET, color(255,200,0), 4);
            }
        }

        if (selectedWorkerCoord != null) {
            highlightCell(selectedWorkerCoord[0], selectedWorkerCoord[1], C_SELECTED, color(255), 4);
        }
    }

    private void highlightCell(int c, int r, int fillColor, int strokeColor, int weight) {
        int x = BOARD_OFFSET + c * CELL_SIZE;
        int y = WINDOW_SIZE - BOARD_OFFSET - (r + 1) * CELL_SIZE;
        noStroke();
        fill(fillColor);
        rectMode(CORNER);
        rect(x, y, CELL_SIZE, CELL_SIZE);
        stroke(strokeColor);
        strokeWeight(weight);
        noFill();
        rect(x, y, CELL_SIZE, CELL_SIZE);
    }

    private void drawUI() {
        int uiX = WINDOW_SIZE + BOARD_OFFSET;
        fill(0);
        textSize(20);
        textAlign(CENTER, CENTER);
        text("Santorini Spiel", uiX + 100, 30);
        textSize(18);
        text("Am Zug: " + (currentPlayerId == null ? "—" : currentPlayerId + (agents.containsKey(currentPlayerId) ? " (KI)" : " (Mensch)")), uiX + 100, 80);

        // Bewertung
        fill(255);
        stroke(0);
        rectMode(CORNER);
        rect(uiX, 120, 200, 100, 5);
        fill(0);
        textSize(14);
        textAlign(LEFT, TOP);
        text("Zugbewertung/Nachricht:", uiX + 5, 125);
        textSize(12);
        text(moveEvaluation, uiX + 5, 145, 190, 70);

        // Log
        fill(255);
        stroke(0);
        rect(uiX, 250, 200, 500, 5);
        fill(0);
        textSize(14);
        textAlign(LEFT, TOP);
        text("Spiel-Logbuch (Notation):", uiX + 5, 255);
        textSize(12);
        text(logMessage, uiX + 5, 275, 190, 480);
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

    // --- Maus & Klicks ---

    @Override
    public void mouseClicked() {
        if (gameOver) return;

        // Setup-Screen clicks
        if (phase == GamePhase.SETUP_OPPONENTS || phase == GamePhase.SETUP_START_PLAYER) {
            handleSetupClick();
            return;
        }

        int[] coord = getCoordFromMouse();
        if (coord == null) return;
        int c = coord[0];
        int r = coord[1];

        if (placingWorkers) {
            // Mensch platziert
            if (!agents.containsKey(currentPlayerId)) {
                handlePlacementClick(c, r);
            }
        } else if (agents.containsKey(currentPlayerId)) {
            // KI am Zug -> Klicks ignorieren
        } else {
            // Menschlicher Zug
            handleGameClick(c, r);
        }
    }

    private void handleSetupClick() {
        int y = WINDOW_SIZE / 2;
        int h = 40;

        if (phase == GamePhase.SETUP_OPPONENTS) {
            int w = 200;
            int x1 = WINDOW_SIZE / 2 - 120;
            int x2 = WINDOW_SIZE / 2 + 120;
            if (mouseX > x1 - w / 2 && mouseX < x1 + w / 2 && mouseY > y - h / 2 && mouseY < y + h / 2) {
                setupGameStructure(1);
            } else if (mouseX > x2 - w / 2 && mouseX < x2 + w / 2 && mouseY > y - h / 2 && mouseY < y + h / 2) {
                setupGameStructure(2);
            }
        } else if (phase == GamePhase.SETUP_START_PLAYER) {
            int offset = 120;
            int buttonWidth = 120;
            for (int i = 0; i < playerIds.size(); i++) {
                String pid = playerIds.get(i);
                int x = WINDOW_SIZE / 2 - offset * (playerIds.size() - 1) / 2 + offset * i;
                if (mouseX > x - buttonWidth / 2 && mouseX < x + buttonWidth / 2 && mouseY > y - h / 2 && mouseY < y + h / 2) {
                    initializePlacement(pid);
                    return;
                }
            }
        }
    }

    private int countPlacedWorkers() {
        int count = 0;
        for (String pid : playerIds) {
            List<Worker> list = board.getWorkersByPlayer(pid);
            if (list != null) count += list.size();
        }
        return count;
    }

    // --- KI-Platzierung: läuft so lange weiter, wie der aktuelle Spieler eine KI ist und noch nicht alle platziert sind ---
    private void runAiPlacementLoop() {
        Random random = new Random();

        while (true) {
            // Falls schon alle platziert -> brechen
            if (countPlacedWorkers() == playerIds.size() * workersToPlace) break;

            // Wenn aktueller Spieler keine KI ist -> Menschen-Platzierung abwarten
            if (!agents.containsKey(currentPlayerId)) {
                // Reset counter für menschlichen Spieler
                placedWorkersCount = board.getWorkersByPlayer(currentPlayerId) == null ? 0 : board.getWorkersByPlayer(currentPlayerId).size();
                break;
            }

            // Setze beide Arbeiter der KI (falls noch nicht gesetzt)
            int already = board.getWorkersByPlayer(currentPlayerId) == null ? 0 : board.getWorkersByPlayer(currentPlayerId).size();
            for (int i = already + 1; i <= workersToPlace; i++) {
                int col, row;
                do {
                    col = random.nextInt(Board.BOARD_SIZE);
                    row = random.nextInt(Board.BOARD_SIZE);
                } while (board.isOccupied(col, row));
                board.placeWorker(currentPlayerId, i, col, row);
                logMessage += "\n" + currentPlayerId + " platziert Arbeiter " + i + ": " + coordToNotation(col, row);
            }

            // Weiter zum nächsten Spieler
            currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
            currentPlayerId = playerIds.get(currentPlayerIndex);
        }

        // Falls jetzt alle platziert wurden -> Platzierungsphase abschließen und Startspieler setzen
        boolean allPlaced = countPlacedWorkers() == playerIds.size() * workersToPlace;
        if (allPlaced) {
            placingWorkers = false;
            // Stelle sicher, dass currentPlayerId auf den gewählten Startspieler gesetzt ist
            currentPlayerIndex = playerIds.indexOf(startPlayerId);
            currentPlayerId = startPlayerId;
            moveEvaluation = "Alle Arbeiter platziert! " + currentPlayerId + " beginnt.";

            phase = GamePhase.MOVE_WORKER;
            // Wenn der Startspieler eine KI ist, sofort KI-Zug starten
            if (agents.containsKey(currentPlayerId)) {
                aiThinking = true;
                phase = GamePhase.WAIT_FOR_AI;
            }
        } else {
            // Noch nicht alle platziert → menschlicher Spieler dran (currentPlayerId ist bereits korrekt)
            moveEvaluation = "Platzierung " + currentPlayerId + ". Klicke " + (workersToPlace - (board.getWorkersByPlayer(currentPlayerId) == null ? 0 : board.getWorkersByPlayer(currentPlayerId).size())) + " freie Felder.";
        }
    }

    private void handlePlacementClick(int c, int r) {
        if (!board.isOccupied(c, r)) {
            // Setze Arbeiter für aktuellen menschlichen Spieler
            int idForThisPlayer = board.getWorkersByPlayer(currentPlayerId) == null ? 1 : board.getWorkersByPlayer(currentPlayerId).size() + 1;
            board.placeWorker(currentPlayerId, idForThisPlayer, c, r);
            logMessage += "\n" + currentPlayerId + " platziert Arbeiter " + idForThisPlayer + ": " + coordToNotation(c, r);
            placedWorkersCount++;

            if (placedWorkersCount < workersToPlace) {
                moveEvaluation = "Platzierung " + currentPlayerId + ". Klicke noch " + (workersToPlace - placedWorkersCount) + " freie Felder.";
                return;
            }

            // beide Arbeiter des aktuellen Menschen gesetzt -> zum nächsten Spieler
            placedWorkersCount = 0;
            currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
            currentPlayerId = playerIds.get(currentPlayerIndex);

            // Wenn der nächste Spieler eine KI ist -> KI-Platzierungsschleife starten
            if (agents.containsKey(currentPlayerId)) {
                runAiPlacementLoop();
                return;
            }

            // Prüfen, ob alle platziert sind (falls nach KI-Platzierung alle fertig wurden)
            boolean allPlaced = countPlacedWorkers() == playerIds.size() * workersToPlace;
            if (allPlaced) {
                placingWorkers = false;
                currentPlayerIndex = playerIds.indexOf(startPlayerId);
                currentPlayerId = startPlayerId;
                moveEvaluation = "Alle Arbeiter platziert! " + currentPlayerId + " beginnt.";
                phase = GamePhase.MOVE_WORKER;
                if (agents.containsKey(currentPlayerId)) {
                    aiThinking = true;
                    phase = GamePhase.WAIT_FOR_AI;
                }
                return;
            }

            moveEvaluation = "Platzierung " + currentPlayerId + ". Klicke 2 freie Felder.";
        }
    }

    private void handleGameClick(int c, int r) {
        int[] clickedCoord = new int[]{c, r};

        if (phase == GamePhase.MOVE_WORKER) {
            String workerIdAt = board.getWorkerIdAt(c, r);

            if (workerIdAt != null && workerIdAt.equals(currentPlayerId)) {
                selectedWorkerCoord = clickedCoord;
                moveEvaluation = "Arbeiter gewählt. Klicke Bewegungsziel (grüner Rahmen).";
                moveFromCoord = null;
                moveToCoord = null;

            } else if (selectedWorkerCoord != null) {
                List<int[]> moveTargets = board.getValidMoveTargets(selectedWorkerCoord);
                if (targetsContain(moveTargets, clickedCoord)) {
                    if (board.checkWin(clickedCoord)) {
                        Move move = new Move(selectedWorkerCoord, clickedCoord);
                        executeMove(currentPlayerId, move);
                        return;
                    }
                    moveFromCoord = selectedWorkerCoord;
                    moveToCoord = clickedCoord;
                    selectedWorkerCoord = null;
                    phase = GamePhase.CHOOSE_BUILD_TARGET;
                    moveEvaluation = "Bewegt nach " + coordToNotation(moveToCoord) + ". Klicke Bauziel (gelber Rahmen).";
                } else {
                    moveEvaluation = "Ungültiger Zug. Wähle ein gültiges Bewegungsziel (grüner Rahmen).";
                }
            } else {
                moveEvaluation = "Bitte wähle zuerst einen deiner Arbeiter.";
            }

        } else if (phase == GamePhase.CHOOSE_BUILD_TARGET) {
            if (isBuildTargetValid(moveToCoord, moveFromCoord, clickedCoord)) {
                Move move = new Move(moveFromCoord, moveToCoord, clickedCoord);
                executeMove(currentPlayerId, move);
                moveFromCoord = null;
                moveToCoord = null;
                // executeMove setzt currentPlayerId auf nächsten Spieler
                if (agents.containsKey(currentPlayerId) && !gameOver) {
                    aiThinking = true;
                    phase = GamePhase.WAIT_FOR_AI;
                    moveEvaluation = "KI " + currentPlayerId + " denkt nach...";
                } else if (!gameOver) {
                    moveEvaluation = "Bauen beendet. Nächster Zug " + currentPlayerId + ". Wähle einen Arbeiter.";
                    phase = GamePhase.MOVE_WORKER;
                }
            } else {
                moveEvaluation = "Ungültiges Bauziel. Wähle ein gültiges Feld (gelber Rahmen).";
            }
        }
    }

    private boolean isBuildTargetValid(int[] workerNewCoord, int[] workerOldCoord, int[] buildCoord) {
        if (workerNewCoord == null || buildCoord == null) return false;
        List<int[]> neighbors = board.getNeighbors(workerNewCoord);
        if (!targetsContain(neighbors, buildCoord)) return false;
        if (board.isDomed(buildCoord[0], buildCoord[1])) return false;
        String workerAtBuild = board.getWorkerIdAt(buildCoord[0], buildCoord[1]);
        if (workerAtBuild != null) {
            // erlaubt, wenn es das Feld ist, das gerade verlassen wurde
            return Arrays.equals(buildCoord, workerOldCoord);
        }
        return true;
    }

    private void executeMove(String playerId, Move move) {
        Board before = board.clone();
        board.moveWorker(playerId, move.getMoveFrom(), move.getMoveTo());
        board.buildStructure(move.getBuildAt());
        Board after = board.clone();

        moveEvaluation = evaluateMove(before, after, move, playerId);

        String notation = formatMoveNotation(move);
        logMessage += "\n" + playerId + ": " + notation;
        logMessage += "\n" + moveEvaluation;

        if (board.checkWin(move.getMoveTo())) {
            gameOver = true;
            winnerId = playerId;
            moveEvaluation = "GEWONNEN! " + playerId + " hat Level 3 erreicht.";
            return;
        }

        // Wechsle zum nächsten Spieler (behalte die Reihenfolge playerIds)
        currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        currentPlayerId = playerIds.get(currentPlayerIndex);

        // Standardphase
        phase = GamePhase.MOVE_WORKER;
    }

    private String evaluateMove(Board before, Board after, Move move, String playerId) {
        int[] from = move.getMoveFrom();
        int[] to = move.getMoveTo();
        int[] build = move.getBuildAt();
        int levelBefore = before.getLevel(from[0], from[1]);
        int levelAfter = after.getLevel(to[0], to[1]);
        StringBuilder eval = new StringBuilder();
        eval.append(playerId).append(" zieht von ").append(coordToNotation(from)).append(" nach ").append(coordToNotation(to));
        if (levelAfter > levelBefore) eval.append(" (steigt auf Level ").append(levelAfter).append(")");
        eval.append(" und baut auf ").append(coordToNotation(build)).append(". ");
        int score = 0;
        if (levelAfter > levelBefore) { eval.append("Er steigt höher – das ist gut. "); score += 2; }
        int buildLevel = before.getLevel(build[0], build[1]);
        if (buildLevel == 2) { eval.append("Baut auf Level 2 – mögliche Verteidigung. "); score += 1; }
        int distCenter = Math.abs(to[0] - 2) + Math.abs(to[1] - 2);
        if (distCenter <= 1) { eval.append("Kontrolliert das Zentrum. "); score += 1; }
        boolean blocks = false;
        for (String pid : playerIds) {
            if (!pid.equals(playerId)) {
                for (Worker w : before.getWorkersByPlayer(pid)) {
                    List<int[]> moves = before.getValidMoveTargets(w.getCoord());
                    if (moves != null) {
                        for (int[] t : moves) {
                            if (Arrays.equals(t, to)) { blocks = true; break; }
                        }
                    }
                    if (blocks) break;
                }
            }
        }
        if (blocks) { eval.append("Blockiert Gegnerischen Weg. "); score += 2; }
        if (score >= 4) eval.append("Sehr starker Zug.");
        else if (score >= 2) eval.append("Guter Zug.");
        else if (score >= 0) eval.append("Solider Zug.");
        else eval.append("Riskanter Zug.");
        return eval.toString();
    }

    private void runAiTurn() {
        if (!agents.containsKey(currentPlayerId)) {
            aiThinking = false;
            return;
        }

        ReflexAgent agent = agents.get(currentPlayerId);

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

        moveEvaluation = evaluation.evaluation;
        aiThinking = false;

        executeMove(currentPlayerId, move);

        // Wenn der nächste Spieler KI ist → KI weitermachen
        if (agents.containsKey(currentPlayerId) && !gameOver) {
            aiThinking = true;
            phase = GamePhase.WAIT_FOR_AI;
        } else if (!gameOver) {
            moveEvaluation = "Dein Zug! Wähle einen Arbeiter.";
            phase = GamePhase.MOVE_WORKER;
        }
    }

    // --- Hilfsmethoden ---

    private int[] getCoordFromMouse() {
        if (mouseX < BOARD_OFFSET || mouseX > BOARD_OFFSET + Board.BOARD_SIZE * CELL_SIZE ||
                mouseY < BOARD_OFFSET || mouseY > BOARD_OFFSET + Board.BOARD_SIZE * CELL_SIZE) {
            return null;
        }
        int c = (mouseX - BOARD_OFFSET) / CELL_SIZE;
        int r = Board.BOARD_SIZE - 1 - (mouseY - BOARD_OFFSET) / CELL_SIZE;
        return new int[]{c, r};
    }

    private boolean targetsContain(List<int[]> targets, int[] coord) {
        if (targets == null || coord == null) return false;
        for (int[] t : targets) {
            if (t[0] == coord[0] && t[1] == coord[1]) return true;
        }
        return false;
    }

    private String coordToNotation(int c, int r) {
        char colChar = (char) ('a' + c);
        char rowChar = (char) ('1' + r);
        return String.valueOf(colChar) + rowChar;
    }

    private String coordToNotation(int[] coord) {
        if (coord == null || !board.isValidCoord(coord[0], coord[1])) return "N/A";
        return coordToNotation(coord[0], coord[1]);
    }

    private String formatMoveNotation(Move move) {
        String fromNot = coordToNotation(move.getMoveFrom()[0], move.getMoveFrom()[1]);
        String toNot = coordToNotation(move.getMoveTo()[0], move.getMoveTo()[1]);
        if (move.getBuildAt() == null) return fromNot + "-" + toNot;
        String buildNot = coordToNotation(move.getBuildAt()[0], move.getBuildAt()[1]);
        return fromNot + "-" + toNot + "," + buildNot;
    }

    // --- Main ---
    public static void main(String[] passedArgs) {
        String[] appletArgs = new String[] { "SantoriniGUI" };
        PApplet.main(appletArgs);
    }
}
