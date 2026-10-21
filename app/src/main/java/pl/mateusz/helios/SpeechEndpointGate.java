package pl.mateusz.helios;

/** Sample-clock endpointing: never ends before HA confirms speech or on idle noise alone. */
final class SpeechEndpointGate {
    private final int silenceTargetMs;
    private int elapsedMs,speechMs,silenceMs;
    SpeechEndpointGate(int silenceTargetMs){
        if(silenceTargetMs!=300&&silenceTargetMs!=450)throw new IllegalArgumentException("Unsupported silence duration");
        this.silenceTargetMs=silenceTargetMs;
    }
    boolean accept(boolean speech,int frameMs,boolean haSpeechStarted){
        elapsedMs+=frameMs;
        if(speech){speechMs+=frameMs;silenceMs=0;}
        else if(haSpeechStarted&&speechMs>=200)silenceMs+=frameMs;
        return haSpeechStarted&&elapsedMs>=1000&&speechMs>=200&&silenceMs>=silenceTargetMs;
    }
}
