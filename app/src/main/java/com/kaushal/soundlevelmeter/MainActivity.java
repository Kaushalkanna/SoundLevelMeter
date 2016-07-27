package com.kaushal.soundlevelmeter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.DecimalFormat;

public class MainActivity extends Activity implements
        AudioInputListener {
    AudioInput micInput;
    TextView mdBTextView;
    TextView mdBFractionTextView;
    BarLevelDrawable mBarLevel;
    private TextView mGainTextView;

    double mOffsetdB = 10;
    double mGain = 2500.0 / Math.pow(10.0, 90.0 / 20.0);
    double mDifferenceFromNominal = 0.0;
    double mRmsSmoothed;
    double mAlpha = 0.9;
    private int mSampleRate;
    private int mAudioSource;
    private volatile boolean mDrawing;
    private volatile int mDrawingCollided;

    private static final String TAG = "LevelMeterActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        micInput = new AudioInput(this);

        setContentView(R.layout.activity_main);

        mBarLevel = (BarLevelDrawable) findViewById(R.id.bar_level_drawable_view);
        mdBTextView = (TextView) findViewById(R.id.dBTextView);
        mdBFractionTextView = (TextView) findViewById(R.id.dBFractionTextView);
        mGainTextView = (TextView) findViewById(R.id.gain);
        GraphView graph = (GraphView) findViewById(R.id.graph);
        LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>(new DataPoint[] {
                new DataPoint(0, 1),
                new DataPoint(1, 5),
                new DataPoint(2, 3),
                new DataPoint(3, 2),
                new DataPoint(4, 6)
        });
        graph.addSeries(series);


        final ToggleButton onOffButton = (ToggleButton) findViewById(
                R.id.on_off_toggle_button);

        ToggleButton.OnClickListener tbListener =
                new ToggleButton.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (onOffButton.isChecked()) {
                            readPreferences();
                            micInput.setSampleRate(mSampleRate);
                            micInput.setAudioSource(mAudioSource);
                            micInput.start();
                        } else {
                            micInput.stop();
                        }
                    }
                };
        onOffButton.setOnClickListener(tbListener);

        Button minus5dbButton = (Button) findViewById(R.id.minus_5_db_button);
        DbClickListener minus5dBButtonListener = new DbClickListener(-5.0);
        minus5dbButton.setOnClickListener(minus5dBButtonListener);
        Button minus1dbButton = (Button) findViewById(R.id.minus_1_db_button);
        DbClickListener minus1dBButtonListener = new DbClickListener(-1.0);
        minus1dbButton.setOnClickListener(minus1dBButtonListener);
        Button plus1dbButton = (Button) findViewById(R.id.plus_1_db_button);
        DbClickListener plus1dBButtonListener = new DbClickListener(1.0);
        plus1dbButton.setOnClickListener(plus1dBButtonListener);
        Button plus5dbButton = (Button) findViewById(R.id.plus_5_db_button);
        DbClickListener plus5dBButtonListener = new DbClickListener(5.0);
        plus5dbButton.setOnClickListener(plus5dBButtonListener);
        Button settingsButton = (Button) findViewById(R.id.settingsButton);
        Button.OnClickListener settingsBtnListener =
                new Button.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        final ToggleButton onOffButton = (ToggleButton) findViewById(
                                R.id.on_off_toggle_button);
                        onOffButton.setChecked(false);
                        MainActivity.this.micInput.stop();

                        Intent settingsIntent = new Intent(MainActivity.this,
                                SampleOptions.class);
                        MainActivity.this.startActivity(settingsIntent);
                    }
                };
        settingsButton.setOnClickListener(settingsBtnListener);
    }

    private class DbClickListener implements Button.OnClickListener {
        private double gainIncrement;

        public DbClickListener(double gainIncrement) {
            this.gainIncrement = gainIncrement;
        }

        @Override
        public void onClick(View v) {
            MainActivity.this.mGain *= Math.pow(10, gainIncrement / 20.0);
            mDifferenceFromNominal -= gainIncrement;
            DecimalFormat df = new DecimalFormat("##.# dB");
            mGainTextView.setText(df.format(mDifferenceFromNominal));
        }
    }

    private void readPreferences() {
        SharedPreferences preferences = getSharedPreferences("LevelMeter",
                MODE_PRIVATE);
        mSampleRate = preferences.getInt("SampleRate", 8000);
        mAudioSource = preferences.getInt("AudioSource",
                MediaRecorder.AudioSource.VOICE_RECOGNITION);
    }

    @Override
    public void processAudioFrame(short[] audioFrame) {
        if (!mDrawing) {
            mDrawing = true;
            double rms = 0;
            for (int i = 0; i < audioFrame.length; i++) {
                rms += audioFrame[i] * audioFrame[i];
            }
            rms = Math.sqrt(rms / audioFrame.length);

            mRmsSmoothed = mRmsSmoothed * mAlpha + (1 - mAlpha) * rms;
            final double rmsdB = 20.0 * Math.log10(mGain * mRmsSmoothed);


            mBarLevel.post(new Runnable() {
                @Override
                public void run() {
                    mBarLevel.setLevel((mOffsetdB + rmsdB) / 60);

                    DecimalFormat df = new DecimalFormat("##");
                    mdBTextView.setText(df.format(20 + rmsdB));

                    DecimalFormat df_fraction = new DecimalFormat("#");
                    int one_decimal = (int) (Math.round(Math.abs(rmsdB * 10))) % 10;
                    mdBFractionTextView.setText(Integer.toString(one_decimal));
                    mDrawing = false;
                }
            });
        } else {
            mDrawingCollided++;
            Log.v(TAG, "Level bar update collision, i.e. update took longer " +
                    "than 20ms. Collision count" + Double.toString(mDrawingCollided));
        }
    }
}
