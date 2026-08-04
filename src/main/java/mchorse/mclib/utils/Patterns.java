package mchorse.mclib.utils;

import java.util.regex.Pattern;

/**
 * Full port of McLib 2.4.3's Patterns (roadmap P14). FILENAME is the exact
 * allowed charset for user-entered file/scene/model names — do not change it.
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/Patterns.java
 */
public class Patterns
{
    public static final Pattern FILENAME = Pattern.compile("^[\\w\\d-_.\\[\\]!@#$%^&()]*$");
}
