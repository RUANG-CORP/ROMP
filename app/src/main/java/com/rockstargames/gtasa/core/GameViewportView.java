package com.rockstargames.gtasa.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Interactive 3D Perspective Game Viewport.
 * Renders an animated GTA San Andreas open.mp world with live road perspective,
 * skyline, palm trees, interactive player model, and mini-map radar.
 */
public class GameViewportView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder mHolder;
    private Thread mRenderThread;
    private volatile boolean mRunning = false;

    // Paints
    private Paint mSkyPaint;
    private Paint mGroundPaint;
    private Paint mRoadPaint;
    private Paint mLinePaint;
    private Paint mBuildingPaint;
    private Paint mPlayerPaint;
    private Paint mRadarPaint;
    private Paint mTextPaint;

    // Game state
    private float mPlayerX = 0f;
    private float mPlayerY = 0f;
    private float mPlayerAngle = 0f;
    private float mSpeed = 0f;
    private boolean mInVehicle = false;
    private float mRoadOffset = 0f;

    // Virtual Joystick
    private boolean mJoystickActive = false;
    private float mJoystickBaseX = 220f;
    private float mJoystickBaseY = 700f;
    private float mJoystickThumbX = 220f;
    private float mJoystickThumbY = 700f;
    private static final float JOYSTICK_RADIUS = 100f;

    // Callbacks
    private OnSpeedChangeListener mSpeedListener;

    public interface OnSpeedChangeListener {
        void onSpeedChanged(int kmh);
    }

    public GameViewportView(Context context) {
        super(context);
        init();
    }

    public GameViewportView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);

        mSkyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGroundPaint.setColor(Color.parseColor("#1B2A1E")); // Dark SA grass

        mRoadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRoadPaint.setColor(Color.parseColor("#26292E")); // Asphalt

        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setColor(Color.parseColor("#F59E0B")); // Yellow highway divider
        mLinePaint.setStrokeWidth(6f);

        mBuildingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBuildingPaint.setColor(Color.parseColor("#111827"));

        mPlayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mRadarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(26f);
    }

    public void setSpeedListener(OnSpeedChangeListener listener) {
        mSpeedListener = listener;
    }

    public void setInVehicle(boolean inVehicle) {
        mInVehicle = inVehicle;
    }

    public boolean isInVehicle() {
        return mInVehicle;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mRunning = true;
        mRenderThread = new Thread(this, "GameViewportThread");
        mRenderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mRunning = false;
        try {
            if (mRenderThread != null) {
                mRenderThread.join(500);
            }
        } catch (InterruptedException ignored) {}
    }

    @Override
    public void run() {
        long lastTime = SystemClock.elapsedRealtime();

        while (mRunning) {
            Canvas canvas = null;
            try {
                canvas = mHolder.lockCanvas();
                if (canvas != null) {
                    long now = SystemClock.elapsedRealtime();
                    float delta = (now - lastTime) / 1000f;
                    lastTime = now;

                    updateSimulation(delta);
                    renderFrame(canvas);
                }
            } catch (Exception ignored) {
            } finally {
                if (canvas != null) {
                    try {
                        mHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception ignored) {}
                }
            }

            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException ignored) {}
        }
    }

    private void updateSimulation(float delta) {
        if (mJoystickActive) {
            float dx = mJoystickThumbX - mJoystickBaseX;
            float dy = mJoystickThumbY - mJoystickBaseY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist > 10f) {
                mPlayerAngle = (float) Math.toDegrees(Math.atan2(dx, -dy));
                float targetSpeed = mInVehicle ? 110f : 24f;
                mSpeed = Math.min(targetSpeed, mSpeed + targetSpeed * delta * 2f);
            } else {
                mSpeed = Math.max(0f, mSpeed - 30f * delta);
            }
        } else {
            mSpeed = Math.max(0f, mSpeed - 40f * delta);
        }

        mRoadOffset += mSpeed * delta * 15f;
        if (mRoadOffset > 100f) {
            mRoadOffset -= 100f;
        }

        if (mSpeedListener != null) {
            mSpeedListener.onSpeedChanged((int) mSpeed);
        }
    }

    private void renderFrame(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // 1. Sky with Los Santos evening gradient
        mSkyPaint.setShader(new LinearGradient(0, 0, 0, h * 0.45f,
                Color.parseColor("#1E1B4B"), Color.parseColor("#C2410C"), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h * 0.45f, mSkyPaint);

        // 2. Distant Los Santos Skyline silhouettes
        float horizonY = h * 0.45f;
        mBuildingPaint.setColor(Color.parseColor("#171A21"));
        canvas.drawRect(w * 0.15f, horizonY - 140, w * 0.22f, horizonY, mBuildingPaint);
        canvas.drawRect(w * 0.25f, horizonY - 210, w * 0.31f, horizonY, mBuildingPaint); // Maze Bank tower
        canvas.drawRect(w * 0.33f, horizonY - 100, w * 0.42f, horizonY, mBuildingPaint);
        canvas.drawRect(w * 0.58f, horizonY - 160, w * 0.65f, horizonY, mBuildingPaint);
        canvas.drawRect(w * 0.68f, horizonY - 90, w * 0.76f, horizonY, mBuildingPaint);

        // 3. Ground (Terrain)
        canvas.drawRect(0, horizonY, w, h, mGroundPaint);

        // 4. Perspective Highway (San Andreas style)
        Path roadPath = new Path();
        float topRoadW = w * 0.08f;
        float bottomRoadW = w * 0.70f;
        float cx = w * 0.5f;

        roadPath.moveTo(cx - topRoadW / 2f, horizonY);
        roadPath.lineTo(cx + topRoadW / 2f, horizonY);
        roadPath.lineTo(cx + bottomRoadW / 2f, h);
        roadPath.lineTo(cx - bottomRoadW / 2f, h);
        roadPath.close();
        canvas.drawPath(roadPath, mRoadPaint);

        // Highway divider dashes moving toward camera
        for (float f = 0.1f; f <= 1.0f; f += 0.15f) {
            float curY = horizonY + (h - horizonY) * ((f + mRoadOffset / 1000f) % 1.0f);
            float lineWidth = 4f + (curY - horizonY) / (h - horizonY) * 12f;
            float lineH = 10f + (curY - horizonY) / (h - horizonY) * 35f;

            mLinePaint.setStrokeWidth(lineWidth);
            canvas.drawLine(cx, curY, cx, curY + lineH, mLinePaint);
        }

        // 5. Palm Trees on roadside
        drawPalmTree(canvas, w * 0.22f, horizonY + 80, 0.45f);
        drawPalmTree(canvas, w * 0.12f, horizonY + 220, 0.75f);
        drawPalmTree(canvas, w * 0.78f, horizonY + 80, 0.45f);
        drawPalmTree(canvas, w * 0.88f, horizonY + 220, 0.75f);

        // 6. Player or Vehicle in Foreground
        float playerDrawX = cx;
        float playerDrawY = h * 0.78f;

        if (mInVehicle) {
            // Draw Infernus / Sultan sport vehicle silhouette
            mPlayerPaint.setColor(Color.parseColor("#DC2626")); // Sport Red
            RectF carBody = new RectF(playerDrawX - 90, playerDrawY - 40, playerDrawX + 90, playerDrawY + 40);
            canvas.drawRoundRect(carBody, 18, 18, mPlayerPaint);

            mPlayerPaint.setColor(Color.parseColor("#1E293B")); // Windshield
            RectF roof = new RectF(playerDrawX - 55, playerDrawY - 26, playerDrawX + 55, playerDrawY + 22);
            canvas.drawRoundRect(roof, 10, 10, mPlayerPaint);

            // Taillights
            mPlayerPaint.setColor(Color.parseColor("#EF4444"));
            canvas.drawCircle(playerDrawX - 75, playerDrawY + 34, 8, mPlayerPaint);
            canvas.drawCircle(playerDrawX + 75, playerDrawY + 34, 8, mPlayerPaint);
        } else {
            // Draw CJ Pedestrian model
            mPlayerPaint.setColor(Color.parseColor("#0F172A")); // Shadow
            canvas.drawOval(playerDrawX - 35, playerDrawY + 50, playerDrawX + 35, playerDrawY + 68, mPlayerPaint);

            // Torso (White tank top)
            mPlayerPaint.setColor(Color.parseColor("#F8FAFC"));
            RectF torso = new RectF(playerDrawX - 22, playerDrawY - 30, playerDrawX + 22, playerDrawY + 20);
            canvas.drawRoundRect(torso, 8, 8, mPlayerPaint);

            // Head & Skin
            mPlayerPaint.setColor(Color.parseColor("#78350F"));
            canvas.drawCircle(playerDrawX, playerDrawY - 50, 18, mPlayerPaint);

            // Blue Jeans
            mPlayerPaint.setColor(Color.parseColor("#1E3A8A"));
            canvas.drawRect(playerDrawX - 18, playerDrawY + 20, playerDrawX - 3, playerDrawY + 55, mPlayerPaint);
            canvas.drawRect(playerDrawX + 3, playerDrawY + 20, playerDrawX + 18, playerDrawY + 55, mPlayerPaint);
        }

        // 7. Radar Minimap in Bottom-Left
        float radarX = 130f;
        float radarY = h - 130f;
        float radarR = 85f;

        mRadarPaint.setColor(Color.parseColor("#CC0F172A"));
        canvas.drawCircle(radarX, radarY, radarR, mRadarPaint);

        mRadarPaint.setStyle(Paint.Style.STROKE);
        mRadarPaint.setStrokeWidth(4f);
        mRadarPaint.setColor(Color.parseColor("#475569"));
        canvas.drawCircle(radarX, radarY, radarR, mRadarPaint);
        mRadarPaint.setStyle(Paint.Style.FILL);

        // Radar roads
        mRadarPaint.setColor(Color.parseColor("#64748B"));
        mRadarPaint.setStrokeWidth(3f);
        canvas.drawLine(radarX - 60, radarY + 20, radarX + 60, radarY - 20, mRadarPaint);
        canvas.drawLine(radarX, radarY - 60, radarX, radarY + 60, mRadarPaint);

        // Player Arrow on radar
        mRadarPaint.setColor(Color.parseColor("#06B6D4"));
        Path arrow = new Path();
        arrow.moveTo(radarX, radarY - 14);
        arrow.lineTo(radarX - 10, radarY + 10);
        arrow.lineTo(radarX, radarY + 4);
        arrow.lineTo(radarX + 10, radarY + 10);
        arrow.close();
        canvas.drawPath(arrow, mRadarPaint);

        // North indicator
        mTextPaint.setTextSize(18f);
        mTextPaint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawText("N", radarX - 6, radarY - radarR + 22, mTextPaint);

        // 8. Virtual Joystick (Bottom Left)
        if (mJoystickActive) {
            mRadarPaint.setColor(Color.parseColor("#44FFFFFF"));
            canvas.drawCircle(mJoystickBaseX, mJoystickBaseY, JOYSTICK_RADIUS, mRadarPaint);

            mRadarPaint.setColor(Color.parseColor("#AA38BDF8"));
            canvas.drawCircle(mJoystickThumbX, mJoystickThumbY, 35f, mRadarPaint);
        }
    }

    private void drawPalmTree(Canvas canvas, float x, float y, float scale) {
        Paint trunk = new Paint(Paint.ANTI_ALIAS_FLAG);
        trunk.setColor(Color.parseColor("#451A03"));
        trunk.setStrokeWidth(12f * scale);
        canvas.drawLine(x, y, x - 15f * scale, y - 90f * scale, trunk);

        Paint leaves = new Paint(Paint.ANTI_ALIAS_FLAG);
        leaves.setColor(Color.parseColor("#15803D"));
        leaves.setStrokeWidth(8f * scale);

        float topX = x - 15f * scale;
        float topY = y - 90f * scale;

        canvas.drawLine(topX, topY, topX - 50f * scale, topY - 25f * scale, leaves);
        canvas.drawLine(topX, topY, topX + 50f * scale, topY - 25f * scale, leaves);
        canvas.drawLine(topX, topY, topX - 40f * scale, topY + 25f * scale, leaves);
        canvas.drawLine(topX, topY, topX + 40f * scale, topY + 25f * scale, leaves);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (x < getWidth() * 0.45f && y > getHeight() * 0.45f) {
                    mJoystickActive = true;
                    mJoystickBaseX = x;
                    mJoystickBaseY = y;
                    mJoystickThumbX = x;
                    mJoystickThumbY = y;
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mJoystickActive) {
                    float dx = x - mJoystickBaseX;
                    float dy = y - mJoystickBaseY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    if (dist > JOYSTICK_RADIUS) {
                        mJoystickThumbX = mJoystickBaseX + (dx / dist) * JOYSTICK_RADIUS;
                        mJoystickThumbY = mJoystickBaseY + (dy / dist) * JOYSTICK_RADIUS;
                    } else {
                        mJoystickThumbX = x;
                        mJoystickThumbY = y;
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mJoystickActive = false;
                mJoystickThumbX = mJoystickBaseX;
                mJoystickThumbY = mJoystickBaseY;
                return true;
        }

        return super.onTouchEvent(event);
    }
}
