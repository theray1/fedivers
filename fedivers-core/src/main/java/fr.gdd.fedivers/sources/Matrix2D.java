package fr.gdd.fedivers.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Generated because it's trivial code but added a default value of type <T>.
/**
 * A 2D matrix with default value
 * @param <T> The type of the elements stored in the matrix.
 */
public class Matrix2D<T> {
    private final List<List<T>> data = new ArrayList<>();
    private final T defaultValue;

    public Matrix2D () { this(null); }
    public Matrix2D (T defaultValue) { this.defaultValue = defaultValue; }

    public void set(int row, int col, T value) {
        ensureSize(row, col);
        data.get(row).set(col, value);
    }

    public T get(int row, int col) {
        if (row >= data.size() || col >= data.get(row).size()) {
            return defaultValue; // or throw exception
        }
        final T found = data.get(row).get(col);
        return Objects.isNull(found) ? defaultValue : found;
    }

    private void ensureSize(int row, int col) {
        while (data.size() <= row) {
            data.add(new ArrayList<>());
        }
        List<T> currentRow = data.get(row);
        while (currentRow.size() <= col) {
            currentRow.add(defaultValue);
        }
    }

    public int rows() {
        return data.size();
    }

    public int cols(int row) {
        return data.get(row).size();
    }
}
