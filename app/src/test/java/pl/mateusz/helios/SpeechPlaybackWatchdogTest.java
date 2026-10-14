package pl.mateusz.helios;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeechPlaybackWatchdogTest {
 @Test public void healthyLongReplyDoesNotStopAtThirtySeconds(){
  SpeechPlaybackWatchdog w=new SpeechPlaybackWatchdog(0);w.started(500,90_000);
  for(int pos=0;pos<=90_000;pos+=1000){w.progress(500+pos,pos);assertNull(w.timeout(500+pos));}
 }
 @Test public void preparationAndStallAreSeparateFailures(){
  SpeechPlaybackWatchdog w=new SpeechPlaybackWatchdog(0);
  assertNull(w.timeout(29_999));assertEquals("Audio preparation timed out",w.timeout(30_000));
  w.started(30_000,120_000);w.progress(31_000,1000);
  assertNull(w.timeout(75_999));assertEquals("Speech playback stalled",w.timeout(76_000));
 }
 @Test public void unknownDurationCanStreamBeyondThirtySecondsButIsBounded(){
  SpeechPlaybackWatchdog w=new SpeechPlaybackWatchdog(0);w.started(0,0);
  w.progress(120_000,120_000);assertNull(w.timeout(120_000));
  w.progress(600_000,600_000);assertEquals("Speech playback exceeded duration allowance",w.timeout(600_000));
 }
}
