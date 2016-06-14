package io.snapback.magicwhand.util;

/**
 * Created by joaobiriba on 13/06/16.
 */

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class InclinationDetector implements SensorEventListener {

    private Context mContext;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetic;
    private float[] accelerometerValues = new float[3];
    private float[] magneticValues = new float[3];

    public InclinationDetector(Context context) {
        mContext = context;
    }

    public void resume() {
        sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void pause() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public int getInclination() {
        float[] rotationMatrix = new float[9];
        float[] inclinationMatrix = new float[9];
        SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelerometerValues, magneticValues);
        int inclination = (int) Math.round(Math.toDegrees(Math.acos(rotationMatrix[8])));
        return inclination;
    }

    public int getHeadingDeg() {
        int headingDeg = -999;
        if (accelerometerValues != null && magneticValues != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, accelerometerValues,
                    magneticValues);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                float headingRad = orientation[0];
                headingDeg = (int)((float)Math.toDegrees(headingRad)+360)%360;
            }
        }
        return headingDeg;
    }

    public boolean isHorizontal() {
        int inclination = this.getInclination();
        int thrDeg = 30; // Tolerance
        return inclination < thrDeg;
    }

    public boolean isVertical() {
        final int thrDeg = 30; // Tolerance
        int inclination = this.getInclination();
        return (inclination > (90 - thrDeg)) && (inclination < (90 + thrDeg));
    }

    private float getXAcceleration()
    {
        return accelerometerValues[0];
    }

    private float getYAcceleration()
    {
        return accelerometerValues[1];
    }

    private float getZAcceleration()
    {
        return accelerometerValues[2];
    }

    private boolean isCloseToZero(double val)
    {
        return (Math.abs(val) < 1.5);
    }

    private boolean isCloseToGPos(double val)
    {
        return (val > 8.5);
    }

    private boolean isCloseToGNeg(double val)
    {
        return (val < -8.5);
    }

    public String getAccelerationStringValues()
    {
        return "(x=" + getXAcceleration() + " y=" + getYAcceleration() + "z=" + getZAcceleration() + ")";
    }

    public boolean isHorizontalFaceUp()
    {
		/*
		x = 0;
		y = 0;
		z = 9.8;
		*/

        if(isCloseToZero(getXAcceleration()) && isCloseToZero(getYAcceleration()) && isCloseToGPos(getZAcceleration()))
        {
            return true;
        }

        return false;
    }

    public boolean isHorizontalFaceDown()
    {
		/*
		x = 0;
		y = 0;
		z = -9.8;
		*/

        if(isCloseToZero(getXAcceleration()) && isCloseToZero(getYAcceleration()) && isCloseToGNeg(getZAcceleration()))
        {
            return true;
        }

        return false;
    }

    public boolean isVerticalUp()
    {
		/*
		x = 0;
		y = 9.8;
		z = 0;
		*/

        if(isCloseToZero(getXAcceleration()) && isCloseToGPos(getYAcceleration()) && isCloseToZero(getZAcceleration()))
        {
            return true;
        }

        return false;
    }

    public boolean isVerticalDown()
    {
		/*
		x = 0;
		y = -9.8;
		z = 0;
		*/

        if(isCloseToZero(getXAcceleration()) && isCloseToGNeg(getYAcceleration()) && isCloseToZero(getZAcceleration()))
        {
            return true;
        }

        return false;
    }

    public boolean isVerticalLeftSide()
    {
		/*
		x = 9.8;
		y = 0;
		z = 0;
		*/

        if(isCloseToGPos(getXAcceleration()) && isCloseToZero(getYAcceleration()) && isCloseToZero(getZAcceleration()))
        {
            return true;
        }

        return false;
    }

    public boolean isVerticalRightSide()
    {
		/*
		x = -9.8;
		y = 0;
		z = 0;
		*/

        if(isCloseToGNeg(getXAcceleration()) && isCloseToZero(getYAcceleration()) && isCloseToZero(getZAcceleration()))
        {
            return true;
        }

        return false;
    }

    public boolean isVerticalSide()
    {
        return isVerticalRightSide() || isVerticalLeftSide();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        float[] dst;
        if (event.sensor == accelerometer) {
            dst = accelerometerValues;
        } else {
            dst = magneticValues;
        }
        System.arraycopy(event.values, 0, dst, 0, 3);
    }
}
