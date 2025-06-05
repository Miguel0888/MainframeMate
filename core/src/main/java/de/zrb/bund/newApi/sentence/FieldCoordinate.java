package de.zrb.bund.newApi.sentence;

import java.util.Objects;

public final class FieldCoordinate {
    private final int position; // 1-basiert
    private final int row;      // 1-basiert

    public FieldCoordinate(int position, int row) {
        this.position = position;
        this.row = row;
    }

    public int getPosition() {
        return position;
    }

    public int getRow() {
        return row;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldCoordinate)) return false;
        FieldCoordinate that = (FieldCoordinate) o;
        return position == that.position && row == that.row;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, row);
    }

    @Override
    public String toString() {
        return position + "/" + row;
    }
}
