import java.util.*;

/**
 * Verwaltet den Zustand des Spielfelds, einschließlich Bauleveln und Arbeiterpositionen.
 */
public class Board {
    public static final int BOARD_SIZE = 5;
    public static final int MAX_LEVEL = 4; // Level 4 ist Kuppel
    private static final int WIN_LEVEL = 3;

    private final int[][] buildingLevels; // Bauhöhe [col][row] (0 bis 4)
    private final Map<String, List<Worker>> workers; // Arbeiter nach Spieler-ID
    private final List<String> playerIds;

    public Board(List<String> playerIds) {
        this.playerIds = playerIds;
        this.buildingLevels = new int[BOARD_SIZE][BOARD_SIZE];
        this.workers = new HashMap<>();
        for (String id : playerIds) {
            workers.put(id, new ArrayList<>());
        }
    }

    // --- Zustandsabfragen ---

    /**
     * Gibt die Arbeiter-ID an der Koordinate zurück, falls vorhanden.
     */
    public String getWorkerIdAt(int col, int row) {
        for (Map.Entry<String, List<Worker>> entry : workers.entrySet()) {
            for (Worker worker : entry.getValue()) {
                if (worker.getCoord()[0] == col && worker.getCoord()[1] == row) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public boolean isOccupied(int col, int row) {
        return getWorkerIdAt(col, row) != null;
    }

    public int getLevel(int col, int row) {
        if (!isValidCoord(col, row)) return -1;
        return buildingLevels[col][row];
    }

    public void setLevel(int col, int row, int level) {
        if (isValidCoord(col, row)) {
            buildingLevels[col][row] = Math.min(level, MAX_LEVEL);
        }
    }


    public boolean isDomed(int col, int row) {
        return getLevel(col, row) == MAX_LEVEL;
    }

    public boolean isValidCoord(int col, int row) {
        return col >= 0 && col < BOARD_SIZE && row >= 0 && row < BOARD_SIZE;
    }

    // --- Zug-Logik ---

    public boolean checkWin(int[] moveTo) {
        return getLevel(moveTo[0], moveTo[1]) == WIN_LEVEL;
    }


    @Override
    public Board clone() {
        Board copy = new Board(this.playerIds);
        for (int c = 0; c < BOARD_SIZE; c++) {
            for (int r = 0; r < BOARD_SIZE; r++) {
                copy.setLevel(c, r, this.getLevel(c, r));
            }
        }
        for (String pid : playerIds) {
            for (Worker w : this.getWorkersByPlayer(pid)) {
                int[] coord = w.getCoord();
                copy.placeWorker(pid, w.getWorkerId(), coord[0], coord[1]);
            }
        }
        return copy;
    }


    /**
     * Gibt eine Liste aller gültigen Nachbarfelder zurück.
     */
    public List<int[]> getNeighbors(int[] coord) {
        List<int[]> neighbors = new ArrayList<>();
        int col = coord[0];
        int row = coord[1];

        for (int dc = -1; dc <= 1; dc++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (dc == 0 && dr == 0) continue;

                int nCol = col + dc;
                int nRow = row + dr;

                if (isValidCoord(nCol, nRow)) {
                    neighbors.add(new int[]{nCol, nRow});
                }
            }
        }
        return neighbors;
    }

    /**
     * Gibt die gültigen Felder zurück, auf die ein Arbeiter von 'workerCoord' ziehen kann.
     */
    public List<int[]> getValidMoveTargets(int[] workerCoord) {
        int currentLevel = getLevel(workerCoord[0], workerCoord[1]);
        List<int[]> targets = new ArrayList<>();

        for (int[] target : getNeighbors(workerCoord)) {
            int targetLevel = getLevel(target[0], target[1]);

            if (isOccupied(target[0], target[1]) || isDomed(target[0], target[1])) {
                continue;
            }

            // Maximal eine Ebene höher steigen
            if (targetLevel <= currentLevel + 1) {
                targets.add(target);
            }
        }
        return targets;
    }

    /**
     * Gibt die gültigen Felder zurück, auf denen nach dem Zug gebaut werden kann.
     */
    public List<int[]> getValidBuildTargets(int[] workerCoord) {
        List<int[]> targets = new ArrayList<>();

        for (int[] target : getNeighbors(workerCoord)) {
            if (isOccupied(target[0], target[1]) || isDomed(target[0], target[1])) {
                continue;
            }
            targets.add(target);
        }
        return targets;
    }

    // --- Zustandsänderungen ---

    public void placeWorker(String playerId, int workerId, int col, int row) {
        Worker newWorker = new Worker(playerId, workerId, col, row);
        workers.get(playerId).add(newWorker);
    }

    public void moveWorker(String playerId, int[] moveFrom, int[] moveTo) {
        Worker worker = getWorker(playerId, moveFrom);
        if (worker != null) {
            worker.setCoord(moveTo[0], moveTo[1]);
        }
    }

    public void buildStructure(int[] buildAt) {
        if (buildAt == null) return;
        int col = buildAt[0];
        int row = buildAt[1];

        if (isValidCoord(col, row) && buildingLevels[col][row] < MAX_LEVEL) {
            buildingLevels[col][row]++;
        }
    }

    // --- Hilfsfunktionen für den Agenten ---
    public List<String> getPlayerIds() {
        return playerIds;
    }
    public List<Worker> getWorkersByPlayer(String playerId) {
        return workers.get(playerId);
    }

    public Worker getWorker(String playerId, int[] coord) {
        for (Worker worker : workers.get(playerId)) {
            if (worker.getCoord()[0] == coord[0] && worker.getCoord()[1] == coord[1]) {
                return worker;
            }
        }
        return null;
    }


    // --- Anzeige ---

    public void display(String currentPlayerId) {
        System.out.println("-------------------------------------");
        System.out.println("Am Zug: " + currentPlayerId);
        System.out.println("    a   b   c   d   e");

        // Iteration von oben nach unten (Zeile 5 bis 1)
        for (int r = BOARD_SIZE - 1; r >= 0; r--) {
            StringBuilder rowStr = new StringBuilder((r + 1) + " |");
            for (int c = 0; c < BOARD_SIZE; c++) {
                String workerId = getWorkerIdAt(c, r);
                int level = getLevel(c, r);
                String cell;

                if (workerId != null) {
                    // Arbeiter: z.B. P1 auf Level 2 -> '12'
                    cell = " " + workerId.charAt(1) + level + " ";
                } else if (level == MAX_LEVEL) {
                    // Kuppel
                    cell = " D ";
                } else {
                    // Nur Gebäude oder leer
                    cell = " " + level + " ";
                }
                rowStr.append(cell).append(" ");
            }
            System.out.println(rowStr.toString());
        }
        System.out.println("-------------------------------------");
        System.out.println("Legende: 'NL' = Arbeiter PN auf Level L, 'D' = Kuppel");
    }
}