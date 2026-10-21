package pl.mateusz.helios;

import org.json.JSONObject;

/** One playback per run; the stream is claimed only after HA says TTS is ready. */
final class StreamingReplyState {
    private String streamUrl,finalUrl;
    private boolean streamReady,claimed;
    void accept(String type,JSONObject data){
        if(data==null)return;
        if(type.equals("run-start")){
            JSONObject output=data.optJSONObject("tts_output");
            if(output!=null&&output.optBoolean("stream_response",false))streamUrl=output.optString("url",null);
        }else if(type.equals("intent-progress")&&data.optBoolean("tts_start_streaming",false))streamReady=true;
        else if(type.equals("tts-end")){
            JSONObject output=data.optJSONObject("tts_output");
            if(output!=null)finalUrl=output.optString("url",null);
        }
    }
    String claimUrl(){
        if(claimed)return null;
        String url=streamReady&&streamUrl!=null?streamUrl:finalUrl;
        if(url!=null&&!url.isEmpty()){claimed=true;return url;}
        return null;
    }
}
