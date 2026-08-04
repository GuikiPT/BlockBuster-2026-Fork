package mchorse.mclib.utils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Write-to-temp-then-rename helper (roadmap <b>P284</b>) — a port addition with
 * no 1.12.2 counterpart.
 *
 * <p><b>Why this exists.</b> Every user-content writer in 2.7.2 (and therefore
 * in this port before P284) opened the destination file directly:
 * {@code new FileOutputStream(file)}, {@code new PrintWriter(file)},
 * {@code Files.write(path, bytes)}. All three <b>truncate the destination the
 * instant they are opened</b>, before a single byte of the new content is
 * written. If serialization throws halfway, the disk is full, or the game
 * crashes in the window, the user is left with a truncated or empty file and
 * the previous content is gone. For a recording that window is covered by the
 * {@code .dat~N} rotation, but scenes, camera profiles and {@code model.json}
 * have <b>no backup at all</b> — the in-place write is their only copy.</p>
 *
 * <p>This helper writes the full content to a sibling {@code .tmp} file, and
 * only once that write has completed successfully does it move the temp file
 * over the destination. The destination therefore never exists in a half-written
 * state: either it still holds the previous content, or it holds the complete
 * new content. {@link java.nio.file.StandardCopyOption#ATOMIC_MOVE} is attempted
 * first and falls back to a plain replace on filesystems that refuse it (the
 * temp file is a sibling, so it is on the same filesystem in practice).</p>
 *
 * <p><b>Deliberate deviation from 1.12.2</b>, recorded here rather than at each
 * call site: legacy's writers are in-place and this port's are not. The bytes
 * that land in the destination are unchanged — only the failure mode differs
 * (previous content survives instead of being destroyed).</p>
 */
public final class AtomicWrite
{
    /** Suffix of the scratch file. Sibling of the destination, same filesystem. */
    public static final String TEMP_SUFFIX = ".bbtmp";

    private AtomicWrite()
    {}

    /**
     * Produces the bytes of the destination file into the supplied stream.
     * Anything it throws aborts the write and leaves the destination untouched.
     */
    public interface Writer
    {
        void write(OutputStream stream) throws IOException;
    }

    /**
     * Write {@code writer}'s output to {@code file}, atomically.
     *
     * <p>The parent directory is created when missing (legacy writers relied on
     * their path helpers having {@code mkdirs()}'d already; doing it here keeps
     * the temp file creation from failing on a fresh world).</p>
     *
     * @throws IOException if the content could not be produced or the move
     *         failed. In either case the destination is untouched.
     */
    public static void write(File file, Writer writer) throws IOException
    {
        if (file == null)
        {
            throw new IOException("Cannot write to a null file");
        }

        File parent = file.getParentFile();

        if (parent != null && !parent.isDirectory())
        {
            parent.mkdirs();
        }

        File temp = new File(parent, file.getName() + TEMP_SUFFIX);

        try
        {
            try (OutputStream stream = Files.newOutputStream(temp.toPath()))
            {
                writer.write(stream);
            }

            move(temp.toPath(), file.toPath());
        }
        finally
        {
            /* A failed write leaves a stale scratch file next to the user's
             * data; drop it. Deleting after a *successful* move is a no-op. */
            temp.delete();
        }
    }

    /**
     * Convenience for the text writers (camera profiles, {@code model.json}):
     * UTF-8 bytes, written atomically.
     */
    public static void writeString(File file, String content) throws IOException
    {
        write(file, stream -> stream.write(content.getBytes(StandardCharsets.UTF_8)));
    }

    /** {@link #writeString(File, String)} against a {@link Path}. */
    public static void writeString(Path path, String content) throws IOException
    {
        writeString(path.toFile(), content);
    }

    private static void move(Path from, Path to) throws IOException
    {
        try
        {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e)
        {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
