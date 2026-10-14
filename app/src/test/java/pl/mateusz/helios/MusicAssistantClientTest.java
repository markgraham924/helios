package pl.mateusz.helios;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.*;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class MusicAssistantClientTest {
    static class Server extends WebSocketServer {
        final CountDownLatch ready=new CountDownLatch(1);
        final BlockingQueue<JSONObject> requests=new LinkedBlockingQueue<>();
        volatile WebSocket client;
        volatile boolean rejectToken,silentSearch,silentCmd,silentQueue,noQueue;
        Server(){super(new InetSocketAddress("127.0.0.1",0));}
        @Override public void onStart(){ready.countDown();}
        @Override public void onOpen(WebSocket ws,ClientHandshake h){client=ws;}
        @Override public void onClose(WebSocket ws,int code,String reason,boolean remote){}
        @Override public void onError(WebSocket ws,Exception ex){}
        @Override public void onMessage(WebSocket ws,String message){
            try{
                JSONObject m=new JSONObject(message);String command=m.getString("command");String id=m.getString("message_id");JSONObject args=m.optJSONObject("args");
                requests.add(m);
                JSONObject reply=new JSONObject().put("message_id",id);
                switch(command){
                    case "auth":
                        if(rejectToken||!"secret".equals(args.optString("token")))reply.put("error_code","unauthorized").put("details","Invalid token");
                        else reply.put("result",new JSONObject().put("authenticated",true).put("user",new JSONObject().put("username","helios")));
                        break;
                    case "players/all":reply.put("result",new JSONArray().put(new JSONObject().put("player_id","p1").put("name","Lenovo").put("playback_state","idle")));break;
                    case "music/search":if(silentSearch)return;reply.put("result",new JSONObject().put("tracks",new JSONArray().put(new JSONObject().put("uri","library://track/1").put("name",args.getString("search_query")))));break;
                    case "players/cmd/stop":reply.put("result",JSONObject.NULL);break;
                    case "players/cmd/pause":case "players/cmd/volume_set":if(silentCmd)return;reply.put("result",JSONObject.NULL);break;
                    case "player_queues/play_media":reply.put("result",JSONObject.NULL);break;
                    case "player_queues/get_active_queue":if(silentQueue)return;reply.put("result",noQueue?JSONObject.NULL:new JSONObject().put("queue_id",args.getString("player_id")).put("state","paused"));break;
                    default:reply.put("error_code","unknown_command").put("details","Unknown command");
                }
                ws.send(reply.toString());
            }catch(Exception e){ws.close(1011,"fake failure "+e);}
        }
        JSONObject request(String command) throws Exception {
            long until=System.currentTimeMillis()+5000;
            while(System.currentTimeMillis()<until){JSONObject r=requests.poll(5,TimeUnit.SECONDS);if(r==null)break;if(r.getString("command").equals(command))return r;}
            fail("no request "+command);return null;
        }
    }
    private Server server;
    private final BlockingQueue<String> connections=new LinkedBlockingQueue<>();
    private final BlockingQueue<JSONObject> players=new LinkedBlockingQueue<>();
    private final BlockingQueue<JSONObject> queues=new LinkedBlockingQueue<>();
    private MusicAssistantClient client(String token) throws Exception {
        server=new Server();server.start();assertTrue(server.ready.await(5,TimeUnit.SECONDS));
        MusicAssistantClient c=new MusicAssistantClient("ws://127.0.0.1:"+server.getPort()+"/ws",token,new MusicAssistantClient.Listener(){
            public void onConnection(boolean connected,String detail){connections.add((connected?"up:":"down:")+detail);}
            public void onPlayerUpdated(JSONObject player){players.add(player);}
            public void onQueueUpdated(JSONObject queue){queues.add(queue);}
        });
        c.start();return c;
    }
    private String outcome(java.util.function.BiConsumer<java.util.function.Consumer<Object>,java.util.function.Consumer<String>> call) throws Exception {
        BlockingQueue<String> result=new LinkedBlockingQueue<>();
        call.accept(r->result.add("ok:"+r),e->result.add("err:"+e));
        String v=result.poll(15,TimeUnit.SECONDS);assertNotNull(v);return v;
    }

    @Test public void authenticatesFirstAndCorrelatesRepliesAndEvents() throws Exception {
        MusicAssistantClient client=client("secret");
        try{
            assertEquals("up:connected",connections.poll(5,TimeUnit.SECONDS));
            JSONObject auth=server.requests.poll(1,TimeUnit.SECONDS);assertEquals("auth",auth.getString("command"));assertEquals("secret",auth.getJSONObject("args").getString("token"));
            BlockingQueue<Object> got=new LinkedBlockingQueue<>();
            client.players(got::add,e->got.add("err:"+e));
            Object list=got.poll(5,TimeUnit.SECONDS);assertTrue(list instanceof JSONArray);assertEquals("p1",((JSONArray)list).getJSONObject(0).getString("player_id"));
            server.client.send(new JSONObject().put("event","player_updated").put("object_id","p1").put("data",new JSONObject().put("player_id","p1").put("playback_state","playing")).toString());
            assertEquals("playing",players.poll(5,TimeUnit.SECONDS).getString("playback_state"));
            assertEquals("http://127.0.0.1:"+server.getPort()+"/imageproxy?path=a%2Fb&provider=library&size=256",client.imageUrl("a/b","library",256));
        }finally{client.stop();server.stop(2000);}
    }
    @Test public void rejectedTokenReportsWithoutLeakingItAndBadQueriesAreNotSent() throws Exception {
        MusicAssistantClient rejected=client("wrong");
        try{
            String down=connections.poll(7,TimeUnit.SECONDS);assertTrue(down,down.startsWith("down:"));assertFalse(down.contains("wrong"));
            assertTrue(outcome((ok,fail)->rejected.search("ab",ok::accept,fail)).startsWith("err:"));
        }finally{rejected.stop();server.stop(2000);}
        MusicAssistantClient client=client("secret");
        try{
            assertEquals("up:connected",connections.poll(5,TimeUnit.SECONDS));
            assertEquals("err:Enter 2 to 100 characters",outcome((ok,fail)->client.search("a",ok::accept,fail)));
            assertEquals("err:Enter 2 to 100 characters",outcome((ok,fail)->client.search(new String(new char[101]).replace('\0','x'),ok::accept,fail)));
            server.requests.clear();
            assertNull(server.requests.poll(300,TimeUnit.MILLISECONDS));
            String ok=outcome((o,f)->client.search("beat",o::accept,f));
            assertTrue(ok,ok.startsWith("ok:"));
            JSONObject sent=server.request("music/search");JSONObject args=sent.getJSONObject("args");
            assertEquals(50,args.getInt("limit"));assertEquals("[\"library\"]",args.getJSONArray("providers").toString());assertFalse(args.has("library_only"));
        }finally{client.stop();server.stop(2000);}
    }
    @Test public void activeQueueHasItsOwnSupersedingLaneAndQueueEventsReachTheListener() throws Exception {
        long previous=MusicAssistantClient.TIMEOUT_MS;MusicAssistantClient.TIMEOUT_MS=1500;
        MusicAssistantClient client=client("secret");
        try{
            assertEquals("up:connected",connections.poll(5,TimeUnit.SECONDS));
            server.silentCmd=true;
            BlockingQueue<String> pause=new LinkedBlockingQueue<>();
            client.playerCommand("p1","pause",r->pause.add("ok"),pause::add);Thread.sleep(100);
            server.silentQueue=true;
            BlockingQueue<String> first=new LinkedBlockingQueue<>();
            client.activeQueue("p1",r->first.add("ok"),first::add);Thread.sleep(100); // a pending cmd does not block the queue lane
            server.silentQueue=false;
            String second=outcome((ok,fail)->client.activeQueue("p1",q->ok.accept(q.optString("state")),fail));
            assertEquals("ok:paused",second);assertEquals("superseded",first.poll(1,TimeUnit.SECONDS));
            server.noQueue=true;
            assertEquals("err:No queue",outcome((ok,fail)->client.activeQueue("p1",ok::accept,fail)));
            server.client.send(new JSONObject().put("event","queue_updated").put("object_id","p1").put("data",new JSONObject().put("queue_id","p1").put("state","idle")).toString());
            assertEquals("idle",queues.poll(3,TimeUnit.SECONDS).getString("state"));
            server.silentCmd=false;
        }finally{MusicAssistantClient.TIMEOUT_MS=previous;client.stop();server.stop(2000);}
    }
    @Test public void lanesSupersedeSearchBlockCommandsAndNeverBlockStop() throws Exception {
        long previous=MusicAssistantClient.TIMEOUT_MS;MusicAssistantClient.TIMEOUT_MS=1500;
        MusicAssistantClient client=client("secret");
        try{
            assertEquals("up:connected",connections.poll(5,TimeUnit.SECONDS));
            server.silentSearch=true;
            BlockingQueue<String> first=new LinkedBlockingQueue<>();
            client.search("first",r->first.add("ok"),first::add);
            Thread.sleep(100);
            server.silentSearch=false;
            assertEquals("ok:"+new JSONObject().put("tracks",new JSONArray().put(new JSONObject().put("uri","library://track/1").put("name","second"))),outcome((ok,fail)->client.search("second",ok::accept,fail)));
            assertEquals("superseded",first.poll(1,TimeUnit.SECONDS));
            server.silentCmd=true;
            BlockingQueue<String> pause=new LinkedBlockingQueue<>();
            client.playerCommand("p1","pause",r->pause.add("ok"),pause::add);
            Thread.sleep(100);
            assertEquals("err:Previous command is still running",outcome((ok,fail)->client.volume("p1",40,ok,fail)));
            assertEquals("ok:null",outcome((ok,fail)->client.playerCommand("p1","stop",ok,fail)));
            assertTrue(pause.poll(3,TimeUnit.SECONDS).contains("did not respond"));
            server.silentCmd=false;
            assertEquals("ok:null",outcome((ok,fail)->client.volume("p1",40,ok,fail)));
            assertEquals(40,server.request("players/cmd/volume_set").getJSONObject("args").getInt("volume_level"));
            assertEquals("ok:null",outcome((ok,fail)->client.playMedia("q1","library://album/9",true,ok,fail)));
            JSONObject play=server.request("player_queues/play_media");assertEquals("replace",play.getJSONObject("args").getString("option"));
            assertTrue(outcome((ok,fail)->client.playerCommand("p1","eject",ok,fail)).startsWith("err:"));
            server.client.close(1001,"drop");
            String down=connections.poll(5,TimeUnit.SECONDS);assertTrue(down,down.startsWith("down:"));
            assertEquals("err:No connection to Music Assistant",outcome((ok,fail)->client.players(r->ok.accept(r),fail)));
            assertEquals("up:connected",connections.poll(7,TimeUnit.SECONDS));
        }finally{MusicAssistantClient.TIMEOUT_MS=previous;client.stop();server.stop(2000);}
    }
}
