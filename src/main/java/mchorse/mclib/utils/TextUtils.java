package mchorse.mclib.utils;

/**
 * Port of McLib 2.4.3's TextUtils (roadmap P14, partial — this is the whole
 * legacy class, other text helpers live elsewhere).
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/TextUtils.java
 */
public class TextUtils
{
    public static String processColoredText(String text)
    {
        if (!text.contains("["))
        {
            return text;
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0, c = text.length(); i < c; i++)
        {
            char character = text.charAt(i);

            if (character == '\\' && i < c - 1 && text.charAt(i + 1) == '[')
            {
                builder.append('[');
                i += 1;
            }
            else
            {
                builder.append(character == '[' ? '§' : character);
            }
        }

        return builder.toString();
    }
}
