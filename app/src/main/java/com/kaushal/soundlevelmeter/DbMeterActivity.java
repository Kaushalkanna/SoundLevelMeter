package com.kaushal.soundlevelmeter;

/**
 * Created by xkxd061 on 7/26/16.
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.DecimalFormat;

public class DbMeterActivity extends Activity implements AudioInputListener {
    private final Handler handler = new Handler();
    Runnable runTimer;
    AudioInput micInput;
    TextView primaryDb;
    TextView fractionDb;
    private TextView gainDb;
    private LineGraphSeries<DataPoint> graphData;
    private double graphLastXValue = -1d;
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
    int graphXMin = 0;
    int graphXMax = 250;
    int graphYMin = 0;
    int graphYMax = 120;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        micInput = new AudioInput(this);
        setContentView(R.layout.db_meter_main);

        primaryDb = (TextView) findViewById(R.id.dBTextView);
        fractionDb = (TextView) findViewById(R.id.dBFractionTextView);
        gainDb = (TextView) findViewById(R.id.gain);
        graph = (GraphView) findViewById(R.id.graph);
        Button minus5dbButton = (Button) findViewById(R.id.minus_5_db_button);
        Button minus1dbButton = (Button) findViewById(R.id.minus_1_db_button);
        Button plus1dbButton = (Button) findViewById(R.id.plus_1_db_button);
        Button plus5dbButton = (Button) findViewById(R.id.plus_5_db_button);
        final ToggleButton onOffButton = (ToggleButton) findViewById(R.id.on_off_toggle_button);
        final Button settingsButton = (Button) findViewById(R.id.settingsButton);
        final Button infoButton = (Button) findViewById(R.id.infobutton);


        graphData = new LineGraphSeries<DataPoint>();
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(graphXMin);
        graph.getViewport().setMaxX(graphXMax);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(graphYMin);
        graph.getViewport().setMaxY(graphYMax);
        graph.getViewport().setScrollable(true);
        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);
        staticLabelsFormatter.setVerticalLabels(new String[] {"0", "30", "60", "90", "120"});
        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);
        graph.setClickable(true);

        ToggleButton.OnClickListener toggleButtonListener = new ToggleButton.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onOffButton.isChecked()) {
                    startListening();
                } else {
                    micInput.stop();
                }
            }
        };
        onOffButton.setOnClickListener(toggleButtonListener);

        DbClickListener minus5dBButtonListener = new DbClickListener(-5.0);
        DbClickListener minus1dBButtonListener = new DbClickListener(-1.0);
        DbClickListener plus1dBButtonListener = new DbClickListener(1.0);
        DbClickListener plus5dBButtonListener = new DbClickListener(5.0);


        minus5dbButton.setOnClickListener(minus5dBButtonListener);
        minus1dbButton.setOnClickListener(minus1dBButtonListener);
        plus1dbButton.setOnClickListener(plus1dBButtonListener);
        plus5dbButton.setOnClickListener(plus5dBButtonListener);


        Button.OnClickListener settingsButtonListener = new Button.OnClickListener() {
            public void onClick(View v) {
                onOffButton.setChecked(false);
                DbMeterActivity.this.micInput.stop();
                showSettingsDialog();
            }

        };
        settingsButton.setOnClickListener(settingsButtonListener);

        Button.OnClickListener infoButtonListener = new Button.OnClickListener() {
            public void onClick(View v) {
                final AlertDialog.Builder infoDialog = new AlertDialog.Builder(DbMeterActivity.this);
                infoDialog.setTitle("Help:");
                infoDialog.setMessage(R.string.info_message);
                infoDialog.setPositiveButton("OK",null);
                infoDialog.show();

            }

        };
        infoButton.setOnClickListener(infoButtonListener);
    }

    private void startListening() {
        readPreferences();
        micInput.setSampleRate(sampleRate);
        micInput.setAudioSource(audioSource);
        micInput.start();
        graph.addSeries(graphData);
    }

    private void showSettingsDialog() {
        settingsDialog = new Dialog(DbMeterActivity.this);
        settingsDialog.setContentView(R.layout.db_sample_settings);
        Spinner spinner = (Spinner) settingsDialog.findViewById(R.id.spinnerSampleRate);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                DbMeterActivity.this, R.array.sample_rate_array, android.R.layout.simple_spinner_item);
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
                        DbMeterActivity.this.setPreferences();
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
            DbMeterActivity.this.gain *= Math.pow(10, gainIncrement / 20.0);
            differenceFromNominal -= gainIncrement;
            DecimalFormat df = new DecimalFormat("##.# dB");
            gainDb.setText(df.format(differenceFromNominal));
        }
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
                    graphData.appendData(new DataPoint(graphLastXValue, 20 + rmsdB), true, graphXMax);
                    DecimalFormat df = new DecimalFormat("##");
                    primaryDb.setText(df.format(20 + rmsdB));
                    int one_decimal = (int) (Math.round(Math.abs(rmsdB * 10))) % 10;
                    fractionDb.setText(Integer.toString(one_decimal));
                    drawing = false;
                }
            };
            handler.postDelayed(runTimer, 100);
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
            DbMeterActivity.this.sampleRate =
                    Integer.parseInt(parent.getItemAtPosition(pos).toString());
        }

        @Override
        public void onNothingSelected(AdapterView parent) {
            // Do nothing.
        }
    }

    private void readPreferences() {
        SharedPreferences preferences = getSharedPreferences("SoundLevelMeter", MODE_PRIVATE);
        sampleRate = preferences.getInt("SampleRate", 8000);
        audioSource = preferences.getInt("AudioSource", MediaRecorder.AudioSource.VOICE_RECOGNITION);
    }

    private void setPreferences() {
        SharedPreferences preferences = getSharedPreferences("SoundLevelMeter", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("SampleRate", sampleRate);
        editor.apply();
    }
}
