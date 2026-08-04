package mchorse.mclib.utils;

import org.apache.commons.io.FilenameUtils;

import java.io.File;

/**
 * The {@code <name>.dat~1 … <name>.dat~5} backup rotation (roadmap
 * <b>P284</b>).
 *
 * <p>Lifted verbatim out of Blockbuster's {@code Record.savePastCopies} — the algorithm
 * is 2.7.2's, unchanged. It lives in mclib because recordings, scenes and the
 * Metamorph morph library all need it now, and one implementation cannot drift
 * from itself.</p>
 *
 * <p>The rotation walks <i>down</i> from the highest iteration so nothing is
 * clobbered on the way: {@code ~5} is deleted, then {@code ~4 → ~5},
 * {@code ~3 → ~4} … {@code ~1 → ~2}, and finally the live file itself becomes
 * {@code ~1}, leaving the destination free for the caller's fresh write. It
 * therefore runs <b>before</b> the risky write, which is the ordering that makes
 * it worth anything.</p>
 *
 * <p><b>What it does not do</b>, and why the P284 guards exist alongside it: the
 * rotation is unconditional. It shifts on every save regardless of what is being
 * saved, so five consecutive bad saves evict every good copy. The reporter's
 * world had {@code E_1.dat} at 0/0/0/226/99 frames across the chain when this
 * was written — three empty saves in, two from losing the take. Rotation buys
 * time; refusing the bad write is what actually keeps the file.</p>
 */
public final class PastCopies
{
    /** How many {@code ~N} iterations are kept. Legacy constant. */
    public static final int COPIES = 5;

    private PastCopies()
    {}

    /**
     * Rotate the backup chain of {@code file}, whose live extension is
     * {@code extension} (with the leading dot, e.g. {@code ".dat"}).
     *
     * <p>No-op when {@code file} does not exist. Rename failures are silently
     * tolerated exactly like legacy — a rotation that cannot proceed must never
     * stop the save.</p>
     */
    public static void rotate(File file, String extension)
    {
        if (file == null || !file.exists())
        {
            return;
        }

        int counter = COPIES;
        String name = FilenameUtils.removeExtension(file.getName());

        while (counter >= 0 && file.exists())
        {
            File current = pastFile(file, name, extension, counter);

            if (current.exists())
            {
                if (counter == COPIES)
                {
                    current.delete();
                }
                else
                {
                    current.renameTo(pastFile(file, name, extension, counter + 1));
                }
            }

            counter--;
        }
    }

    /**
     * Path of iteration {@code iteration} of the chain; iteration {@code 0} is
     * the live file itself.
     */
    public static File pastFile(File file, String name, String extension, int iteration)
    {
        return new File(file.getParentFile(), name + (iteration == 0 ? extension : extension + "~" + iteration));
    }
}
