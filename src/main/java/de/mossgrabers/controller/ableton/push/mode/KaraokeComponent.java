// Written for Pasha's REAPER/Push workflow.
// Licensed under LGPLv3-or-later, matching DrivenByMoss.

package de.mossgrabers.controller.ableton.push.mode;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.graphics.Align;
import de.mossgrabers.framework.graphics.IBounds;
import de.mossgrabers.framework.graphics.IGraphicsContext;
import de.mossgrabers.framework.graphics.IGraphicsInfo;
import de.mossgrabers.framework.graphics.canvas.component.IComponent;


/** Full-width karaoke renderer for the 960x160 Push 2/3 display. */
public record KaraokeComponent (KaraokeState state) implements IComponent
{
    private static final ColorEx BACKGROUND       = ColorEx.fromRGB (13, 15, 19);
    private static final ColorEx CURRENT_CARD     = ColorEx.fromRGB (27, 31, 39);
    private static final ColorEx ACCENT           = ColorEx.fromRGB (55, 211, 170);
    private static final ColorEx CURRENT_TEXT     = ColorEx.fromRGB (246, 248, 250);
    private static final ColorEx NEXT_TEXT        = ColorEx.fromRGB (199, 205, 216);
    private static final ColorEx DIM_TEXT         = ColorEx.fromRGB (108, 116, 132);


    /** {@inheritDoc} */
    @Override
    public void draw (final IGraphicsInfo info)
    {
        final IGraphicsContext gc = info.getContext ();
        final IBounds bounds = info.getBounds ();
        final double left = bounds.left ();
        final double top = bounds.top ();
        final double width = bounds.width ();
        final double height = bounds.height ();

        gc.fillRectangle (left, top, width, height, BACKGROUND);

        final String status = "KARAOKE  •  " + this.state.status ();
        gc.drawTextInBounds (status, left + 16, top + 1, width - 32, 18, Align.LEFT, DIM_TEXT, 12);

        final String previous = this.state.previous ();
        if (!previous.isBlank ())
            gc.drawTextInBounds (previous, left + 24, top + 17, width - 48, 24, Align.CENTER, DIM_TEXT, fit (gc, previous, 17, width - 48, 12));

        final double cardTop = top + 41;
        final double cardHeight = Math.min (70, height - 86);
        gc.fillRoundedRectangle (left + 12, cardTop, width - 24, cardHeight, 5, CURRENT_CARD);
        gc.fillRectangle (left + 12, cardTop, 5, cardHeight, ACCENT);

        String current = this.state.current ();
        if (current.isBlank ())
            current = this.state.next ().isBlank () ? "ОЖИДАЮ ТЕКСТ ИЗ REAPER" : "…";
        gc.drawTextInBounds (current, left + 32, cardTop, width - 64, cardHeight, Align.CENTER, CURRENT_TEXT, fit (gc, current, 44, width - 64, 20));

        final String next = this.state.next ();
        if (!next.isBlank ())
        {
            final double nextTop = cardTop + cardHeight + 3;
            final double nextHeight = Math.max (24, height - nextTop + top - 1);
            gc.drawTextInBounds (next, left + 28, nextTop, width - 56, nextHeight, Align.CENTER, NEXT_TEXT, fit (gc, next, 27, width - 56, 15));
        }
    }


    private static double fit (final IGraphicsContext gc, final String text, final double maximum, final double width, final double minimum)
    {
        final double calculated = gc.calculateFontSize (text, maximum, width, minimum);
        if (calculated < 0)
            return minimum;
        return Math.max (minimum, Math.min (maximum, calculated));
    }
}
