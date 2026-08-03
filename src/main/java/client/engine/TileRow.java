package client.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A retained row of draggable tiles — the same treatment {@code Hand} gives cards, generalized for the joker bar,
 * the consumable/relic area, and the shop shelves. Tiles are matched across frames by stable id (so a reorder
 * broadcast slides tiles instead of teleporting them), one tile at a time may be dragged (the row parts around
 * it), and a released tile always glides: into its new slot when the drop lands, back home when it doesn't —
 * that glide-back needs no special case, because the layout retargets every tile every frame and a tile the
 * model didn't move simply gets told its old slot again. Pure and unit-tested; callers draw at {@link #x}/{@link #y}.
 */
public final class TileRow {

    private final double tileW, tileH;
    private final List<Integer> ids = new ArrayList<>();          // model order, as last reconciled
    private final Map<Integer, Motion> motions = new HashMap<>();
    private double[] slotsX = new double[0];                      // slot centers, as last laid out
    private double slotY;
    private int draggedId = -1;

    public TileRow(double tileW, double tileH) {
        this.tileW = tileW;
        this.tileH = tileH;
    }

    /** Matches the row to the model's order; new tiles will spawn directly in their slot at the next layout. */
    public void reconcile(List<Integer> modelIds) {
        ids.clear();
        ids.addAll(modelIds);
        motions.keySet().retainAll(modelIds);
        if (draggedId != -1 && !modelIds.contains(draggedId)) draggedId = -1;   // retainAll already dropped its motion
    }

    /**
     * Assigns this frame's slot centers ({@code slotsX[i]}, all at {@code y}) and retargets every tile. While a
     * tile is dragged the display order parts around it: the others close ranks toward the slots the drop would
     * leave them in, which is the row previewing its own future.
     */
    public void layout(double[] slotsX, double y) {
        this.slotsX = slotsX.clone();
        this.slotY = y;
        List<Integer> order = displayOrder();
        for (int slot = 0; slot < order.size() && slot < slotsX.length; slot++) {
            int id = order.get(slot);
            Motion m = motions.get(id);
            if (m == null) {   // first sighting: spawn directly in place, no fly-in from (0,0)
                m = new Motion(slotsX[slot], y, 0.25, Easing.EASE_OUT_BACK_SOFT);   // a small settle bounce on landing
                motions.put(id, m);
            }
            if (id != draggedId) m.moveTo(slotsX[slot], y);
        }
    }

    public void advance(double dt) { for (Motion m : motions.values()) m.advance(dt); }

    /** The ids in display order: model order, except the dragged tile occupies the slot nearest the cursor. */
    public List<Integer> displayOrder() {
        List<Integer> order = new ArrayList<>(ids);
        Motion held = motions.get(draggedId);
        if (held != null && order.remove((Integer) draggedId)) {
            int slot = nearestSlot(held.x(), order.size() + 1);
            order.add(slot, draggedId);
        }
        return order;
    }

    public int count() { return ids.size(); }
    public double x(int id) { Motion m = motions.get(id); return m == null ? 0 : m.x(); }
    public double y(int id) { Motion m = motions.get(id); return m == null ? 0 : m.y(); }
    public boolean isDragged(int id) { return id == draggedId; }

    /** Squash &amp; stretch for a tile from its current velocity — 1 at rest; the caller scales the tile's w/h by these. */
    public double stretchX(int id) { Motion m = motions.get(id); return m == null ? 1 : Squash.scaleX(m.vx(), m.vy()); }
    public double stretchY(int id) { Motion m = motions.get(id); return m == null ? 1 : Squash.scaleY(m.vx(), m.vy()); }

    /** The tile under {@code (px,py)} by current animated positions, or -1. Topmost = latest in display order. */
    public int tileAt(double px, double py) {
        List<Integer> order = displayOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            int id = order.get(i);
            if (Math.abs(px - x(id)) <= tileW / 2 && Math.abs(py - y(id)) <= tileH / 2) return id;
        }
        return -1;
    }

    // --- drag ----------------------------------------------------------------

    /** Picks up the tile under the point; returns its model index, or -1 if the point hit none. */
    public int beginDrag(double px, double py) {
        int id = tileAt(px, py);
        if (id == -1) return -1;
        draggedId = id;
        return ids.indexOf(id);
    }

    public void dragTo(double px, double py) {
        Motion m = motions.get(draggedId);
        if (m != null) m.snap(px, py);
    }

    /**
     * Releases the held tile and reports where it ended: the slot index it was dropped into when the cursor is
     * within the row's band, or -1 for "nowhere" — in either case the tile itself just glides to wherever the
     * next layout sends it.
     */
    public int endDrag(double px, double py) {
        if (draggedId == -1) return -1;
        draggedId = -1;
        boolean inBand = Math.abs(py - slotY) <= tileH / 2 + 30 && slotsX.length > 0
                && px >= slotsX[0] - tileW && px <= slotsX[slotsX.length - 1] + tileW;
        return inBand ? nearestSlot(px, slotsX.length) : -1;
    }

    /** The slot whose center is nearest {@code px}, among the first {@code n} slots. */
    private int nearestSlot(double px, int n) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < Math.min(n, slotsX.length); i++) {
            double d = Math.abs(slotsX[i] - px);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }
}
