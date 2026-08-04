package mchorse.aperture.client.gui.utils.undo;

/**
 * Fixture points change undo (P183) — also snapshots the selected path
 * point (consumed by the P184 path fixture panel).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/utils/undo/FixturePointsChangeUndo.java
 */
public class FixturePointsChangeUndo extends FixtureValueChangeUndo
{
    private int lastPoint;
    private int point;

    public FixturePointsChangeUndo(int index, String name, int lastPoint, int point, Object oldValue, Object newValue)
    {
        super(index, name, oldValue, newValue);

        this.lastPoint = lastPoint;
        this.point = point;
    }

    public int getLastPoint()
    {
        return this.lastPoint;
    }

    public int getPoint()
    {
        return this.point;
    }
}
