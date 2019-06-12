package com.link.simplesports;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SensorEventListener {
    TextView count;
    Button squat;
    Button liftArm;
    Button climb;
    private SensorManager mSensorManager;
    //识别深蹲和弯举的阈值
    private int mMaxValue, mMinValue;
    private long mMaxTime, mMinTime;
    private long mMaxDTime, mMinDTime;
    //三轴与垂直方向的角度
    private int mAngleX, mAngleY, mAngleZ;
    //三轴重力加速度
    private float[] gravity;

    //识别爬楼的阈值
    private boolean mThreshold;
    public static float SENSITIVITY = 3; // 灵敏度
    private float mLastValues[] = new float[3 * 2];
    private float mScale[] = new float[2];
    private float mYOffset;
    private static long end = 0;
    private static long start = 0;
    //最后加速度方向
    private float mLastDirections[] = new float[3 * 2];
    private float mLastExtremes[][] = { new float[3 * 2], new float[3 * 2] };
    private float mLastDiff[] = new float[3 * 2];
    private int mLastMatch = -1;

    private DecimalFormat df = new DecimalFormat("0.0");

    private int type;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        count = findViewById(R.id.text);
        squat = findViewById(R.id.squat);
        liftArm = findViewById(R.id.liftArm);
        climb = findViewById(R.id.climb);


        int h = 480;
        mYOffset = h * 0.5f;
        mScale[0] = -(h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
        mScale[1] = -(h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        count.setText(0+"");
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.squat){
            type = 0;
        }else if(view.getId() == R.id.liftArm){
            type = 1;
        }else if(view.getId() == R.id.climb){
            type = 2;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
       if(type == 0){
           mMaxValue = 13;
           mMinValue = 7;
           mMaxDTime = 480;
           mMinDTime = 220;
           startSquatOrLiftArm(sensorEvent);
       }else if(type == 1){
           mMaxValue = 16;
           mMinValue = 5;
           mMaxDTime = 310;
           mMinDTime = 190;
           startSquatOrLiftArm(sensorEvent);

       }else if (type == 2){
          mUpHeight = 0;
          mPreHeight = 0;
           startClimb(sensorEvent);
       }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    double mUpHeight = 0;
    double mPreHeight = 0;

    private void startClimb(SensorEvent se){
        Sensor sensor = se.sensor;
        synchronized (this) {

            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float vSum = 0;

                for (int i = 0; i < 3; i++) {
                    final float v = mYOffset + se.values[i] * mScale[1];
                    vSum += v;
                }
                int k = 0;
                float v = vSum / 3;
                float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));

                if (direction == -mLastDirections[k]) {
                    int extType = (direction > 0 ? 0 : 1);
                    mLastExtremes[extType][k] = mLastValues[k];
                    float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

                    if (diff > SENSITIVITY) {
                        boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k] * 2 / 3);
                        boolean isPreviousLargeEnough = mLastDiff[k] > (diff / 3);
                        boolean isNotContra = (mLastMatch != 1 - extType);

                        if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) {
                            end = System.currentTimeMillis();

                            if (end - start > 150) {
                                mThreshold = true;
                                mLastMatch = extType;
                                start = end;
                            }
                        } else {
                            mLastMatch = -1;
                        }
                    }
                    mLastDiff[k] = diff;
                }

                mLastDirections[k] = direction;
                mLastValues[k] = v;
            }

            if (se.sensor.getType() == Sensor.TYPE_PRESSURE) {

                if(!mThreshold){
                    return;
                }

                float pressure = se.values[0];
                df.getRoundingMode();
                // 计算海拔
                double height = Double.parseDouble(df.format(44330000 * (1 - (Math.pow((Double.parseDouble(df.format(pressure)) / 1013.25),
                        (float) 1.0 / 5255.0)))));

                //判断是否向上走，向上才有效
                if (height - mPreHeight >= 0.8 && height - mPreHeight <= 1.8) {

                    if (mPreHeight > 0) {
                        mUpHeight += (height - mPreHeight);
                        count.setText(Double.parseDouble(df.format((mUpHeight)))+"");
                    }

                }

                if(height - mPreHeight >= 0.8 || mPreHeight-height>=0.8 || mPreHeight == 0){
                    mPreHeight = height;
                }

                mThreshold = false;
            }

        }
    }

    private void startSquatOrLiftArm(SensorEvent se){
        int thiscount = 0;
        if (se.sensor.getType() == Sensor.TYPE_GRAVITY) {
            gravity = se.values;
            double rX = gravity[0] / SensorManager.GRAVITY_EARTH;
            double rY = gravity[1] / SensorManager.GRAVITY_EARTH;
            double rZ = gravity[2] / SensorManager.GRAVITY_EARTH;
            mAngleX = (int) Math.ceil(Math.toDegrees(Math.acos(rX)));
            mAngleY = (int) Math.ceil(Math.toDegrees(Math.acos(rY)));
            mAngleZ = (int) Math.ceil(Math.toDegrees(Math.acos(rZ)));
        }

        if (se.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float gravityNew = (float) Math.abs(Math.sqrt(se.values[0] * se.values[0]
                    + se.values[1] * se.values[1] + se.values[2] * se.values[2]));
            BigDecimal bdXX = new BigDecimal(se.values[0] * Math.cos(Math.toRadians(mAngleX)));
            BigDecimal bdYY = new BigDecimal(se.values[1] * Math.cos(Math.toRadians(mAngleY)));
            BigDecimal bdZZ = new BigDecimal(se.values[2] * Math.cos(Math.toRadians(mAngleZ)));
            double dbX = bdXX.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
            double dbY = bdYY.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
            double dbZ = bdZZ.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
            double totalV = Math.abs(dbX + dbY + dbZ);

            if (totalV >= 8 && totalV <= 11) {
                return;
            }

            if (gravityNew > mMaxValue) {
                mMaxTime = System.currentTimeMillis();
            }

            if (mMaxTime != 0 && System.currentTimeMillis() - mMaxTime > mMaxDTime) {
                if (gravityNew <= mMinValue) {
                    mMinTime = System.currentTimeMillis();
                }
            }

            if (mMinTime != 0 && System.currentTimeMillis() - mMinTime > mMinDTime) {
                Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
                if (v.hasVibrator()) {
                    v.vibrate(200);
                }
                count.setText((thiscount++) + "");
                mMinTime = 0;
                mMaxTime = 0;
            }
        }
    }
}
