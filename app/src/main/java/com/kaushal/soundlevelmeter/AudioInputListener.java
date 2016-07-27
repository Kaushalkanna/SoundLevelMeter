package com.kaushal.soundlevelmeter;

/**
 * Created by xkxd061 on 7/26/16.
 */
public interface AudioInputListener {
    public void processAudioFrame(short[] audioFrame);
}
