package io.github.mekhontsev.magicdesk;

import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * PERSONAL PATCH (not upstream): optional relative "laptop trackpad" gesture mode for
 * direct on-screen touch, for use when the desktop is viewed on the phone's own display
 * (not an external one). Drag to move the cursor, tap for a primary click, press-and-hold
 * (or a two-finger tap) for a secondary click, and a two-finger drag to scroll. Mirrors
 * MagicDeskTouchpadActivity's TouchSurface gesture rules, but drives the locally bound
 * HostedSurfaceOutput directly instead of an external display's MagicDeskRuntime pointer API.
 * Only finger-on-touchscreen events are claimed; pen/stylus and physical mouse input are
 * left untouched for the normal HostedPointerInput/HostedDirectInput paths.
 */
final class HostedTrackpadGesture {
    private final View view;
    private final GestureDetector gestureDetector;
    private final float touchSlop;
    private float x = .5f, y = .5f;
    private float lastX, lastY, travel, pendingDragX, pendingDragY;
    private boolean usedTwoFingers, scrolling, longPressHandled, dragActive;

    HostedTrackpadGesture(View view) {
        this.view = view;
        touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        gestureDetector = new GestureDetector(view.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public void onLongPress(MotionEvent event) {
                if (!usedTwoFingers && !scrolling) {
                    longPressHandled = true;
                    pendingDragX = pendingDragY = 0f;
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                }
            }
        });
    }

    /** True when this event was a finger-on-touchscreen gesture it claimed and handled. */
    boolean event(MotionEvent event, HostedSurfaceOutput output, HostedViewport viewport) {
        if (output == null || !viewport.available()) return false;
        boolean finger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
                && event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN);
        if (!finger) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                travel = pendingDragX = pendingDragY = 0f;
                usedTwoFingers = scrolling = longPressHandled = false;
                gestureDetector.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    finishDrag(output);
                    usedTwoFingers = true;
                    lastX = averageX(event);
                    lastY = averageY(event);
                }
                gestureDetector.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleMove(event, output, viewport);
                gestureDetector.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                gestureDetector.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_UP:
                gestureDetector.onTouchEvent(event);
                if (dragActive) finishDrag(output);
                else if (usedTwoFingers) { if (!scrolling) click(output, HostedSurfaceOutput.Button.SECONDARY); }
                else if (longPressHandled) click(output, HostedSurfaceOutput.Button.SECONDARY);
                else if (travel <= touchSlop) click(output, HostedSurfaceOutput.Button.PRIMARY);
                reset();
                return true;
            case MotionEvent.ACTION_CANCEL:
                gestureDetector.onTouchEvent(event);
                finishDrag(output);
                reset();
                return true;
            default:
                return false;
        }
    }

    private void handleMove(MotionEvent event, HostedSurfaceOutput output, HostedViewport viewport) {
        if (usedTwoFingers) {
            if (event.getPointerCount() < 2) return;
            float cx = averageX(event), cy = averageY(event);
            float dx = cx - lastX, dy = cy - lastY;
            travel += (float) Math.hypot(dx, dy);
            if (scrolling || travel > touchSlop) {
                scrolling = true;
                output.scroll(x, y, 0, -dy / Math.max(1f, touchSlop));
            }
            lastX = cx;
            lastY = cy;
            return;
        }
        float cx = event.getX(), cy = event.getY();
        float dx = cx - lastX, dy = cy - lastY;
        travel += (float) Math.hypot(dx, dy);
        lastX = cx;
        lastY = cy;
        if (longPressHandled && !dragActive) {
            pendingDragX += dx;
            pendingDragY += dy;
            if (Math.hypot(pendingDragX, pendingDragY) <= touchSlop) return;
            dragActive = true;
            output.button(x, y, HostedSurfaceOutput.Button.PRIMARY, true);
            pendingDragX = pendingDragY = 0f;
        }
        move(dx, dy, output, viewport);
    }

    private void move(float dx, float dy, HostedSurfaceOutput output, HostedViewport viewport) {
        x = Math.max(0, Math.min(1, x + dx / viewport.width()));
        y = Math.max(0, Math.min(1, y + dy / viewport.height()));
        output.pointer(x, y);
    }

    private void click(HostedSurfaceOutput output, HostedSurfaceOutput.Button button) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        output.button(x, y, button, true);
        output.button(x, y, button, false);
    }

    private void finishDrag(HostedSurfaceOutput output) {
        if (!dragActive) return;
        dragActive = false;
        output.button(x, y, HostedSurfaceOutput.Button.PRIMARY, false);
    }

    private void reset() {
        usedTwoFingers = scrolling = longPressHandled = false;
        travel = pendingDragX = pendingDragY = 0f;
    }

    private static float averageX(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getX(i);
        return sum / e.getPointerCount();
    }

    private static float averageY(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getY(i);
        return sum / e.getPointerCount();
    }
}
