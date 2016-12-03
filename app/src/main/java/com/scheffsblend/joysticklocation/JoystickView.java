package com.scheffsblend.joysticklocation;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by clark on 7/19/2016.
 */
public class JoystickView extends View {
    private Drawable mBackGround;
    private Drawable mKnob;

    private float mMovementRadius;

    private int mKnobWidth;
    private int mKnobHeight;

    private boolean mSnapBackToCenter;
    private boolean mTouchMovesStick;
    private boolean mMoveStickToTouchPoint;

    private static final double[] SNAP_TO_ANGLES = {
            0, Math.PI / 4, Math.PI / 2, 3 * Math.PI / 4, Math.PI,
            -Math.PI, -3 * Math.PI / 4, -Math.PI / 2, -Math.PI / 4
    };
    private static final double SNAP_TO_DISTANCE = Math.PI / 8;

    private OnJoystickPositionChangedListener mListener;

    public JoystickView(Context context) {
        this(context, null);
    }

    public JoystickView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mBackGround = context.getDrawable(R.drawable.joystick_background);
        mKnob = context.getDrawable(R.drawable.joystick_knob);
        mKnobWidth = mKnob.getIntrinsicWidth();
        mKnobHeight = mKnob.getIntrinsicHeight();
        mSnapBackToCenter = true;
        mMoveStickToTouchPoint = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBackGround.setBounds(0, 0, w, h);
        mMovementRadius = (Math.min(w, h) - Math.min(mKnobWidth, mKnobHeight)) / 2;
        int x = w / 2;
        int y = h / 2;
        setKnobLocation(x, y);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mBackGround.draw(canvas);
        mKnob.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (distanceFromCenter(x, y) > mBackGround.getIntrinsicWidth() / 2) return false;

                Rect bounds = mKnob.getBounds();
                if (!bounds.contains(x, y)) {
                    mTouchMovesStick = mMoveStickToTouchPoint;
                } else {
                    mTouchMovesStick = true;
                }
            case MotionEvent.ACTION_MOVE:
                if (mTouchMovesStick) {
                    setKnobLocation(x, y);
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mTouchMovesStick && mSnapBackToCenter) returnKnobToCenter(x, y);
                break;
        }
        return true;
    }

    private int[] clampKnobLocation(int x, int y) {
        int[] pos = new int[] {x, y};
        double localizedX = x - getWidth() / 2;
        double localizedY = y - getHeight() / 2;
        double length = Math.sqrt(Math.pow(localizedX, 2) + Math.pow(localizedY, 2));
        double theta = getNearestSnapAngle(Math.atan2(localizedY, localizedX));
        if (length > mMovementRadius) {
            length = mMovementRadius;
        }
        pos[0] = (int) (length * Math.cos(theta)) + getWidth() / 2;
        pos[1] = (int) (length * Math.sin(theta)) + getHeight() / 2;
        return pos;
    }

    private void setKnobLocation(int x, int y) {
        int[] pos = clampKnobLocation(x, y);
        int widthDiv2 = mKnobWidth / 2;
        int heightDiv2 = mKnobHeight / 2;
        mKnob.setBounds(pos[0] - widthDiv2, pos[1] - heightDiv2,
                pos[0] + widthDiv2, pos[1] + heightDiv2);
        if (mListener != null) {
            int deltaX = pos[0] - getWidth() / 2;
            int deltaY = pos[1] - getHeight() / 2;
            double magnitude = Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2))
                    / mMovementRadius;
            double theta = Math.atan2(deltaY, deltaX);
            Log.d("setKnobLocation", "theta=" + theta + " radians (" + theta * 180/Math.PI + " deg)");
            mListener.onJoystickPositionChanged((float) (magnitude * Math.cos(theta)),
                    (float) (magnitude * Math.sin(theta)));
        }
    }

    private void returnKnobToCenter(final int startX, final int startY) {
        final int[] pos = clampKnobLocation(startX, startY);
        final int deltaX = getWidth() / 2 - pos[0];
        final int deltaY = getHeight() / 2 - pos[1];
        ValueAnimator animator = ObjectAnimator.ofFloat(0, 1);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (Float) animation.getAnimatedValue();
                setKnobLocation((int)(pos[0] + deltaX * value),
                        (int) (pos[1] + deltaY * value));
                invalidate();
            }
        });
        animator.setDuration(250);
        animator.start();
    }

    private double distanceFromCenter(float x, float y) {
        x -= getWidth() / 2;
        y -= getHeight() / 2;
        return Math.sqrt(x*x + y*y);
    }

    private double getNearestSnapAngle(double theta) {
        double snapAngle = theta;
        for (double angle : SNAP_TO_ANGLES) {
            double delta = theta - angle;
            if (Math.abs(delta) <= SNAP_TO_DISTANCE) {
                snapAngle = angle;
                break;
            }
        }
        return snapAngle;
    }

    public void setOnJoystickPositionChangedListener(OnJoystickPositionChangedListener listener) {
        mListener = listener;
    }

    public void setSnapBackToCenter(boolean snapBackToCenter) {
        mSnapBackToCenter = snapBackToCenter;
        if (snapBackToCenter) {
            Rect bounds = mKnob.getBounds();
            returnKnobToCenter(bounds.centerX(), bounds.centerY());
        }
    }

    public boolean getSnapBackToCenter() {
        return mSnapBackToCenter;
    }

    public interface OnJoystickPositionChangedListener {
        void onJoystickPositionChanged(float x, float y);
    }
}
