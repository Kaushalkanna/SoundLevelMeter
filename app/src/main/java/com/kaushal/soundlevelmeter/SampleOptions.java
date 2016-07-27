package com.kaushal.soundlevelmeter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

/**
 * Created by xkxd061 on 7/26/16.
 */
public class SampleOptions extends Activity {

    private int mSampleRate;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        readPreferences();

        /**
         * Drop down selection of sample rate.
         */
        Spinner spinner = (Spinner) findViewById(R.id.sampleRateSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.sample_rate_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new MyOnItemSelectedListener());

        spinner.setPrompt(Integer.toString(mSampleRate));
        int spinnerPosition = adapter.getPosition(Integer.toString(mSampleRate));

        spinner.setSelection(spinnerPosition);

        /**
         * Ok button dismiss settings.
         */
        Button okButton = (Button) findViewById(R.id.settingsOkButton);
        Button.OnClickListener okBtnListener =
                new Button.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Dismiss this dialog.
                        SampleOptions.this.setPreferences();
                        finish();

                    }
                };
        okButton.setOnClickListener(okBtnListener);
    }

    public class MyOnItemSelectedListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> parent,
                                   View view, int pos, long id) {
            SampleOptions.this.mSampleRate =
                    Integer.parseInt(parent.getItemAtPosition(pos).toString());
        }

        @Override
        public void onNothingSelected(AdapterView parent) {
            // Do nothing.
        }
    }

    private void readPreferences() {
        SharedPreferences preferences = getSharedPreferences("LevelMeter",
                MODE_PRIVATE);
        mSampleRate = preferences.getInt("SampleRate", 8000);
    }

    private void setPreferences() {
        SharedPreferences preferences = getSharedPreferences("LevelMeter",
                MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putInt("SampleRate", mSampleRate);
        editor.commit();
    }
}
