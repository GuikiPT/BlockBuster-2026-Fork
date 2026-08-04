package mchorse.mclib.client.gui.utils.resizers;

/**
 * Port of McLib 2.4.3's {@code DecoratedResizer} (roadmap P30).
 */
public abstract class DecoratedResizer extends BaseResizer
{
    public IResizer resizer;

    public DecoratedResizer(IResizer resizer)
    {
        this.resizer = resizer;
    }
}
