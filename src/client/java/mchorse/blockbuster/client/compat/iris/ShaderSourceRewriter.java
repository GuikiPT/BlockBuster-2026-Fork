package mchorse.blockbuster.client.compat.iris;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites shader-pack GLSL so that eligible pack options become live uniforms
 * (S21 P218).
 *
 * <p>Legacy source: the body of
 * {@code AsmShaderHandler.getCachedShader(reader, filePath, shaderPack, fileIndex, listFiles, includeLevel)},
 * ported statement-for-statement. Everything that class did <i>around</i> the
 * rewrite — reflectively calling Optifine's {@code resolveIncludes}, rebuilding
 * {@code listFiles} from {@code cachedIncludes}, closing the incoming reader —
 * is gone: on 1.20.4 Iris has already resolved the include graph by the time
 * {@code JcppProcessor.glslPreprocessSource} is called, and that call site is
 * the seam (see {@code JcppProcessorMixin}). What remains is exactly the
 * source-to-source transform.</p>
 *
 * <h2>The transform</h2>
 *
 * <ol>
 *   <li>Every enabled, uniform-eligible option contributes two <i>alias</i>
 *       patterns — one matching any {@code #<directive> NEW ... OPT ...} line
 *       and one matching {@code const T NEW = ... OPT ...}. These sets grow
 *       <b>while</b> the file is walked, so a chain
 *       ({@code #define A OPT} then {@code #define B A}) is followed to any
 *       depth. That is why they are {@code Set<Pattern>} and not a fixed list.</li>
 *   <li>A {@code #define} line that matches an option directly is replaced by
 *       {@link ShaderUniformOption#getSourceLine()}
 *       ({@code #define OPT _uniform_OPT}) and a
 *       {@code uniform int|float _uniform_OPT;} declaration is queued.</li>
 *   <li>A {@code const} statement — accumulated across physical lines by
 *       {@link mchorse.aperture.utils.CodelineParser} with {@code ';'} — that
 *       matches a const option has its declaration <b>commented out</b> and a
 *       bare {@code uniform T OPT;} queued in its place.</li>
 *   <li>A {@code const} statement that merely <i>aliases</i> an option loses its
 *       {@code const } keyword ({@code replaceFirst("const\\s", "")}), because
 *       an initialiser that now reads a uniform is no longer a constant
 *       expression.</li>
 *   <li>Finally the queued declarations are inserted after {@code #version} and
 *       before the first {@code #line}.</li>
 * </ol>
 *
 * <h2>Two deliberate deviations from legacy</h2>
 *
 * <ul>
 *   <li><b>Ordered sets.</b> Legacy used {@link java.util.HashSet} for the
 *       queued declarations and both pattern sets; iteration order was
 *       arbitrary (though stable for given content). This port uses
 *       {@link LinkedHashSet} so the rewritten source is byte-deterministic and
 *       can be golden-tested. GLSL semantics are unaffected — the queued items
 *       are all sibling {@code uniform} declarations, and the alias patterns are
 *       mutually exclusive in practice. Note that the insertion loop still
 *       inserts each declaration at the <i>same</i> offset, so the emitted order
 *       is the reverse of iteration order; that is legacy's shape, kept.</li>
 *   <li><b>Missing {@code #line} is survivable.</b> Legacy computed
 *       {@code pos = builder.indexOf("#line", version)} and inserted at
 *       {@code pos} unguarded — a source with no {@code #line} directive
 *       crashed with {@code StringIndexOutOfBoundsException}. Every reader in
 *       this port is total, so {@link #uniformInsertPosition(CharSequence)}
 *       falls back to just after the {@code #version} line, or to offset 0 when
 *       there is no {@code #version} either.</li>
 * </ul>
 */
public final class ShaderSourceRewriter
{
    private ShaderSourceRewriter()
    {}

    /**
     * The result of one rewrite: the new source plus the options that were
     * actually patched into it (legacy's {@code addOptionUniform} /
     * {@code addOptionUniformConst} side effects, made explicit).
     */
    public static final class Result
    {
        public final String source;
        public final List<ShaderUniformOption> patchedOptions = new ArrayList<>();
        public final List<ShaderUniformConstOption> patchedConstOptions = new ArrayList<>();

        Result(String source)
        {
            this.source = source;
        }
    }

    /**
     * Rewrite {@code source} against the given option sets.
     *
     * @param options      candidate {@code #define} options; only entries that
     *                     are {@link ShaderUniformOption#isEnabled()} and
     *                     {@link ShaderUniformOption#isUniform()} take part
     *                     (legacy filtered {@code Shaders.getShaderPackOptions()}
     *                     the same way).
     * @param constOptions candidate {@code const} options, filtered identically.
     */
    public static Result rewrite(String source, List<ShaderUniformOption> options, List<ShaderUniformConstOption> constOptions)
    {
        List<ShaderUniformOption> patched = new ArrayList<>();
        List<ShaderUniformConstOption> patchedConst = new ArrayList<>();
        String rewritten = rewrite(source, options, constOptions, patched::add, patchedConst::add);
        Result result = new Result(rewritten);

        result.patchedOptions.addAll(patched);
        result.patchedConstOptions.addAll(patchedConst);

        return result;
    }

    /**
     * Rewrite {@code source}, reporting every patched option through the two
     * consumers (legacy's {@code addOptionUniform} /
     * {@code addOptionUniformConst} calls, which registered the uniform with
     * the per-program push side).
     */
    public static String rewrite(String source, List<ShaderUniformOption> options, List<ShaderUniformConstOption> constOptions, Consumer<ShaderUniformOption> onOption, Consumer<ShaderUniformConstOption> onConstOption)
    {
        List<ShaderUniformOption> uniformOptions = new ArrayList<>();
        List<ShaderUniformConstOption> uniformConstOptions = new ArrayList<>();
        Set<String> uniforms = new LinkedHashSet<>();
        Set<Pattern> definePatterns = new LinkedHashSet<>();
        Set<Pattern> constPatterns = new LinkedHashSet<>();

        for (ShaderUniformOption option : options)
        {
            if (option.isEnabled() && option.isUniform())
            {
                uniformOptions.add(option);
                definePatterns.add(Pattern.compile(String.format("^\\s*#\\w+\\s+(\\S+)\\s+(?:.*\\W)?%s(?:\\W.*)?$", option.getName())));
                constPatterns.add(Pattern.compile(String.format("^\\s*const\\s+\\S+\\s+(\\w+)\\s*=(?:.*\\W)?%s(?:\\W.*)?$", option.getName())));
            }
        }

        for (ShaderUniformConstOption option : constOptions)
        {
            if (option.isEnabled() && option.isUniform())
            {
                uniformConstOptions.add(option);
                definePatterns.add(Pattern.compile(String.format("^\\s*#\\w+\\s+(\\S+)\\s+(?:.*\\W)?%s(?:\\W.*)?$", option.getName())));
                constPatterns.add(Pattern.compile(String.format("^\\s*const\\s+\\S+\\s+(\\w+)\\s*=(?:.*\\W)?%s(?:\\W.*)?$", option.getName())));
            }
        }

        StringBuilder builder = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();

        ShaderUniformOption.doPatch = true;
        ShaderUniformConstOption.doPatch = true;

        try (BufferedReader reader = new BufferedReader(new StringReader(source)))
        {
            String line;

            while ((line = reader.readLine()) != null)
            {
                boolean matched = false;

                if (ShaderCurveBridge.PATTERN_DEFINE.matcher(line).find())
                {
                    for (ShaderUniformOption uniform : uniformOptions)
                    {
                        if (matched = uniform.matchesLine(line))
                        {
                            line = uniform.getSourceLine();

                            uniforms.add(String.format("uniform %s %s;\n", uniform.uniformType == ShaderUniformOption.INTEGER ? "int" : "float", ShaderCurveBridge.UNIFORM_PREFIX + uniform.getName()));
                            onOption.accept(uniform);

                            break;
                        }
                    }

                    if (!matched)
                    {
                        String defVar = null;

                        for (Pattern pattern : definePatterns)
                        {
                            Matcher found = pattern.matcher(line);

                            if (found.find())
                            {
                                defVar = found.group(1);

                                break;
                            }
                        }

                        if (defVar != null)
                        {
                            definePatterns.add(Pattern.compile(String.format("^\\s*#\\w+\\s+(\\S+)\\s+(?:.*\\W)?%s(?:\\W.*)?$", defVar)));
                            constPatterns.add(Pattern.compile(String.format("^\\s*const\\s+\\w+\\s+(\\w+)\\s*=(?:.*\\W)?%s(?:\\W.*)?$", defVar)));
                        }
                    }
                }

                if (!matched && ShaderCurveBridge.PATTERN_CONST.matcher(line).find())
                {
                    line = readConstStatement(line, reader, lineBuffer);

                    for (ShaderUniformConstOption uniform : uniformConstOptions)
                    {
                        if (matched = uniform.matchesLine(line))
                        {
                            line = uniform.getSourceLine();

                            uniforms.add(String.format("uniform %s %s;\n/*\n%s\n*/\n", uniform.type, uniform.getName(), line));
                            onConstOption.accept(uniform);

                            line = "";

                            break;
                        }
                    }

                    if (!matched)
                    {
                        String constVar = null;

                        for (Pattern pattern : constPatterns)
                        {
                            Matcher found = pattern.matcher(line);

                            if (found.find())
                            {
                                constVar = found.group(1);
                                line = lineBuffer.toString().replaceFirst("const\\s", "");

                                break;
                            }
                        }

                        if (constVar != null)
                        {
                            definePatterns.add(Pattern.compile(String.format("^\\s*#\\w+\\s+(\\S+)\\s+(?:.*\\W)?%s(?:\\W.*)?$", constVar)));
                            constPatterns.add(Pattern.compile(String.format("^\\s*const\\s+\\w+\\s+(\\w+)\\s*=(?:.*\\W)?%s(?:\\W.*)?$", constVar)));
                        }
                        else
                        {
                            line = lineBuffer.toString();
                        }
                    }
                }

                builder.append(line).append('\n');
            }
        }
        catch (IOException e)
        {
            /* StringReader cannot actually throw, but readLine declares it. */
            throw new UncheckedIOException(e);
        }
        finally
        {
            ShaderUniformOption.doPatch = false;
            ShaderUniformConstOption.doPatch = false;
        }

        int pos = uniformInsertPosition(builder);

        for (String uniform : uniforms)
        {
            builder.insert(pos, uniform);
        }

        return builder.toString();
    }

    /**
     * Legacy's {@code getConstLine}: accumulate a {@code const} statement across
     * however many physical lines it takes to reach a {@code ';'}, stripping
     * comments, while {@code origin} collects the untouched original text (used
     * when the statement turns out to be an alias rather than an option).
     */
    static String readConstStatement(String line, BufferedReader reader, StringBuilder origin) throws IOException
    {
        origin.setLength(0);
        origin.append(line);

        ShaderCurveBridge.constParser.reset();
        ShaderCurveBridge.constParser.parseLine(line);

        while (!ShaderCurveBridge.constParser.isEnd)
        {
            line = reader.readLine();

            if (line == null)
            {
                break;
            }

            ShaderCurveBridge.constParser.parseLine(line);
            origin.append('\n').append(line);
        }

        return ShaderCurveBridge.constParser.cache.toString();
    }

    /**
     * After {@code #version}, before the first {@code #line} — legacy's
     * {@code builder.indexOf("#line", builder.indexOf("#version"))}, made total.
     */
    static int uniformInsertPosition(CharSequence source)
    {
        String text = source.toString();
        int version = text.indexOf("#version");
        int line = text.indexOf("#line", Math.max(version, 0));

        if (line >= 0)
        {
            return line;
        }

        if (version < 0)
        {
            return 0;
        }

        int newline = text.indexOf('\n', version);

        return newline < 0 ? text.length() : newline + 1;
    }
}
