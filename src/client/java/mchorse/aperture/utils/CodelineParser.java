package mchorse.aperture.utils;

/**
 * Comment-aware GLSL statement accumulator (S21 P218).
 *
 * <p>Ported <b>verbatim</b> from
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/utils/CodelineParser.java}
 * — same fields, same public mutability, same character walk. Two instances
 * exist in {@link mchorse.blockbuster.client.compat.iris.ShaderCurveBridge}:
 * one with {@code ':'} for {@code case} labels (option-demotion pass) and one
 * with {@code ';'} for {@code const} statements that span lines (source
 * rewriter).</p>
 *
 * <p>The behaviours that matter and are pinned by
 * {@code CodelineParserTest}:</p>
 *
 * <ul>
 *   <li>Each {@link #parseLine(String)} call {@code trim()}s its input and
 *       appends a single {@code ' '} <b>after</b> the walk — including when the
 *       walk broke out early on a {@code //} comment, and including when the
 *       statement already ended. That trailing space is what joins physical
 *       lines into one logical statement.</li>
 *   <li>{@code //} deletes the already-appended {@code '/'} and stops the line;
 *       {@code /*} deletes it too and swallows everything until {@code *}{@code /}.</li>
 *   <li>The terminator character is itself appended before the walk stops, so
 *       {@code cache} ends with {@code ':'} / {@code ';'} — legacy's
 *       {@code checkCase} regex ({@code ^\s*case\s+NAME\s*:}) depends on it.</li>
 *   <li>Once {@link #isEnd} is set the loop body never runs again, so trailing
 *       code after the terminator is dropped; only {@link #reset()} clears it.</li>
 * </ul>
 */
public class CodelineParser
{
    public final char lineEnd;

    public StringBuilder cache;
    public boolean isComment;
    public boolean isEnd;

    public CodelineParser(char lineEnd)
    {
        this.lineEnd = lineEnd;

        this.cache = new StringBuilder();
        this.isComment = false;
        this.isEnd = false;
    }

    public void reset()
    {
        this.cache.setLength(0);
        this.isComment = false;
        this.isEnd = false;
    }

    public void parseLine(String code)
    {
        code = code.trim();

        for (int i = 0; i < code.length() && !this.isEnd; i++)
        {
            char thisChar = code.charAt(i);
            char lastChar = 0;

            if (i > 0)
            {
                lastChar = code.charAt(i - 1);
            }

            if (this.isComment)
            {
                if (lastChar == '*' && thisChar == '/')
                {
                    this.isComment = false;
                }
            }
            else
            {
                if (lastChar == '/' && thisChar == '/')
                {
                    this.cache.setLength(this.cache.length() - 1);

                    break;
                }
                else if (lastChar == '/' && thisChar == '*')
                {
                    this.isComment = true;
                    this.cache.setLength(this.cache.length() - 1);
                }
                else if (thisChar == this.lineEnd)
                {
                    this.isEnd = true;
                }

                if (!this.isComment)
                {
                    this.cache.append(thisChar);
                }
            }
        }

        this.cache.append(' ');
    }
}
