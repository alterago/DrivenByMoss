// Written for Pasha's REAPER/Push workflow.
// Licensed under LGPLv3-or-later, matching DrivenByMoss.

package de.mossgrabers.controller.ableton.push.mode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;


/** Displays the current REAPER lyric and the following line on Push. */
public class KaraokeMode extends BaseMode<IItem>
{
    private final Path  snapshotPath;
    private long        lastModified = Long.MIN_VALUE;
    private KaraokeState state       = KaraokeState.WAITING;


    /**
     * Constructor.
     *
     * @param surface Control surface
     * @param model DAW model
     */
    public KaraokeMode (final PushControlSurface surface, final IModel model)
    {
        super ("Karaoke Lyrics", surface, model);

        final String override = System.getProperty ("pasha.lyrics.file", "");
        this.snapshotPath = override.isBlank () ? Path.of (
            System.getProperty ("user.home"),
            "Library", "Application Support", "REAPER", "Cache", "PashaLyricsPush.tsv") : Path.of (override);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay1 (final ITextDisplay display)
    {
        this.refresh ();
        display.setBlock (1, 0, "KARAOKE");
        display.setBlock (2, 0, this.state.current ());
        display.setBlock (3, 0, this.state.next ());
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        this.refresh ();
        display.addElement (new KaraokeComponent (this.state));
    }


    /** {@inheritDoc} */
    @Override
    public String getButtonColorID (final ButtonID buttonID)
    {
        return AbstractFeatureGroup.BUTTON_COLOR_OFF;
    }


    private void refresh ()
    {
        try
        {
            final long modified = Files.getLastModifiedTime (this.snapshotPath).toMillis ();
            if (modified == this.lastModified)
                return;
            this.lastModified = modified;
            this.state = KaraokeState.read (this.snapshotPath);
        }
        catch (final IOException ex)
        {
            this.lastModified = Long.MIN_VALUE;
            this.state = KaraokeState.WAITING;
        }
    }
}
