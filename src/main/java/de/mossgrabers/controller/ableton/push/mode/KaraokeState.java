// Written for Pasha's REAPER/Push workflow.
// Licensed under LGPLv3-or-later, matching DrivenByMoss.

package de.mossgrabers.controller.ableton.push.mode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


/**
 * Snapshot produced by the companion REAPER ReaScript.
 *
 * @param status Transport/data status
 * @param previous Previous lyric line
 * @param current Current lyric line
 * @param next Next lyric line
 * @param following Line after the next line
 */
public record KaraokeState (String status, String previous, String current, String next, String following)
{
    /** State shown before the REAPER bridge has produced its first snapshot. */
    public static final KaraokeState WAITING = new KaraokeState ("WAITING FOR REAPER", "", "", "", "");


    /**
     * Read a snapshot from disk.
     *
     * @param path Snapshot path
     * @return Parsed state, or {@link #WAITING} if unavailable/invalid
     */
    public static KaraokeState read (final Path path)
    {
        try
        {
            final var lines = Files.readAllLines (path, StandardCharsets.UTF_8);
            if (lines.isEmpty () || !"PASHALYRICS1".equals (lines.get (0)))
                return WAITING;

            final Map<String, String> values = new HashMap<> ();
            for (int i = 1; i < lines.size (); i++)
            {
                final String line = lines.get (i);
                final int separator = line.indexOf ('\t');
                if (separator > 0)
                    values.put (line.substring (0, separator), unescape (line.substring (separator + 1)));
            }

            return new KaraokeState (
                values.getOrDefault ("status", "REAPER"),
                values.getOrDefault ("previous", ""),
                values.getOrDefault ("current", ""),
                values.getOrDefault ("next", ""),
                values.getOrDefault ("following", ""));
        }
        catch (final IOException | RuntimeException ex)
        {
            return WAITING;
        }
    }


    private static String unescape (final String value)
    {
        final StringBuilder result = new StringBuilder (value.length ());
        boolean escaped = false;
        for (int i = 0; i < value.length (); i++)
        {
            final char character = value.charAt (i);
            if (!escaped)
            {
                if (character == '\\')
                    escaped = true;
                else
                    result.append (character);
                continue;
            }

            result.append (switch (character)
            {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                default -> character;
            });
            escaped = false;
        }
        if (escaped)
            result.append ('\\');
        return result.toString ();
    }
}
