package pl.mateusz.helios;

/** Preparation and stalled playback time out separately; a healthy long reply may finish. */
final class SpeechPlaybackWatchdog {
    static final long PREPARE_MS=30_000,STALL_MS=45_000,GRACE_MS=60_000,UNKNOWN_MAX_MS=600_000;
    private final long requestedAt;
    private long startedAt=-1,lastProgressAt,endBy;private int position=-1;
    SpeechPlaybackWatchdog(long now){requestedAt=now;}
    synchronized void started(long now,int durationMs){
        startedAt=lastProgressAt=now;
        endBy=now+(durationMs>0?(long)durationMs+GRACE_MS:UNKNOWN_MAX_MS);
    }
    synchronized boolean isStarted(){return startedAt>=0;}
    synchronized void progress(long now,int currentPosition){
        if(startedAt>=0&&currentPosition>position){position=currentPosition;lastProgressAt=now;}
    }
    synchronized String timeout(long now){
        if(startedAt<0)return now-requestedAt>=PREPARE_MS?"Audio preparation timed out":null;
        if(now-lastProgressAt>=STALL_MS)return "Speech playback stalled";
        if(now>=endBy)return "Speech playback exceeded duration allowance";
        return null;
    }
}
