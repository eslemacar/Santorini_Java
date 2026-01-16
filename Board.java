import java.util.*;

/**
 * Verwaltet den Zustand des Spielfelds, einschließlich Bauleveln und Arbeiterpositionen.
 */
public class Board {
    public static final int BOARD_SIZE = 5;
    public static final int MAX_LEVEL = 4; // Level 4 ist Kuppel
    private static final int WIN_LEVEL = 3;

    private final int[][] buildingLevels;
    private final Map<String, List<Worker>> workers;
    private final List<String> playerIds;

    public Board(List<String> playerIds) {
        this.playerIds = new ArrayList<>(playerIds);
        this.buildingLevels = new int[BOARD_SIZE][BOARD_SIZE];
        this.workers = new HashMap<>();
        for (String id : this.playerIds) {
            workers.put(id, new ArrayList<>());
        }
    }

    //  Zustand abfragen

    public String getWorkerIdAt(int col, int row) {
        if (!isValidCoord(col, row)) return null;

        for (Map.Entry<String, List<Worker>> entry : workers.entrySet()) {
            for (Worker worker : entry.getValue()) {
                int[] c = worker.getCoord();
                if (c[0] == col && c[1] == row) return entry.getKey();
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
            buildingLevels[col][row] = Math.max(0, Math.min(level, MAX_LEVEL));
        }
    }

    public boolean isDomed(int col, int row) {
        return getLevel(col, row) == MAX_LEVEL;
    }

    public boolean isValidCoord(int col, int row) {
        return col >= 0 && col < BOARD_SIZE && row >= 0 && row < BOARD_SIZE;
    }

    // --- Gewinnprüfung ---
    public boolean checkWin(int[] moveTo) {
        if (moveTo == null) return false;
        return getLevel(moveTo[0], moveTo[1]) == WIN_LEVEL;
    }

    //  Klonen
    @Override
    public Board clone() {
        Board copy = new Board(this.playerIds);

        for (int c = 0; c < BOARD_SIZE; c++) {
            for (int r = 0; r < BOARD_SIZE; r++) {
                copy.buildingLevels[c][r] = this.buildingLevels[c][r];
            }
        }

        for (String pid : playerIds) {
            for (Worker w : workers.get(pid)) {
                int[] coord = w.getCoord();
                copy.placeWorker(pid, w.getWorkerId(), coord[0], coord[1]);
            }
        }

        return copy;
    }

    //  Nachbarn
    public List<int[]> getNeighbors(int[] coord) {
        List<int[]> neighbors = new ArrayList<>();
        if (coord == null) return neighbors;

        int col = coord[0];
        int row = coord[1];

        for (int dc = -1; dc <= 1; dc++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (dc == 0 && dr == 0) continue;
                int nc = col + dc;
                int nr = row + dr;
                if (isValidCoord(nc, nr)) neighbors.add(new int[]{nc, nr});
            }
        }

        return neighbors;
    }

    //  gültige Ziele
    public List<int[]> getValidMoveTargets(int[] workerCoord) {
        List<int[]> targets = new ArrayList<>();
        if (workerCoord == null) return targets;

        int currentLevel = getLevel(workerCoord[0], workerCoord[1]);

        for (int[] t : getNeighbors(workerCoord)) {
            int c = t[0], r = t[1];
            if (isOccupied(c, r)) continue;
            if (isDomed(c, r)) continue;

            int level = getLevel(c, r);
            if (level <= currentLevel + 1) targets.add(t);
        }

        return targets;
    }

    public List<int[]> getValidBuildTargets(int[] workerCoord) {
        List<int[]> targets = new ArrayList<>();
        if (workerCoord == null) return targets;

        for (int[] t : getNeighbors(workerCoord)) {
            int c = t[0], r = t[1];
            if (isDomed(c, r)) continue;
            if (isOccupied(c, r)) continue;
            targets.add(t);
        }

        return targets;
    }

    //  Änderungen am Board

    public boolean placeWorker(String playerId, int workerId, int col, int row) {
        if (!playerIds.contains(playerId)) return false;
        if (!isValidCoord(col, row)) return false;
        if (isOccupied(col, row)) return false;

        workers.get(playerId).add(new Worker(playerId, workerId, col, row));
        return true;
    }

    public boolean moveWorker(String playerId, int[] from, int[] to) {
        if (!playerIds.contains(playerId)) return false;
        if (from == null || to == null) return false;
        if (!isValidCoord(to[0], to[1])) return false;
        if (isOccupied(to[0], to[1])) return false;

        Worker w = getWorker(playerId, from);
        if (w == null) return false;

        w.setCoord(to[0], to[1]);
        return true;
    }

    public boolean buildStructure(int[] buildAt) {
        if (buildAt == null) return false;
        int c = buildAt[0];
        int r = buildAt[1];
        if (!isValidCoord(c, r)) return false;

        if (buildingLevels[c][r] >= MAX_LEVEL) return false;
        buildingLevels[c][r]++;

        return true;
    }

    //  Arbeiter Hilfen

    public Worker getWorker(String playerId, int[] coord) {
        if (!playerIds.contains(playerId)) return null;
        for (Worker w : workers.get(playerId)) {
            int[] c = w.getCoord();
            if (c[0] == coord[0] && c[1] == coord[1]) return w;
        }
        return null;
    }

    public List<Worker> getWorkersByPlayer(String pid) {
        return new ArrayList<>(workers.get(pid));
    }

    public List<String> getPlayerIds() {
        return new ArrayList<>(playerIds);
    }

    //  Anzeige
    public void display(String currentPlayerId) {
        System.out.println("-------------------------------------");
        System.out.println("Am Zug: " + currentPlayerId);
        System.out.println("    a   b   c   d   e");

        for (int r = 4; r >= 0; r--) {
            StringBuilder row = new StringBuilder((r + 1) + " |");

            for (int c = 0; c < 5; c++) {
                String owner = getWorkerIdAt(c, r);
                int lvl = getLevel(c, r);

                String cell;
                if (owner != null) {
                    // P1 → 1, P2 → 2
                    cell = " " + owner.charAt(1) + lvl + " ";
                } else if (lvl == 4) {
                    cell = " D ";
                } else {
                    cell = " " + lvl + " ";
                }

                row.append(cell);
                row.append(" ");
            }
            System.out.println(row);
        }

        System.out.println("-------------------------------------");
    }
}
