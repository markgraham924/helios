package pl.mateusz.helios;

import com.konovalov.vad.webrtc.Vad;
import com.konovalov.vad.webrtc.VadWebRTC;
import com.konovalov.vad.webrtc.config.FrameSize;
import com.konovalov.vad.webrtc.config.Mode;
import com.konovalov.vad.webrtc.config.SampleRate;

/** Runs only on decimated 16-kHz command audio, after wake word; never stores audio. */
final class ClockSpeechEndpoint implements AutoCloseable {
    private final VadWebRTC vad;
    private final SpeechEndpointGate gate;
    private final short[] frame=new short[320];
    private int used;
    ClockSpeechEndpoint(int silenceMs){
        gate=new SpeechEndpointGate(silenceMs);
        vad=Vad.builder().setSampleRate(SampleRate.SAMPLE_RATE_16K)
                .setFrameSize(FrameSize.FRAME_SIZE_320).setMode(Mode.NORMAL)
                .setSpeechDurationMs(0).setSilenceDurationMs(0).build();
    }
    boolean accept(short[] pcm,int count,boolean haSpeechStarted){
        for(int i=0;i<count;i++){
            frame[used++]=pcm[i];
            if(used==frame.length){used=0;if(gate.accept(vad.isSpeech(frame),20,haSpeechStarted))return true;}
        }
        return false;
    }
    public void close(){vad.close();}
}
