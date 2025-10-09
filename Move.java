import java.util.Arrays;
import java.util.Objects;

/**
 * Repräsentiert einen vollständigen Zug (Bewegen und optional Bauen).
 */
public class Move {
    private final int[] moveFrom;
    private final int[] moveTo;
    private final int[] buildAt; // null, wenn es ein Gewinnzug ist

    /**
     * Konstruktor für einen Standardzug (Bewegen und Bauen).
     * @param moveFrom Startkoordinate.
     * @param moveTo Zielkoordinate der Bewegung.
     * @param buildAt Zielkoordinate des Baus.
     */
    public Move(int[] moveFrom, int[] moveTo, int[] buildAt) {
        this.moveFrom = moveFrom;
        this.moveTo = moveTo;
        this.buildAt = buildAt;
    }

    /**
     * Konstruktor für einen Gewinnzug (Bewegen ohne Bauen).
     * @param moveFrom Startkoordinate.
     * @param moveTo Zielkoordinate der Bewegung.
     */
    public Move(int[] moveFrom, int[] moveTo) {
        this(moveFrom, moveTo, null);
    }

    // Getter
    public int[] getMoveFrom() { return moveFrom; }
    public int[] getMoveTo() { return moveTo; }
    public int[] getBuildAt() { return buildAt; }

    /**
     * Wichtig für Set-Operationen oder das Vergleichen von Zügen.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Move move = (Move) o;
        return Arrays.equals(moveFrom, move.moveFrom) &&
                Arrays.equals(moveTo, move.moveTo) &&
                Arrays.equals(buildAt, move.buildAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(moveFrom), Arrays.hashCode(moveTo), Arrays.hashCode(buildAt));
    }
}