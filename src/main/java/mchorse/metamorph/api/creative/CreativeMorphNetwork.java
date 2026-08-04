package mchorse.metamorph.api.creative;

/**
 * Static holder for the {@link ICreativeMorphNetwork} seam.
 *
 * <p>Defaults to {@link ICreativeMorphNetwork.Noop}. The network / keybind
 * phase (P54/P55/P60) replaces {@link #INSTANCE} with a real, packet-sending
 * implementation on client init; nothing in the data model references the
 * packet classes directly.</p>
 */
public class CreativeMorphNetwork
{
    public static ICreativeMorphNetwork INSTANCE = new ICreativeMorphNetwork.Noop();
}
