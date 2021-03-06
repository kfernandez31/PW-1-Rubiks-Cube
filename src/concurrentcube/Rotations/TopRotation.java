package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.WorkingGroup;
import concurrentcube.Side;

public class TopRotation extends Rotation {

    public TopRotation(Cube cube, int layer) {
        super(cube, Side.Top, layer);
    }

    @Override
    protected WorkingGroup assignGroup() {
        return WorkingGroup.TopBottom;
    }

    @Override
    public int getPlane() {
        return layer;
    }

    @Override
    public void applyRotation() {
        swapRowAndRow(Side.Left, layer, Side.Front, layer);
        swapRowAndRow(Side.Front, layer, Side.Right, layer);
        swapRowAndRow(Side.Right, layer, Side.Back, layer);

        super.applyRotation();
    }

}
