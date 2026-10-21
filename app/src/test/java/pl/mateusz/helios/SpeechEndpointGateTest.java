package pl.mateusz.helios;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeechEndpointGateTest {
 @Test public void idleNoiseAndUnconfirmedSpeechCannotEndUpload(){
  SpeechEndpointGate g=new SpeechEndpointGate(300);
  for(int i=0;i<100;i++)assertFalse(g.accept(false,20,true));
  for(int i=0;i<100;i++)assertFalse(g.accept(i<20,20,false));
 }
 @Test public void endsAfter300msAndResetsOnSpeech(){
  SpeechEndpointGate g=new SpeechEndpointGate(300);
  for(int i=0;i<50;i++)assertFalse(g.accept(true,20,true));
  for(int i=0;i<10;i++)assertFalse(g.accept(false,20,true));
  assertFalse(g.accept(true,20,true));
  for(int i=0;i<14;i++)assertFalse(g.accept(false,20,true));
  assertTrue(g.accept(false,20,true));
 }
 @Test public void balancedIsSlowerAndMinimumUtteranceIsRetained(){
  SpeechEndpointGate g=new SpeechEndpointGate(450);
  for(int i=0;i<10;i++)assertFalse(g.accept(true,20,true));
  for(int i=0;i<39;i++)assertFalse(g.accept(false,20,true));
  assertTrue(g.accept(false,20,true));
 }
}
