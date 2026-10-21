package pl.mateusz.helios;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class StreamingReplyStateTest {
 @Test public void startsBeforeRunEndButOnlyOnce() throws Exception{
  StreamingReplyState s=new StreamingReplyState();
  s.accept("run-start",new JSONObject("{\"tts_output\":{\"stream_response\":true,\"url\":\"/stream\"}}"));
  assertNull(s.claimUrl());
  s.accept("intent-progress",new JSONObject("{\"tts_start_streaming\":true}"));
  assertEquals("/stream",s.claimUrl());
  s.accept("tts-end",new JSONObject("{\"tts_output\":{\"url\":\"/final\"}}"));
  assertNull(s.claimUrl());
 }
 @Test public void shortOrNonStreamingRepliesUseFinalUrl() throws Exception{
  StreamingReplyState s=new StreamingReplyState();
  s.accept("run-start",new JSONObject("{\"tts_output\":{\"stream_response\":false,\"url\":\"/unused\"}}"));
  s.accept("intent-progress",new JSONObject("{\"tts_start_streaming\":true}"));
  assertNull(s.claimUrl());
  s.accept("tts-end",new JSONObject("{\"tts_output\":{\"url\":\"/cached\"}}"));
  assertEquals("/cached",s.claimUrl());assertNull(s.claimUrl());
 }
 @Test public void toleratesMissingFieldsAndUnrelatedProgress() throws Exception{
  StreamingReplyState s=new StreamingReplyState();s.accept("run-start",null);
  s.accept("intent-progress",new JSONObject("{\"chat_log_delta\":{\"content\":\"hello\"}}"));
  s.accept("tts-end",new JSONObject());assertNull(s.claimUrl());
 }
}
