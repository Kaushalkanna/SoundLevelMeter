package com.kaushal.soundlevelmeter;

/**
 * Created by xkxd061 on 7/26/16.
 */

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.DecimalFormat;

public class MainActivity extends Activity implements AudioInputListener {
    private final Handler handler = new Handler();
    Runnable runTimer;
    AudioInput micInput;
    TextView primaryDb;
    TextView fractionDb;
    private TextView gainDb;
    private LineGraphSeries<DataPoint> graphData;
    private double graphLastXValue = 5d;
    double gain = 2500.0 / Math.pow(10.0, 90.0 / 20.0);
    double differenceFromNominal = 0.0;
    double smoothedRms;
    double mAlpha = 0.9;
    private int sampleRate;
    private int audioSource;
    private volatile boolean drawing;
    private volatile int drawingCollided;
    GraphView graph;
    Dialog settingsDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        micInput = new AudioInput(this);
        setContentView(R.layout.activity_main);
        primaryDb = (TextView) findViewById(R.id.dBTextView);
        fractionDb = (TextView) findViewById(R.id.dBFractionTextView);
        gainDb = (TextView) findViewById(R.id.gain);
        graph = (GraphView) findViewById(R.id.graph);
        graphData = new LineGraphSeries<DataPoint>();
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(20);
        final ToggleButton onOffButton = (ToggleButton) findViewById(R.id.on_off_toggle_button);
        ToggleButton.OnClickListener tbListener =
                new ToggleButton.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (onOffButton.isChecked()) {
                            readPreferences();
                            micInput.setSampleRate(sampleRate);
                            micInput.setAudioSource(audioSource);
                            micInput.start();
                            graph.addSeries(graphData);
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
        final Button settingsButton = (Button) findViewById(R.id.settingsButton);
        Button.OnClickListener settingsBtnListener = new Button.OnClickListener() {
            public void onClick(View v) {
                onOffButton.setChecked(false);
                MainActivity.this.micInput.stop();
                showSettingsDialog();
            }

        };
        settingsButton.setOnClickListener(settingsBtnListener);
    }

    private void showSettingsDialog() {
        settingsDialog = new Dialog(MainActivity.this);
        settingsDialog.setContentView(R.layout.sample_settings);
        settingsDialog.setTitle("Settings:");
        Spinner spinner = (Spinner) settingsDialog.findViewById(R.id.spinnerSampleRate);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                MainActivity.this, R.array.sample_rate_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new MyOnItemSelectedListener());
        spinner.setPrompt(Integer.toString(sampleRate));
        int spinnerPosition = adapter.getPosition(Integer.toString(sampleRate));
        spinner.setSelection(spinnerPosition);


        Button okButton = (Button) settingsDialog.findViewById(R.id.settingsOkButton);
        Button.OnClickListener okBtnListener =
                new Button.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        MainActivity.this.setPreferences();
                        settingsDialog.dismiss();

                    }
                };
        okButton.setOnClickListener(okBtnListener);
        settingsDialog.show();
    }

    private class DbClickListener implements Button.OnClickListener {
        private double gainIncrement;

        public DbClickListener(double gainIncrement) {
            this.gainIncrement = gainIncrement;
        }

        @Override
        public void onClick(View v) {
            MainActivity.this.gain *= Math.pow(10, gainIncrement / 20.0);
            differenceFromNominal -= gainIncrement;
            DecimalFormat df = new DecimalFormat("##.# dB");
            gainDb.setText(df.format(differenceFromNominal));
        }
    }

    private void readPreferences() {
        SharedPreferences preferences = getSharedPreferences("SoundLevelMeter", MODE_PRIVATE);
        sampleRate = preferences.getInt("SampleRate", 8000);
        audioSource = preferences.getInt("AudioSource", MediaRecorder.AudioSource.VOICE_RECOGNITION);
    }

    @Override
    public void processAudioFrame(short[] audioFrame) {
        if (!drawing) {
            drawing = true;
            final double rmsdB = getRmsdB(audioFrame);
            runTimer = new Runnable() {
                @Override
                public void run() {
                    graphLastXValue += 1d;
                    graphData.appendData(new DataPoint(graphLastXValue, 20 + rmsdB), true, 40);
                    DecimalFormat df = new DecimalFormat("##");
                    primaryDb.setText(df.format(20 + rmsdB));
                    int one_decimal = (int) (Math.round(Math.abs(rmsdB * 10))) % 10;
                    fractionDb.setText(Integer.toString(one_decimal));
                    drawing = false;
                }
            };
            handler.postDelayed(runTimer, 100);
        } else {
            drawingCollided++;
            Log.v("LevelMeterActivity", "Level bar update collision, i.e. update took longer " +
                    "than 20ms. Collision count" + Double.toString(drawingCollided));
        }
    }

    private double getRmsdB(short[] audioFrame) {
        double rms = 0;
        for (int i = 0; i < audioFrame.length; i++) {
            rms += audioFrame[i] * audioFrame[i];
        }
        rms = Math.sqrt(rms / audioFrame.length);
        smoothedRms = smoothedRms * mAlpha + (1 - mAlpha) * rms;
        return 20.0 * Math.log10(gain * smoothedRms);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public class MyOnItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            MainActivity.this.sampleRate =
                    Integer.parseInt(parent.getItemAtPosition(pos).toString());
        }
        @Override
        public void onNothingSelected(AdapterView parent) {
            // Do nothing.
        }
    }

    private void setPreferences() {
        SharedPreferences preferences = getSharedPreferences("SoundLevelMeter",
                MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("SampleRate", sampleRate);
        editor.apply();
    }
}
