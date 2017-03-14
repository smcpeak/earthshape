// PlotPanel1D.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.ItemSelectable;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import javax.swing.JPanel;

/** Draw the data in a PlotData1D object as a JPanel.
  *
  * This also responds to mouse clicks in the plot area, sending
  * ItemEvents to any listeners, per the model that clicking on
  * an X value is like selecting it. */
public class PlotPanel1D extends JPanel implements
    // Respond to mouse events in this panel.
    MouseListener, MouseMotionListener,
    // This panel is capable of sending ItemEvents.
    ItemSelectable
{
    // ---- Constants ----
    /** AWT boilerplate. */
    private static final long serialVersionUID = -6657389271092560523L;

    /** Pixels between left edge of panel and left edge of main plot area. */
    private static final int LEFT_MARGIN = 70;

    /** Pixels between right edge of panel and right edge of main plot area. */
    private static final int RIGHT_MARGIN = 20;

    /** Pixels between top edge of panel and top edge of main plot area. */
    private static final int TOP_MARGIN = 10;

    /** Pixels between bottom edge of panel and bottom edge of main plot area. */
    private static final int BOTTOM_MARGIN = 25;

    /** Pixel length of major tick marks. */
    private static final int MAJOR_TICK_LENGTH = 5;

    /** Pixel length of minor tick marks. */
    private static final int MINOR_TICK_LENGTH = 2;

    /** Pixels between top edge of an X axis label and its
      * associated tick mark. */
    private static final int X_AXIS_LABEL_PADDING = 3;

    /** Pixels between right edge of a Y axis label and its
      * associated tick mark. */
    private static final int Y_AXIS_LABEL_PADDING = 3;

    // ---- Instance data ----
    /** Data and plot options. */
    private PlotData1D plotData;

    /** If not null, the user has selected the X value at this
      * index (for some purpose; this panel does not know). */
    public Integer selectedXIndex = null;

    /** True when a left button drag is ongoing. */
    private boolean lbuttonDown = false;

    /** Set of registered item listeners. */
    public ArrayList<ItemListener> itemListeners = new ArrayList<ItemListener>();

    // ---- Methods ----
    public PlotPanel1D(PlotData1D plotData_)
    {
        this.setPreferredSize(new Dimension(400, 200));
        this.setBackground(Color.WHITE);

        this.plotData = plotData_;

        this.addMouseListener(this);
        this.addMouseMotionListener(this);
    }

    /** X pixel coordinate of the left of the main plot area. */
    protected int plotLeft()
    {
        return LEFT_MARGIN;
    }

    /** X pixel coordinate of the right of the main plot area. */
    protected int plotRight()
    {
        return this.getWidth()-1 - RIGHT_MARGIN;
    }

    /** Main plot area width. */
    protected int plotWidth()
    {
        return plotRight() - plotLeft();
    }

    /** Y pixel coordinate of the top of the main plot area. */
    protected int plotTop()
    {
        return TOP_MARGIN;
    }

    /** Y pixel coordinate of the bottom of the main plot area. */
    protected int plotBottom()
    {
        return this.getHeight()-1 - BOTTOM_MARGIN;
    }

    /** Main plot area height. */
    protected int plotHeight()
    {
        return plotBottom() - plotTop();
    }

    /** The X coordinate, in pixels from the top of the panel,
      * at which to draw data value 'x'. */
    protected int plotX(double x)
    {
        // Distance from left of plot area as a fraction of
        // the total plot width.
        double xFraction = (x - this.plotData.xMin) /
                           (this.plotData.xMax - this.plotData.xMin);

        return this.plotLeft() + (int)(this.plotWidth() * xFraction);
    }

    /** The Y coordinate, in pixels from the top of the panel,
      * at which to draw data value 'y'. */
    protected int plotY(double y)
    {
        // Distance from bottom of plot area as a fraction of
        // the total plot height.
        double yFraction = (y - this.plotData.yMin) /
                           (this.plotData.yMax - this.plotData.yMin);

        return this.plotTop() + (int)(this.plotHeight() * (1.0 - yFraction));
    }

    /** Draw tick marks and labels along the X axis every 'tickSpace'
      * units.  Only draw labels if 'labels' is true. */
    protected void drawXAxisLabels(Graphics g, double tickSpace, int tickLength, boolean labels)
    {
        if (tickSpace > 0) {
            double x = Math.floor(this.plotData.xMax / tickSpace) * tickSpace;
            while (x >= this.plotData.xMin) {
                int px = plotX(x);
                g.drawLine(px, this.plotBottom(),
                           px, this.plotBottom() + tickLength);
                if (labels) {
                    String label = String.format("%.3g", x);
                    if (Math.abs(x) < tickSpace/5) {
                        // This is really 0.
                        label = "0";
                    }
                    Rectangle2D rect = g.getFontMetrics().getStringBounds(label, g);

                    int dx = (int)(px - rect.getWidth()/2 - rect.getX());
                    int dy = (int)(this.plotBottom() + tickLength + X_AXIS_LABEL_PADDING
                                     - rect.getY());

                    g.drawString(label, dx, dy);
                }
                x -= tickSpace;
            }
        }
    }

    /** Draw tick marks and labels along the Y axis every 'tickSpace'
      * units.  Only draw labels if 'labels' is true. */
    protected void drawYAxisLabels(Graphics g, double tickSpace, int tickLength, boolean labels)
    {
        if (tickSpace > 0) {
            double y = Math.floor(this.plotData.yMax / tickSpace) * tickSpace;
            while (y >= this.plotData.yMin) {
                int py = plotY(y);
                g.drawLine(this.plotLeft() - tickLength, py,
                           this.plotLeft(), py);
                if (labels) {
                    String label = String.format("%.3g", y);
                    if (Math.abs(y) < tickSpace/5) {
                        // This is really 0.
                        label = "0";
                    }
                    Rectangle2D rect = g.getFontMetrics().getStringBounds(label, g);

                    int dx = (int)(this.plotLeft() - tickLength - Y_AXIS_LABEL_PADDING
                                     - rect.getWidth() - rect.getX());
                    int dy = (int)(py - rect.getHeight()/2 - rect.getY());

                    g.drawString(label, dx, dy);
                }
                y -= tickSpace;
            }
        }
    }

    /** Check that the data value ranges in 'plotData' are
      * acceptable.  If not, draw a message and return false. */
    protected boolean checkDataRanges(Graphics g)
    {
        if (this.plotData.xMax <= this.plotData.xMin) {
            g.drawString("X axis range is degenerate.", 20, 20);
            return false;
        }

        if (this.plotData.yMax <= this.plotData.yMin) {
            g.drawString("Y axis range is degenerate.", 20, 20);
            return false;
        }

        return true;
    }

    protected void drawBorderAndAxes(Graphics g)
    {
        // Draw axis ticks and labels.
        this.drawXAxisLabels(g, this.plotData.xMajorTickSpace, MAJOR_TICK_LENGTH, true /*labels*/);
        this.drawXAxisLabels(g, this.plotData.xMinorTickSpace, MINOR_TICK_LENGTH, false /*labels*/);
        this.drawYAxisLabels(g, this.plotData.yMajorTickSpace, MAJOR_TICK_LENGTH, true /*labels*/);
        this.drawYAxisLabels(g, this.plotData.yMinorTickSpace, MINOR_TICK_LENGTH, false /*labels*/);

        // Border around main plot area.
        g.drawRect(this.plotLeft(), this.plotTop(), this.plotWidth(), this.plotHeight());
    }

    @Override
    public void paint(Graphics g)
    {
        super.paint(g);

        g.setColor(Color.BLACK);
        if (this.plotData.yValues.length < 1) {
            g.drawString("Insufficient data", 20, 20);
            return;
        }

        if (!this.checkDataRanges(g)) {
            return;
        }

        this.drawBorderAndAxes(g);

        // Line segments connecting adjacent pairs of points.
        int prevX = 0;
        int prevY = 0;
        for (int i=0; i < this.plotData.yValues.length; i++) {
            float x = this.plotData.xValueForIndex(i);
            float y = this.plotData.yValues[i];

            int px = this.plotX(x);
            int py = this.plotY(y);

            if (i > 0) {
                g.drawLine(prevX, prevY, px, py);
            }
            prevX = px;
            prevY = py;
        }

        if (this.selectedXIndex != null) {
            g.setColor(Color.RED);
            int px = this.plotX(this.plotData.xValueForIndex(this.selectedXIndex));
            g.drawLine(px, this.plotTop(), px, this.plotBottom());
        }
    }

    @Override
    public void mousePressed(MouseEvent ev)
    {
        if (ev.getButton() == MouseEvent.BUTTON1) {
            this.lbuttonDown = true;
            this.lbuttonPressOrDrag(ev);
        }
        else {
            this.setSelectedXIndex(null);
        }
    }

    @Override
    public void mouseReleased(MouseEvent ev)
    {
        if (ev.getButton() == MouseEvent.BUTTON1) {
            this.lbuttonDown = false;
        }
    }

    @Override
    public void mouseDragged(MouseEvent ev)
    {
        if (this.lbuttonDown) {
            this.lbuttonPressOrDrag(ev);
        }
    }

    protected void lbuttonPressOrDrag(MouseEvent ev)
    {
        if (this.plotData.yValues == null) {
            // Guard against this method being called in, say, PlotPanel2D.
            return;
        }

        // Fraction of plot width where mouse clicked.
        float xPlotFraction = (ev.getX() - this.plotLeft()) / (float)this.plotWidth();
        if (xPlotFraction < 0 || xPlotFraction > 1) {
            return;     // Ignore click outside plot area.
        }

        // X value where user clicked.
        float x = this.plotData.xMin + xPlotFraction * (this.plotData.xMax - this.plotData.xMin);

        // Fractional distance from first to last.
        float xIndexRangeFraction = (x - this.plotData.xFirst) /
                                    (this.plotData.xLast - this.plotData.xFirst);

        // Closest index.
        this.setSelectedXIndex(
            (int)(xIndexRangeFraction * (this.plotData.yValues.length-1) + 0.5f));
    }

    /** Change 'selectedXIndex' to 'i', then repaint and fire item
      * selected events as necessary. */
    public void setSelectedXIndex(Integer i)
    {
        if (this.selectedXIndex == i) {
            return;     // No change.
        }

        Integer oldIndex = this.selectedXIndex;
        this.selectedXIndex = i;

        this.repaint();

        this.fireStateChangeEvent(oldIndex, ItemEvent.DESELECTED);
        this.fireStateChangeEvent(this.selectedXIndex, ItemEvent.SELECTED);
    }

    /** If 'item' is not null, fire a state change for it. */
    private void fireStateChangeEvent(Integer item, int stateChange)
    {
        if (item != null) {
            for (ItemListener listener : this.itemListeners) {
                listener.itemStateChanged(new ItemEvent(
                    this, ItemEvent.ITEM_FIRST, item, stateChange));
            }
        }
    }

    @Override
    public void addItemListener(ItemListener listener)
    {
        this.itemListeners.add(listener);
    }

    @Override
    public void removeItemListener(ItemListener listener)
    {
        this.itemListeners.remove(listener);
    }

    @Override
    public Object[] getSelectedObjects()
    {
        if (this.selectedXIndex == null) {
            return null;
        }
        else {
            return new Object[] { this.selectedXIndex };
        }
    }

    // Empty event handlers.
    @Override public void mouseClicked(MouseEvent ev) {}
    @Override public void mouseEntered(MouseEvent ev) {}
    @Override public void mouseExited(MouseEvent ev) {}
    @Override public void mouseMoved(MouseEvent ev) {}
}

// EOF
