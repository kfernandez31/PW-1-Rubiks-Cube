package concurrentcube.Rotations;

import concurrentcube.Color;
import concurrentcube.Cube;
import concurrentcube.Side;

public abstract class Rotation {

    protected final Side side;
    protected final Cube cube;
    protected final int layer;
    protected final int group_no;

    protected Rotation(Cube cube, Side side, int layer) {
        this.cube = cube;
        this.side = side;
        this.layer = layer;
        this.group_no = calculateGroup();
    }

    public static Rotation newRotation(Cube cube, Side side, int layer) {
        switch (side) {
            case Top : return new TopRotation(cube, layer);
            case Left : return new LeftRotation(cube, layer);
            case Front : return new FrontRotation(cube, layer);
            case Right : return new RightRotation(cube, layer);
            case Back : return new BackRotation(cube, layer);
            case Bottom : return new BottomRotation(cube, layer);
            default : throw new IndexOutOfBoundsException("Invalid side.");
        }
    }

    public Side getSide() {
        return side;
    }

    public int getLayer() {
        return layer;
    }

    public int getGroupno() {
        return group_no;
    }

    /**
     * Calculates to which group a given rotation belongs to based on its side and rotated layer.
     * There is a total of 3 * `size` rotation groups (3 choices for an axis and size for a column/row)
     * @return group number
     */
    protected abstract int calculateGroup();

    /**
     * Physically rotates a Rubik's cube -
     * Faces the cube from the perspective of `this.side`,
     * grabs a ring of the cube indicated by `this.layer`
     * and turns it clockwise.
     *
     * How the rotation is performed:
     * - swaps rows and columns of surrounding sides cyclically,
     * - reverses them if it has to,
     * - and finally turns the whole side 90 degrees clockwise.
     */
    public void applyRotation() {
        if (layer == 0) {
            turnSideClockwise(side);
        }
        else if (layer == cube.getSize() - 1) {
            turnSideCounterclockwise(side.opposite());
        }
    }

    /**
     * Checks if two rotations operate on parallel layers of a cube.
     * @param side - another rotation's side
     * @return are rotations parallel
     */
    public boolean isParallel(Side side) {
        if (side == Side.NoSide) {
            return true;
        }
        else return this.side == side || this.side.opposite() == side;
    }

    /**
     * Swaps colors of two squares on a cube.
     * @param side_a : side of the first square
     * @param row_a : row of the first square
     * @param col_a : column of the first square
     * @param side_b : side of the second square
     * @param row_b : row of the second square
     * @param col_b : column of the second square
     */
    public void swapSquareColors(Side side_a, int row_a, int col_a, Side side_b, int row_b, int col_b) {
        Color temp = cube.getSquareColor(side_a, row_a, col_a);
        cube.setSquareColor(cube.getSquareColor(side_b, row_b, col_b), side_a, row_a, col_a);
        cube.setSquareColor(temp, side_b, row_b, col_b);
    }

    /**
     * Swaps a column and a row, maintaining left->right and top->down order.
     * @param side_c: side containing column
     * @param col: swapped column
     * @param side_r: side containing row
     * @param row: swapped row
     */
    protected void swapColumnAndRow(Side side_c, int col, Side side_r, int row) {
        for (int i = 0; i < cube.getSize(); i++) {
            swapSquareColors(side_c, i, col, side_r, row, i);
        }
    }

    /**
     * Swaps two columns, maintaining top->down order.
     * @param side_a: side containing first column
     * @param col_a: first swapped column
     * @param side_b: side containing second column
     * @param col_b: second swapped column
     */
    protected void swapColumnAndColumn(Side side_a, int col_a, Side side_b, int col_b) {
        for (int row = 0; row < cube.getSize(); row++) {
            swapSquareColors(side_a, row, col_a, side_b, row, col_b);
        }
    }

    /**
     * Swaps two rows, maintaining left->right order.
     * @param side_a: side containing first row
     * @param row_a: first swapped row
     * @param side_b: side containing second row
     * @param row_b: second swapped row
     */
    protected void swapRowAndRow(Side side_a, int row_a, Side side_b, int row_b) {
        for (int col = 0; col < cube.getSize(); col++) {
            swapSquareColors(side_a, row_a, col, side_b, row_b, col);
        }
    }

    /**
     * Reverses a row.
     * @param side: side containing row
     * @param row: reversed row
     */
    protected void reverseRow(Side side, int row) {
        for (int i = 0, j = cube.getSize() - 1; i < j; i++, j--) {
            swapSquareColors(side, row, i, side, row, j);
        }
    }

    /**
     * Reverses a column.
     * @param side: side containing column
     * @param col: reversed column
     */
    protected void reverseColumn(Side side, int col) {
        for (int i = 0, j = cube.getSize() - 1; i < j; i++, j--) {
            swapSquareColors(side, i, col, side, j, col);
        }
    }

    /**
     * Reflects a side of the cube diagonally.
     * @param side : reflected side
     */
    private void reflectDiagonally(Side side) {
        for (int i = 0; i < cube.getSize(); i++) {
            for (int j = i + 1; j < cube.getSize(); j++) {
                swapSquareColors(side, i, j, side, j, i);
            }
        }
    }

    /**
     * Reflects a side of the cube horizontally.
     * @param side : reflected side
     */
    private void reflectHorizontally(Side side) {
        for (int i = 0; i < cube.getSize(); i++) {
            for (int j = 0; j < cube.getSize() / 2; j++) {
                swapSquareColors(side, i, j, side, cube.getSize() - 1 - i, j);
            }
        }
    }

    /**
     * Reflects a side of the cube vertically.
     * @param side : reflected side
     */
    private void reflectVertically(Side side) {
        for (int i = 0; i < cube.getSize() / 2; i++) {
            for (int j = 0; j < cube.getSize(); j++) {
                swapSquareColors(side, i, j, side, i, cube.getSize() - 1 - j);
            }
        }
    }

    /*
     for (int x = 0; x < N / 2; x++) {
            for (int y = x; y < N - x - 1; y++) {
                Color temp = cube.getSquareColor(side, x, y);
                cube.setSquareColor(cube.getSquareColor(side, y, N - 1 - x), side, x, y);
                cube.setSquareColor(cube.getSquareColor(side, N - 1 - x, N - 1 - y), side, y, N - 1 - x);
                cube.setSquareColor(cube.getSquareColor(side, N - 1 - y, x), side, N - 1 - x, N - 1 - y);
                cube.setSquareColor(temp, side, N - 1 - y, x);
            }
        }
     */

    /**
     * Rotates a side of the cube counter-clockwise by 90 degrees
     * as a composition of two linear isometries.
     * @param side : rotated side
     */
    protected void turnSideCounterclockwise(Side side) {
        reflectDiagonally(side);
        reflectHorizontally(side);
    }

    /**
     * Rotates a side of the cube clockwise by 90 degrees.
     * as a composition of two linear isometries.
     * @param side : rotated side
     */
    protected void turnSideClockwise(Side side) {
        reflectDiagonally(side);
        reflectVertically(side);
    }

}