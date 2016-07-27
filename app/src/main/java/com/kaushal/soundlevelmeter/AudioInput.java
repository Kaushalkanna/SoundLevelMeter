package com.kaushal.soundlevelmeter;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Created by xkxd061 on 7/26/16.
 */
public class AudioInput implements Runnable{
    int mSampleRate = 8000;
    int mAudioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    final int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
    final int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;

    private final AudioInputListener mListener;
    private Thread mThread;
    private boolean mRunning;

    AudioRecord recorder;

    int mTotalSamples = 0;

    private static final String TAG = "AudioInput";

    public AudioInput(AudioInputListener listener) {
        mListener = listener;
    }

    public void setSampleRate(int sampleRate) {
        mSampleRate = sampleRate;
    }

    public void setAudioSource(int audioSource) {
        mAudioSource = audioSource;
    }

    public void start() {
        if (false == mRunning) {
            mRunning = true;
            mThread = new Thread(this);
            mThread.start();
        }
    }

    public void stop() {
        try {
            if (mRunning) {
                mRunning = false;
                mThread.join();
            }
        } catch (InterruptedException e) {
            Log.v(TAG, "InterruptedException.", e);
        }
    }

    @Override
    public void run() {
        short[] buffer20ms = new short[mSampleRate / 50];
        int buffer1000msSize = bufferSize(mSampleRate, mChannelConfig,
                mAudioFormat);

        try {
            recorder = new AudioRecord(
                    mAudioSource,
                    mSampleRate,
                    mChannelConfig,
                    mAudioFormat,
                    buffer1000msSize);
            recorder.startRecording();

            while (mRunning) {
                int numSamples = recorder.read(buffer20ms, 0, buffer20ms.length);
                mTotalSamples += numSamples;
                mListener.processAudioFrame(buffer20ms);
            }
            recorder.stop();
        } catch(Throwable x) {
            Log.v(TAG, "Error reading audio", x);
        } finally {
        }
    }

    public int totalSamples() {
        return mTotalSamples;
    }

    public void setTotalSamples(int totalSamples) {
        mTotalSamples = totalSamples;
    }

    private int bufferSize(int sampleRateInHz, int channelConfig,
                           int audioFormat) {
        int buffSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig,
                audioFormat);
        if (buffSize < sampleRateInHz) {
            buffSize = sampleRateInHz;
        }
        return buffSize;
    }
}

