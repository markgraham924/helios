package pl.mateusz.helios;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** A bounded read-only card: ordered HA readings, names and units, and independent missing rows. */
public class EntitiesCardTest {
    @BeforeClass public static void icons() throws Exception {MdiIconsTest.install();}

    private static JSONObject entry(String entity) throws Exception {return new JSONObject().put("entity",entity);}
    private static JSONObject card(JSONObject... entries) throws Exception {
        JSONArray rows=new JSONArray();for(JSONObject e:entries)rows.put(e);
        return DashboardSpecV6Test.item("odczyty","entities",1,1,1,1).put("entries",rows);
    }
    private static JSONObject doc(JSONObject item) throws Exception {
        return new JSONObject().put("version",6).put("pages",new JSONArray().put(DashboardSpecV6Test.page("main",null,item)));
    }
    private static void rejects(JSONObject item,String message) throws Exception {
        try{DashboardSpec.parse(doc(item));fail("accepted invalid entities card");}
        catch(IllegalArgumentException expected){assertEquals(message,expected.getMessage());}
    }
    private static final CardBodies.Env ENV=new CardBodies.Env(){
        public String time(){return "";}public String weekday(){return "";}public String date(){return "";}public String musicInfo(){return "";}public long now(){return 0;}
    };
    private static EntityStates.Entity state(String raw,String... attrs){
        Map<String,String> attributes=new HashMap<>();for(int n=0;n<attrs.length;n+=2)attributes.put(attrs[n],attrs[n+1]);
        return new EntityStates.Entity(raw,attributes);
    }
    private static CardBodies.CardContent render(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live){return CardBodies.FOR.get("entities").render(item,states,live,ENV);}

    @Test public void subscribesToOrderedReadingsWithoutAServiceAction() throws Exception {
        DashboardSpec spec=DashboardSpec.parse(doc(card(entry("sensor.temperature"),entry("sensor.humidity"),entry("number.target"))));
        assertEquals(Arrays.asList("sensor.temperature","sensor.humidity","number.target"),spec.entities());
        assertEquals("mdi:format-list-bulleted",spec.items.get(0).icon);
        assertFalse(spec.items.get(0).interactive());
        for(String entity:spec.entities())assertEquals(new HashSet<>(Arrays.asList("friendly_name","icon","unit_of_measurement","device_class")),spec.attributes().get(entity));
    }
    @Test public void allowsOneReadingAndAHeaderOverride() throws Exception {
        DashboardSpec spec=DashboardSpec.parse(doc(card(entry("switch.pump")).put("title","Pomiary").put("icon","mdi:eye")));
        assertEquals(Collections.singletonList("switch.pump"),spec.entities());
        assertEquals("Pomiary",spec.items.get(0).title);assertEquals("mdi:eye",spec.items.get(0).icon);
    }
    @Test public void requiresOneToThreeStrictEntryObjects() throws Exception {
        JSONObject absent=card(entry("sensor.x"));absent.remove("entries");
        rejects(absent,"entities requires 1 to 3 entries");
        rejects(card(),"entities requires 1 to 3 entries");
        rejects(card(entry("sensor.a"),entry("sensor.b"),entry("sensor.c"),entry("sensor.d")),"entities requires 1 to 3 entries");
        rejects(card().put("entries",JSONObject.NULL),"entities requires 1 to 3 entries");
        rejects(card().put("entries",new JSONObject()),"entities requires 1 to 3 entries");
        rejects(card().put("entries",new JSONArray().put("sensor.x")),"entries: each entry must be an object");
        rejects(card(new JSONObject()),"Required field entity");
        rejects(card(entry("sensor.x").put("entity",JSONObject.NULL)),"Invalid field entity");
        rejects(card(entry("sensor.x").put("entity",5)),"Invalid field entity");
        for(String entity:Arrays.asList("sensor.Bad","sensor.","sensor.x.extra","sensor space"," sensor.x"))
            rejects(card(entry(entity)),"entries: invalid entity "+entity);
        rejects(card(entry("sensor."+String.join("",Collections.nCopies(122,"x")))),"Invalid field entity");
        for(Object title:Arrays.asList("", "  ",JSONObject.NULL,3,String.join("",Collections.nCopies(41,"x"))))
            rejects(card(entry("sensor.x").put("title",title)),"Invalid field title");
        for(String key:Arrays.asList("icon","attribute","unit","tap_action","confirmation"))
            rejects(card(entry("sensor.x").put(key,"ignored")),"Unknown field entries: "+key);
    }
    @Test public void staysReadOnlyAndRequiresVersionSix() throws Exception {
        rejects(card(entry("number.target")).put("tap_action",new JSONObject().put("action","details")),"Field not allowed for type entities: tap_action");
        rejects(card(entry("sensor.x")).put("confirmation",new JSONObject().put("enabled",true)),"Field not allowed for type entities: confirmation");
        rejects(card(entry("sensor.x")).put("entity","sensor.y"),"Field not allowed for type entities: entity");
        JSONObject legacy=DashboardSpecTest.example().put("version",5);legacy.getJSONArray("items").put(card(entry("sensor.x")));
        try{DashboardSpec.parse(legacy);fail("entities accepted at version 5");}
        catch(IllegalArgumentException expected){assertEquals("Type entities requires version: 6",expected.getMessage());}
        JSONObject alerts=DashboardSpecV6Test.item("a","alerts",1,1,1,1).put("sources",new JSONArray().put(new JSONObject().put("title","Alarm").put("entity","sensor.warning").put("when",new JSONObject().put("entity","binary_sensor.warning").put("state","on"))));
        JSONObject empty=card(entry("sensor.x"));for(String key:Arrays.asList("id","column","row","width","height"))empty.remove(key);
        try{DashboardSpec.parse(doc(alerts.put("empty",empty)));fail("entities accepted as alerts.empty");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().startsWith("empty: allowed types "));}
    }
    @Test public void showsOrderedNumericReadingsWithIndependentLabelsAndUnits() throws Exception {
        DashboardSpec.Item item=DashboardSpec.parse(doc(card(entry("sensor.temperature").put("title","Temperatura"),entry("sensor.humidity"),entry("sensor.co2")))).items.get(0);
        Map<String,EntityStates.Entity> states=new HashMap<>();
        states.put("sensor.temperature",state("22.44","unit_of_measurement","°C","friendly_name","Zmieniona nazwa"));
        states.put("sensor.humidity",state("48.0","unit_of_measurement","%","friendly_name","Wilgotność"));
        states.put("sensor.co2",state("812.45"));
        CardBodies.CardContent content=render(item,states,true);
        assertEquals(3,content.rows.size());
        assertEquals("Temperatura",content.rows.get(0).label);assertEquals("22.4 °C",content.rows.get(0).value);
        assertEquals("Wilgotność",content.rows.get(1).label);assertEquals("48 %",content.rows.get(1).value);
        assertEquals("Co2",content.rows.get(2).label);assertEquals("812.5",content.rows.get(2).value);
        assertEquals("Readings: Temperatura: 22.4 °C, Wilgotność: 48 %, Co2: 812.5",content.description);
        assertFalse(content.accent);
    }
    @Test public void aMissingReadingKeepsItsOwnRowAndOfflineDescription() throws Exception {
        DashboardSpec.Item item=DashboardSpec.parse(doc(card(entry("sensor.temperature"),entry("sensor.humidity"),entry("sensor.next_visit")).put("title","Pomiary"))).items.get(0);
        Map<String,EntityStates.Entity> states=new HashMap<>();
        states.put("sensor.temperature",state("21.5","unit_of_measurement","°C","friendly_name","Temperatura"));
        states.put("sensor.humidity",state("unavailable","friendly_name","Wilgotność"));
        CardBodies.CardContent content=render(item,states,false);
        assertEquals(3,content.rows.size());assertEquals("21.5 °C",content.rows.get(0).value);
        assertEquals("Wilgotność",content.rows.get(1).label);assertEquals("—",content.rows.get(1).value);
        assertEquals("Next visit",content.rows.get(2).label);assertEquals("—",content.rows.get(2).value);
        assertEquals("Pomiary: Temperatura: 21.5 °C, Wilgotność: no data, Next visit: no data, data is out of date",content.description);
        states.put("sensor.humidity",state("unknown","friendly_name",""));
        assertEquals("Humidity",render(item,states,true).rows.get(1).label);assertEquals("—",render(item,states,true).rows.get(1).value);
    }
    @Test public void formatsNumbersWithoutUnitsAndTranslatesOtherKnownStates() throws Exception {
        String[][] readings={{"sensor.x","1.25","","1.3"},{"number.x","-1.25","","-1.3"},{"sensor.x","0.0","","0"},{"sensor.x","ready","","ready"},
            {"sensor.x","NaN","W","NaN W"},{"sensor.x","Infinity","W","Infinity W"},{"switch.x","on","","On"},{"cover.x","open","","Open"},{"lock.x","locked","","Locked"}};
        for(String[] reading:readings){
            DashboardSpec.Item item=DashboardSpec.parse(doc(card(entry(reading[0])))).items.get(0);
            EntityStates.Entity entity=reading[2].isEmpty()?state(reading[1]):state(reading[1],"unit_of_measurement",reading[2]);
            assertEquals(Arrays.toString(reading),reading[3],render(item,Collections.singletonMap(reading[0],entity),true).rows.get(0).value);
        }
        DashboardSpec.Item item=DashboardSpec.parse(doc(card(entry("binary_sensor.door"),entry("binary_sensor.motion"),entry("binary_sensor.other")))).items.get(0);
        Map<String,EntityStates.Entity> states=new HashMap<>();states.put("binary_sensor.door",state("on","device_class","door"));states.put("binary_sensor.motion",state("off","device_class","motion"));states.put("binary_sensor.other",state("off"));
        CardBodies.CardContent content=render(item,states,true);
        assertEquals("Open",content.rows.get(0).value);assertEquals("None",content.rows.get(1).value);assertEquals("No",content.rows.get(2).value);
    }
    @Test public void consumesLiveSnapshotChangesAndKeepsDuplicateRowsWhileDeduplicatingSubscriptions() throws Exception {
        DashboardSpec spec=DashboardSpec.parse(doc(card(entry("sensor.x"),entry("sensor.x").put("title","Powtórzony"))));
        assertEquals(Collections.singletonList("sensor.x"),spec.entities());
        EntityStates states=new EntityStates(spec.attributes());
        states.apply(new JSONObject("{\"a\":{\"sensor.x\":{\"s\":\"22.2\",\"a\":{\"friendly_name\":\"Pokój\",\"unit_of_measurement\":\"°C\",\"unneeded\":\"discarded\"}}}}"));
        CardBodies.CardContent content=render(spec.items.get(0),states.snapshot(),true);
        assertEquals(2,content.rows.size());assertEquals("Pokój",content.rows.get(0).label);assertEquals("Powtórzony",content.rows.get(1).label);
        assertEquals("22.2 °C",content.rows.get(1).value);
        assertNull(states.snapshot().get("sensor.x").attribute("unneeded"));
        states.apply(new JSONObject("{\"c\":{\"sensor.x\":{\"+\":{\"s\":\"23\",\"a\":{\"friendly_name\":\"Salon\"}}}}}"));
        content=render(spec.items.get(0),states.snapshot(),true);
        assertEquals("Salon",content.rows.get(0).label);assertEquals("23 °C",content.rows.get(0).value);assertEquals("23 °C",content.rows.get(1).value);
        states.apply(new JSONObject("{\"r\":[\"sensor.x\"]}"));
        assertEquals("—",render(spec.items.get(0),states.snapshot(),true).rows.get(0).value);
        try{spec.items.get(0).entries.clear();fail("entries list changed");}catch(UnsupportedOperationException expected){}
        try{content.rows.clear();fail("rendered rows list changed");}catch(UnsupportedOperationException expected){}
    }
}
