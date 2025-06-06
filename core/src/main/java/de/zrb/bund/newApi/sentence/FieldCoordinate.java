package de.zrb.bund.newApi.sentence;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;
import java.util.Objects;

public class FieldCoordinate implements Comparable<FieldCoordinate> {
    private final int row;
    private final int position;

    public FieldCoordinate(int row, int position) {
        this.row = row;
        this.position = position;
    }

    public int getRow() {
        return row;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public int compareTo(FieldCoordinate o) {
        int cmp = Integer.compare(this.row, o.row);
        return cmp != 0 ? cmp : Integer.compare(this.position, o.position);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldCoordinate)) return false;
        FieldCoordinate other = (FieldCoordinate) o;
        return row == other.row && position == other.position;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, position);
    }

    @Override
    public String toString() {
        return row + "/" + position;
    }
}
