/**
 * Repräsentiert einen Arbeiter auf dem Santorini-Spielfeld.
 * Verwendet einfache int-Arrays für Koordinaten (col, row).
 */
public class Worker {
    private final String playerId; // Z.B. "P1", "P2"
    private final int workerId;    // 1 oder 2
    private int[] coord;           // [col, row]

    public Worker(String playerId, int workerId, int col, int row) {
        this.playerId = playerId;
        this.workerId = workerId;
        this.coord = new int[]{col, row};
    }

    // Getter
    public String getPlayerId() { return playerId; }
    public int getWorkerId() { return workerId; }
    public int[] getCoord() { return coord; }

    // Setter
    public void setCoord(int col, int row) { this.coord = new int[]{col, row}; }

    // Hilfsfunktion zur einfachen Anzeige
    @Override
    public String toString() {
        return playerId + workerId + " (" + coord[0] + "," + coord[1] + ")";
    }
}