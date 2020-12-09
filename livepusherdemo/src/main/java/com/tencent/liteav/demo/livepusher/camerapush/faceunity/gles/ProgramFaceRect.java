package com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles;

import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles.core.Drawable2d;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles.core.GlUtil;
import com.tencent.liteav.demo.livepusher.camerapush.faceunity.gles.core.Program;


/**
 * 矩形边框
 *
 * @author LiuQiang on 2018.08.23
 */
public class ProgramFaceRect extends Program {

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 aPosition;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * aPosition;" +
                    "}";
    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
                    "uniform vec4 uColor;" +
                    "void main() {" +
                    "  gl_FragColor = uColor;" +
                    "}";

    private static final float[] LINE_COLOR = {0.64f, 0.77f, 0.22f, 1.0f};
    private static final float LINE_WIDTH = 6.0f;
    private int mMvpMatrixHandle;
    private int mPositionHandle;
    private int mColorHandle;

    public ProgramFaceRect() {
        super(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    @Override
    protected Drawable2d getDrawable2d() {
        return new Drawable2d(new float[4 * 2]);
    }

    @Override
    protected void getLocations() {
        mMvpMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        mColorHandle = GLES20.glGetUniformLocation(mProgramHandle, "uColor");
    }

    @Override
    public void drawFrame(int textureId, float[] texMatrix, float[] mvpMatrix) {
        GLES20.glUseProgram(mProgramHandle);
        GLES20.glLineWidth(LINE_WIDTH);
        GLES20.glUniformMatrix4fv(mMvpMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, Drawable2d.COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
                Drawable2d.COORDS_PER_VERTEX * GlUtil.SIZEOF_FLOAT, mDrawable2d.vertexArray());
        GLES20.glUniform4fv(mColorHandle, 1, LINE_COLOR, 0);
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4);
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glUseProgram(0);
    }

    public void drawFrame(int x, int y, int width, int height) {
        drawFrame(0, null, mMvpMatrix, x, y, width, height);
    }

    private int mCameraFacing;
    private int mCameraOrientation;
    private int mCameraWidth;
    private int mCameraHeight;
    private final float[] mMvpMatrix = new float[16];
    private final float[] mFaceRectData = new float[8];

    public void refresh(float[] faceRectData, int cameraWidth, int cameraHeight, int cameraOrientation,
                        int cameraFacing, float[] mvpMatrix) {
        int rectWidth = (int) (faceRectData[2] - faceRectData[0]);
        int rectHeight = (int) (faceRectData[3] - faceRectData[1]);
        if (rectWidth <= 0 || rectHeight <= 0) {
            return;
        }

        if (mCameraWidth != cameraWidth || mCameraHeight != cameraHeight
                || mCameraOrientation != cameraOrientation || mCameraFacing != cameraFacing) {
            float[] orthoMtx = new float[16];
            Matrix.orthoM(orthoMtx, 0, 0, cameraWidth, 0, cameraHeight, -1, 1);
            float[] rotateMtx = new float[16];
            Matrix.setRotateM(rotateMtx, 0, 360 - cameraOrientation, 0.0f, 0.0f, 1.0f);
            if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                Matrix.rotateM(rotateMtx, 0, 180, 1.0f, 0.0f, 0.0f);
            }
            float[] retMtx = new float[16];
            Matrix.multiplyMM(retMtx, 0, rotateMtx, 0, orthoMtx, 0);
            Matrix.multiplyMM(mMvpMatrix, 0, mvpMatrix, 0, retMtx, 0);

            mCameraWidth = cameraWidth;
            mCameraHeight = cameraHeight;
            mCameraOrientation = cameraOrientation;
            mCameraFacing = cameraFacing;
        }

        mFaceRectData[0] = cameraWidth - faceRectData[0];
        mFaceRectData[1] = cameraHeight - faceRectData[1];
        mFaceRectData[2] = cameraWidth - faceRectData[2];
        mFaceRectData[3] = mFaceRectData[1];
        mFaceRectData[4] = mFaceRectData[2];
        mFaceRectData[5] = cameraHeight - faceRectData[3];
        mFaceRectData[6] = mFaceRectData[0];
        mFaceRectData[7] = mFaceRectData[5];

        updateVertexArray(mFaceRectData);
    }

}
