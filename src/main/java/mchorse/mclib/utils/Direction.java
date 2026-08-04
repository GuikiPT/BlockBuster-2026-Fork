package mchorse.mclib.utils;

/**
 * Full port of McLib 2.4.3's Direction (roadmap P13).
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/Direction.java
 */
public enum Direction
{
    TOP(0.5F, 0F), LEFT(0F, 0.5F), BOTTOM(0.5F, 1F), RIGHT(1F, 0.5F);

    public final float anchorX;
    public final float anchorY;
    public final int factorX;
    public final int factorY;

    private Direction(float anchorX, float anchorY)
    {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.factorX = (int) Interpolations.lerp(-1, 1, anchorX);
        this.factorY = (int) Interpolations.lerp(-1, 1, anchorY);
    }

    public boolean isHorizontal()
    {
        return this == LEFT || this == RIGHT;
    }

    public boolean isVertical()
    {
        return this == TOP || this == BOTTOM;
    }

    public Direction opposite()
    {
        if (this == TOP)
        {
            return BOTTOM;
        }
        else if (this == BOTTOM)
        {
            return TOP;
        }
        else if (this == LEFT)
        {
            return  RIGHT;
        }

        /* this == RIGHT */
        return LEFT;
    }
}
