package com.winlator.star.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Shared touch + lock behaviour for every in-game performance HUD (classic FrameRating(+Horizontal),
 * GameHub PerfHudView, GameNative PerformanceHudView, and the Fusion HUD). One place so the four
 * overlays can't drift:
 *
 * <ul>
 *   <li><b>tap</b> → {@link Callbacks#onTap()} (orientation-toggle for the first three; size-cycle for
 *       Fusion) — a no-op that only re-flashes the lock badge while locked.</li>
 *   <li><b>drag</b> → moves the host view and, on release, {@link Callbacks#onMoved} to persist the
 *       position — disabled (frozen) while locked.</li>
 *   <li><b>long-press</b> → toggles {@code hudLocked}, persisted via {@link Callbacks#onLockChanged}
 *       and kept in sync with the "Lock in place" menu chip. Works locked or unlocked (so a long-press
 *       always unlocks).</li>
 * </ul>
 *
 * <p>On every lock/unlock (and on a blocked gesture while locked) a small padlock badge fades in →
 * holds → fades out in a corner of the panel. The fade is driven by a self-reposting Runnable that
 * invalidates only while visible — nothing runs on the present path, nothing animates at rest.
 *
 * <p>All methods are Main-thread only (touch + draw), matching the epoll-thread rule: the views feed
 * this from {@code onTouchEvent}/{@code dispatchDraw}, never from a present tick.
 */
public class HudLockController {

    public interface Callbacks {
        /** Unlocked single tap: orientation-toggle (classic/gamehub/gamenative) or size-cycle (fusion). */
        void onTap();
        /** Drag released (unlocked): persist the new (x, y). */
        void onMoved(float x, float y);
        /** Lock toggled by long-press: persist {@code hudLocked} + sync the menu chip. */
        void onLockChanged(boolean locked);
    }

    // Fade timing (ms): in → hold → out. Lock/unlock hold ~1.5 s; a blocked-gesture re-flash is shorter.
    private static final long FADE_IN_MS = 160;
    private static final long HOLD_TOGGLE_MS = 1500;
    private static final long HOLD_REFLASH_MS = 750;
    private static final long FADE_OUT_MS = 420;
    private static final long FRAME_MS = 32;

    private final View view;
    private final Callbacks cb;
    private final GestureDetector detector;

    private boolean locked = false;

    // Drag state
    private float downRawX, downRawY, startX, startY;
    private boolean dragging = false;

    // Snap to sides (ported from DroidDeck PR #1937): on drag release the HUD glides to the nearer
    // screen side and keeps its height, like a picture-in-picture window. A fling carries it toward
    // where it was thrown (velocity projected 0.18 s ahead, matching DroidDeck's FLING_REACH_S).
    private boolean snapEnabled = false;
    private float flingVx = 0f;
    private ValueAnimator snapAnim;

    // Fade state
    private long fadeStart = 0;
    private long holdMs = HOLD_TOGGLE_MS;
    private boolean fadeShowLocked = false;

    private final Paint iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HudLockController(Context context, View view, Callbacks cb) {
        this.view = view;
        this.cb = cb;
        this.detector = new GestureDetector(context, new Listener());
        this.detector.setIsLongpressEnabled(true);
        iconFill.setStyle(Paint.Style.FILL);
        iconStroke.setStyle(Paint.Style.STROKE);
        iconStroke.setStrokeCap(Paint.Cap.ROUND);
        iconStroke.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Set lock state from config (applyConfig) WITHOUT firing the persist callback. */
    public void setLocked(boolean locked) { this.locked = locked; }

    public boolean isLocked() { return locked; }

    /** Enable/disable snap-to-sides on drag release (hudSnap config; applies live). */
    public void setSnapEnabled(boolean enabled) {
        snapEnabled = enabled;
        if (!enabled) stopSnapAnim();
    }

    public boolean onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (dragging && !locked) {
                if (action == MotionEvent.ACTION_UP && snapEnabled) {
                    snapToSide();
                } else {
                    cb.onMoved(view.getX(), view.getY());
                }
            }
            dragging = false;
        }
        return true;
    }

    private final class Listener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            downRawX = e.getRawX();
            downRawY = e.getRawY();
            startX = view.getX();
            startY = view.getY();
            dragging = false;
            flingVx = 0f;
            stopSnapAnim();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // Long-press always toggles — this is the only gesture that still works while locked.
            locked = !locked;
            cb.onLockChanged(locked);
            flash(locked, HOLD_TOGGLE_MS);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (locked) {
                flash(true, HOLD_REFLASH_MS);   // intentionally locked — remind the user
            } else {
                cb.onTap();
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (dragging && !locked) flingVx = velocityX;
            return false;   // the drag already moved the view; only the velocity is recorded
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (locked) {
                if (!dragging) flash(true, HOLD_REFLASH_MS);
                dragging = true;   // mark so the blocked drag doesn't fall through to a tap
                return true;
            }
            stopSnapAnim();
            view.setX(startX + (e2.getRawX() - downRawX));
            view.setY(startY + (e2.getRawY() - downRawY));
            dragging = true;
            return true;
        }
    }

    // ---- Snap to sides ----------------------------------------------------
    private void stopSnapAnim() {
        if (snapAnim != null) {
            snapAnim.removeAllUpdateListeners();
            snapAnim.removeAllListeners();
            snapAnim.cancel();
            snapAnim = null;
        }
    }

    /**
     * Glide the HUD to the nearer screen side (left/right) at its released height, then persist.
     * A fling projects the landing side 0.18 s ahead. Full-width views (Strip) skip the horizontal
     * glide — they span the parent and are parked on the top/bottom edges by plain dragging.
     */
    private void snapToSide() {
        stopSnapAnim();
        ViewGroup parent = view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
        if (parent == null || view.getWidth() <= 0) { cb.onMoved(view.getX(), view.getY()); return; }
        int parentW = parent.getWidth();
        if (view.getWidth() >= parentW - 1) { cb.onMoved(view.getX(), view.getY()); return; }

        float projected = view.getX() + view.getWidth() / 2f + flingVx * 0.18f;
        float targetX = projected < parentW / 2f ? 0f : parentW - view.getWidth();

        float targetY = view.getY();
        int parentH = parent.getHeight();
        if (parentH > 0 && view.getHeight() > 0) {
            targetY = Math.max(0f, Math.min(targetY, parentH - view.getHeight()));
        }

        final float fromX = view.getX(), fromY = view.getY(), toX = targetX, toY = targetY;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(260);
        anim.setInterpolator(new DecelerateInterpolator(1.2f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (float) a.getAnimatedValue();
                view.setX(fromX + (toX - fromX) * t);
                view.setY(fromY + (toY - fromY) * t);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (snapAnim == a) snapAnim = null;
                cb.onMoved(view.getX(), view.getY());
            }
        });
        snapAnim = anim;
        anim.start();
    }

    // ---- Fade badge -------------------------------------------------------
    private void flash(boolean showLocked, long hold) {
        fadeShowLocked = showLocked;
        holdMs = hold;
        fadeStart = SystemClock.elapsedRealtime();
        view.removeCallbacks(fadeTick);
        view.post(fadeTick);
    }

    private final Runnable fadeTick = new Runnable() {
        @Override
        public void run() {
            view.invalidate();
            if (currentAlpha() > 0f) view.postDelayed(this, FRAME_MS);
        }
    };

    private float currentAlpha() {
        if (fadeStart == 0) return 0f;
        long e = SystemClock.elapsedRealtime() - fadeStart;
        if (e < 0) return 0f;
        if (e < FADE_IN_MS) return e / (float) FADE_IN_MS;
        long holdEnd = FADE_IN_MS + holdMs;
        if (e < holdEnd) return 1f;
        long outEnd = holdEnd + FADE_OUT_MS;
        if (e < outEnd) return 1f - (e - holdEnd) / (float) FADE_OUT_MS;
        return 0f;
    }

    private static int withAlpha(int color, float a) {
        return Color.argb(Math.round(255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Draw the padlock badge in the top-right of the panel if a fade is in progress. Call from the
     * host's onDraw (plain View) or dispatchDraw (ViewGroup), after the content.
     *
     * <p>No background box: the glyph is drawn twice — a slightly expanded, thicker semi-transparent
     * black "halo" pass for legibility on any game frame, then the white glyph on top — so it reads as
     * a floating icon, not an opaque tile.
     */
    public void drawBadge(Canvas canvas) {
        float a = currentAlpha();
        if (a <= 0f) return;
        float d = view.getResources().getDisplayMetrics().density;
        float size = 15f * d;
        float margin = 4f * d;
        float right = view.getWidth() - margin;
        float left = right - size;
        float top = margin;

        float cx = (left + right) / 2f;
        float bottom = top + size;
        float bodyTop = top + size * 0.46f;
        float bodyLeft = left + size * 0.14f;
        float bodyRight = right - size * 0.14f;
        float r = size * 0.2f;
        float shackleTop = top + size * 0.08f;

        // Pass 1: dark halo (expanded body, thicker shackle) for contrast; Pass 2: white glyph.
        drawLock(canvas, cx, bodyTop, bodyLeft, bodyRight, bottom, r, shackleTop, d,
            Color.argb(Math.round(150 * a), 0, 0, 0), 3.0f * d, 0.9f * d, false);
        drawLock(canvas, cx, bodyTop, bodyLeft, bodyRight, bottom, r, shackleTop, d,
            withAlpha(0xFFFFFFFF, a), 1.7f * d, 0f, true);
    }

    /** One padlock pass. {@code halo} inflates the body/shackle in dark; the white pass punches the keyhole. */
    private void drawLock(Canvas canvas, float cx, float bodyTop, float bodyLeft, float bodyRight,
                          float bottom, float r, float shackleTop, float d,
                          int color, float strokeW, float expand, boolean punchKeyhole) {
        iconFill.setColor(color);
        iconStroke.setColor(color);
        iconStroke.setStrokeWidth(strokeW);

        // Body
        canvas.drawRoundRect(bodyLeft - expand, bodyTop - expand, bodyRight + expand, bottom + expand,
            2.4f * d, 2.4f * d, iconFill);
        if (punchKeyhole) {
            int prev = iconFill.getColor();
            iconFill.setColor(Color.argb(Color.alpha(color), 0, 0, 0));
            canvas.drawCircle(cx, (bodyTop + bottom) / 2f, 1.6f * d, iconFill);
            iconFill.setColor(prev);
        }

        RectF arc = new RectF(cx - r, shackleTop, cx + r, shackleTop + 2f * r);
        if (fadeShowLocked) {
            // Closed: both legs run straight down into the body.
            Path p = new Path();
            p.addArc(arc, 180f, 180f);
            p.moveTo(cx - r, shackleTop + r); p.lineTo(cx - r, bodyTop);
            p.moveTo(cx + r, shackleTop + r); p.lineTo(cx + r, bodyTop);
            canvas.drawPath(p, iconStroke);
        } else {
            // Open, clearly to the LEFT: the RIGHT leg stays seated in the body; the arc + LEFT leg
            // hinge up-and-left off the right attachment so the left leg lifts well clear of the body.
            canvas.drawLine(cx + r, shackleTop + r, cx + r, bodyTop, iconStroke); // fixed right leg
            Path p = new Path();
            p.addArc(arc, 180f, 180f);
            p.moveTo(cx - r, shackleTop + r); p.lineTo(cx - r, bodyTop); // full-length left leg
            canvas.save();
            canvas.rotate(-42f, cx + r, shackleTop + r);
            canvas.drawPath(p, iconStroke);
            canvas.restore();
        }
    }
}
