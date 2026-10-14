package pl.mateusz.helios;

import java.util.*;

/** What each card type shows, as pure text and flags; DashboardView owns the Views and only applies the result. Testable on the JVM. */
final class CardBodies {
    /** Local facts the entity snapshot does not carry. */
    interface Env {String time();String weekday();String date();String musicInfo();long now();
        /** Tomorrow from HA's daily forecast feed, or null while the clock has none. */
        default Tomorrow tomorrow(){return null;}}
    /** The right half of a wide tile: the same shape as the left one - an icon, a big value and a small line under it. */
    static final class Side {
        final String icon,value,detail;
        Side(String icon,String value,String detail){this.icon=icon;this.value=value;this.detail=detail;}
    }
    /** One compact read-only label/value row, in the configured order. */
    static final class Row {
        final String label,value;
        Row(String label,String value){this.label=label;this.value=value;}
    }
    /** One rendering: value line, up to two detail lines, optional label and icon overrides, the full content description, accent tint and an expiry for self-refreshing content. */
    static final class CardContent {
        final String value,detail,detail2,label,icon,description;final boolean accent;final long expiresAt;
        /** An icon drawn in front of the big value (energy: the battery level); null everywhere else. */
        String valueIcon;
        /** The second half, or null for every tile that has only one. */
        final Side side;
        final List<Row> rows;
        CardContent(String value,String detail,String detail2,String label,String icon,String description,boolean accent,long expiresAt){this(value,detail,detail2,label,icon,description,accent,expiresAt,null);}
        CardContent(String value,String detail,String detail2,String label,String icon,String description,boolean accent,long expiresAt,Side side){this(value,detail,detail2,label,icon,description,accent,expiresAt,side,Collections.emptyList());}
        CardContent(String value,String detail,String detail2,String label,String icon,String description,boolean accent,long expiresAt,Side side,List<Row> rows){this.value=value;this.detail=detail;this.detail2=detail2;this.label=label;this.icon=icon;this.description=description;this.accent=accent;this.expiresAt=expiresAt;this.side=side;this.rows=Collections.unmodifiableList(new ArrayList<>(rows));}
        CardContent(String value,String detail,String label,boolean accent,long expiresAt,DashboardSpec.Item item,boolean live){this(value,detail,label,null,accent,expiresAt,item,live);}
        CardContent(String value,String detail,String label,String icon,boolean accent,long expiresAt,DashboardSpec.Item item,boolean live){
            this(value,detail,"",label,icon,(label!=null?label:defaultLabel(item))+": "+value+(detail.isEmpty()?"":", "+detail)+(live?"":", data is out of date"),accent,expiresAt);
        }
        CardContent(String value,String detail,String label,String icon,boolean accent,long expiresAt,DashboardSpec.Item item,boolean live,Side side){
            this(value,detail,"",label,icon,(label!=null?label:defaultLabel(item))+": "+value+(detail.isEmpty()?"":", "+detail)
                +(side==null?"":", tomorrow "+side.value+(side.detail.isEmpty()?"":" "+side.detail))+(live?"":", data is out of date"),accent,expiresAt,side);
        }
    }
    interface Body {CardContent render(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live,Env env);}
    private CardBodies(){}

    static final Map<String,Body> FOR;
    static {
        LinkedHashMap<String,Body> m=new LinkedHashMap<>();
        m.put("clock",(item,states,live,env)->new CardContent(env.time(),env.date(),env.weekday(),null,null,env.time()+", "+env.weekday()+", "+env.date(),false,0));
        m.put("weather",CardBodies::weather);
        m.put("entity",(item,states,live,env)->{
            EntityStates.Entity e=states.get(item.entity);
            String v=e==null||!e.known()?null:item.attribute==null?e.state:e.attribute(item.attribute);
            return new CardContent(v==null?"No data":v,"",null,false,0,item,live);
        });
        m.put("light",(item,states,live,env)->{
            EntityStates.Entity e=states.get(item.entity);boolean known=e!=null&&e.known();
            String text=!known?"No data":e.state.equals("on")?"On":e.state.equals("off")?"Off":e.state;
            return new CardContent(text,"",null,known&&e.state.equals("on"),0,item,live);
        });
        m.put("energy",CardBodies::energy);
        m.put("predbat",CardBodies::predbat);
        m.put("spotify",(item,states,live,env)->new CardContent("Open","",null,true,0,item,true));
        m.put("assistant",(item,states,live,env)->{
            EntityStates.Entity e=states.get(item.entity);
            String text=e==null||!e.known()||e.state.equals("idle")?"Talk":e.state;
            return new CardContent(text,"",null,false,0,item,live);
        });
        m.put("climate",CardBodies::climate);
        m.put("cover",CardBodies::cover);
        m.put("garage",CardBodies::cover);
        m.put("music",(item,states,live,env)->new CardContent(env.musicInfo(),"","",null,null,"Music: "+env.musicInfo(),false,0)); // the player, not HA, says whether this is live
        m.put("cover_group",(item,states,live,env)->{
            DashboardSpec.Cover ca=item.covers.get(0),cb=item.covers.get(1);
            EntityStates.Entity a=states.get(ca.entity),b=states.get(cb.entity);
            String label=defaultLabel(item);
            return new CardContent(CoverText.line("A",a),CoverText.line("B",b),"",null,null,label+": "+ca.title+": "+CoverText.state(a)+", "+cb.title+": "+CoverText.state(b)+(live?"":", data is out of date"),CoverText.attention(a)||CoverText.attention(b),0);
        });
        m.put("tile",CardBodies::tile);
        m.put("entities",CardBodies::entities);
        m.put("alerts",(item,states,live,env)->{ // the tile draws itself from AlertsModel; this keeps value and description for the shared paths
            AlertsModel a=new AlertsModel(item.sources,states,live);
            String idle=a.idleWord();
            return new CardContent(a.count(),idle!=null?idle:a.summaryTitle()+a.summarySuffix(),"",defaultLabel(item),null,a.description(defaultLabel(item)),live&&a.a()>0,0);
        });
        FOR=Collections.unmodifiableMap(m);
    }
    private static CardContent cover(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live,Env env){
        EntityStates.Entity e=states.get(item.entity);boolean known=e!=null&&e.known();
        String text=!known?"No data":coverState(e.state),extra="";
        if(known&&e.attribute("current_position")!=null)extra="Opening "+number(e.attribute("current_position"),"%");
        return new CardContent(text,extra,null,known&&!e.state.equals("closed"),0,item,live);
    }
    private static CardContent weather(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live,Env env){
        if(item.forecast()){
            Forecast.Mode mode=Forecast.mode(states.get(item.forecastWhenEntity),item.forecastWhenState);
            if(mode==Forecast.Mode.UNKNOWN)return new CardContent("—","no mode data",null,false,0,item,live);
            if(mode==Forecast.Mode.TOMORROW){
                Forecast f=Forecast.parse(states.get(item.forecastEntity),env.now());
                if(f==null)return new CardContent("—","no forecast","Tomorrow",false,0,item,live);
                return new CardContent(f.max(),WeatherLabels.english(f.condition)+(f.min()==null?"":" · "+f.min()),"Tomorrow",false,f.validUntilMs,item,live);
            }
        }
        EntityStates.Entity e=states.get(item.entity);boolean known=e!=null&&e.known();
        String temperature=null,unit="";
        if(item.temperatureEntity!=null){EntityStates.Entity t=states.get(item.temperatureEntity);if(t!=null&&t.known()){temperature=t.state;if(t.attribute("unit_of_measurement")!=null)unit=t.attribute("unit_of_measurement");}}
        else if(known){temperature=e.attribute("temperature");if(e.attribute("temperature_unit")!=null)unit=e.attribute("temperature_unit");}
        String text=number(temperature,unit); // no unit in HA means no unit on screen (SPEC 0.8a pkt 3.3)
        String wind=known?e.attribute("wind_speed"):null;
        String windText=wind==null?"":number(wind,e.attribute("wind_speed_unit")==null?"":" "+e.attribute("wind_speed_unit"));
        String condition=known?WeatherLabels.english(e.state):"No data";
        // A wide tile puts tomorrow beside today; the forecast comes from HA's own daily feed, so nothing has to be configured.
        Tomorrow t=item.width>1?env.tomorrow():null;
        if(t!=null&&t.entity!=null&&!t.entity.equals(item.entity))t=null; // one forecast feed, for one weather entity: another page's tile shows today only
        Side side=t==null?null:new Side(t.icon(),t.value(),t.detail());
        // Half a tile is no room for the condition in words - the icon already says it, so only the wind stays
        // under the temperature. The description keeps the word, because a screen reader has no icon to read.
        String night=t==null?"":t.night(); // tonight's low before the wind, where the second half makes room for it
        String extra=side!=null?night+(night.isEmpty()||windText.isEmpty()?"":" · ")+windText:condition+(windText.isEmpty()?"":" · "+windText);
        String spoken=defaultLabel(item)+": "+text+", "+condition+(night.isEmpty()?"":", at night "+night.substring(2))+(windText.isEmpty()?"":", "+windText)
            +(side==null?"":", tomorrow "+side.value+(side.detail.isEmpty()?"":" "+side.detail))+(live?"":", data is out of date");
        return new CardContent(text,extra,"",item.title,known?WeatherLabels.icon(e.state):null,spoken,false,0,side);
    }
    private static final List<String> ACTIVE=Arrays.asList("on","open","opening","closing","unlocked","unlocking","playing","heating","cooling","cleaning","running");
    /** One entity of any domain: the state in Polish where the word is fixed, a number with its unit, or the configured attribute; the entity's own name and icon unless the config says otherwise. */
    private static CardContent tile(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live,Env env){
        EntityStates.Entity e=states.get(item.entity);boolean known=e!=null&&e.known();
        String domain=item.entity.substring(0,item.entity.indexOf('.'));
        String text;
        if(!known)text=ActionPolicy.usable("activate",e)&&ActionPolicy.defaultIntent(domain)==ActionPolicy.Intent.ACTIVATE?"—":"No data"; // a scene or button reads unknown until first used, and still works
        else if(item.attribute!=null){String a=e.attribute(item.attribute);text=a==null?"No data":a;}
        else text=stateText(domain,e);
        String label=item.title!=null?item.title:known&&e.attribute("friendly_name")!=null?e.attribute("friendly_name"):null;
        String icon=known&&!item.ownIcon?e.attribute("icon"):null; // the entity's icon only fills in for a config that named none
        if(icon!=null&&(MdiIcons.installed()==null||MdiIcons.name(icon)==null||!MdiIcons.installed().has(MdiIcons.name(icon))))icon=null; // an icon the font lacks falls back to the configured one
        return new CardContent(text,"",label,icon,known&&ACTIVE.contains(e.state),0,item,live);
    }
    /** One to three independent readings, with the card header and complete spoken labels even when some values are missing. */
    /** Active execution detail belongs to this status, unlike the next planned charge/export limit. */
    private static CardContent predbat(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live,Env env){
        EntityStates.Entity e=states.get(item.entity);
        if(e==null||!e.known())return new CardContent("No data","",null,false,0,item,live);
        String status=e.state,detail=e.attribute("detail");
        if(detail==null)detail="";
        detail=detail.trim();
        // A single-inverter execution publishes e.g. "target 23%-100%". Keep richer fleet/warning text intact.
        if(detail.matches("target \\d+(\\.\\d+)?%-\\d+(\\.\\d+)?%"))
            detail=detail.substring(7).replace("%-","% → ");
        String value=status;
        switch(status){
            case "Demand":if(detail.isEmpty())detail="Normal self-use";break;
            case "Hold charging":value="Charge hold";break;
            case "Hold exporting":value="Export hold";break;
            case "Freeze charging":value="Charge freeze";break;
            case "Freeze exporting":value="Export freeze";break;
        }
        return new CardContent(value,detail,null,status.equals("Charging")||status.equals("Exporting"),0,item,live);
    }

    private static CardContent entities(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live,Env env){
        List<Row> rows=new ArrayList<>();StringBuilder description=new StringBuilder(defaultLabel(item)).append(": ");
        for(DashboardSpec.Entry entry:item.entries){
            EntityStates.Entity entity=states.get(entry.entity);
            String friendly=entity==null?null:entity.attribute("friendly_name");
            String label=entry.title!=null?entry.title:friendly!=null&&!friendly.trim().isEmpty()?friendly:entityLabel(entry.entity);
            String value=reading(entry.entity,entity);
            if(!rows.isEmpty())description.append(", ");
            description.append(label).append(": ").append(value.equals("—")?"no data":value);
            rows.add(new Row(label,value));
        }
        if(!live)description.append(", data is out of date");
        return new CardContent("","","",null,null,description.toString(),false,0,null,rows);
    }
    /** Numeric readings use the Polish decimal even without units; other states keep the tile's domain translations. */
    private static String reading(String id,EntityStates.Entity entity){
        if(entity==null||!entity.known())return "—";
        String unit=unit(entity);
        try{
            double value=Double.parseDouble(entity.state);
            return Double.isFinite(value)?decimal(entity.state,unit).trim():entity.state+(unit.isEmpty()?"":" "+unit);
        }catch(NumberFormatException ignored){return stateText(id.substring(0,id.indexOf('.')),entity);}
    }
    private static final List<String> OPENINGS=Arrays.asList("door","window","garage_door","opening","gate"),PRESENCE=Arrays.asList("motion","occupancy","presence","moving");
    static String stateText(String domain,EntityStates.Entity e){
        String s=e.state,cls=e.attribute("device_class");
        switch(domain){
            case "cover":return coverState(s);
            case "lock":switch(s){case "locked":return "Locked";case "unlocked":return "Unlocked";case "locking":return "Closing…";case "unlocking":return "Opening…";case "jammed":return "Jammed";default:return s;}
            case "binary_sensor":
                if(OPENINGS.contains(cls))return s.equals("on")?"Open":s.equals("off")?"Closed":s;
                if(PRESENCE.contains(cls))return s.equals("on")?"Detected":s.equals("off")?"None":s;
                return s.equals("on")?"Yes":s.equals("off")?"No":s;
            default:
                if(s.equals("on"))return "On";if(s.equals("off"))return "Off";
                String unit=e.attribute("unit_of_measurement");
                return unit!=null?decimal(s,unit):s;
        }
    }
    /** Title from the config, else the type's fixed word, else the entity id humanised. */
    static String defaultLabel(DashboardSpec.Item item){
        if(item.title!=null)return item.title;
        CardDefinition def=CardDefinition.of(item.type);
        if(def!=null&&def.defaultTitle!=null)return def.defaultTitle;
        return entityLabel(item.entity);
    }
    private static String entityLabel(String entity){
        String name=entity.substring(entity.indexOf('.')+1).replace('_',' ');
        return name.substring(0,1).toUpperCase(Locale.UK)+name.substring(1);
    }
    static String number(String raw,String unit){
        if(raw==null)return "—";
        try{return String.format(Locale.UK,"%.0f%s",Double.parseDouble(raw),unit);}catch(NumberFormatException e){return raw+unit;}
    }
    /** Up to one decimal, none when whole: "21,4 °C", "21 °C"; a non-number keeps its text and unit. */
    static String decimal(String raw,String unit){
        try{double v=Double.parseDouble(raw);String s=String.format(Locale.UK,"%.1f",v);if(s.endsWith(".0"))s=s.substring(0,s.length()-2);return s+" "+unit;}
        catch(NumberFormatException e){return raw+" "+unit;}
    }
    /** Measured temperature big, "Zadana 20,5°" under it; the icon says what the device is doing, amber only while it works (SPEC 0.19 pkt 4). */
    private static CardContent climate(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live,Env env){
        EntityStates.Entity e=states.get(item.entity);ClimateModel m=new ClimateModel(e,false);
        String name=item.title!=null?item.title:e!=null&&e.attribute("friendly_name")!=null?e.attribute("friendly_name"):defaultLabel(item);
        String word=m.known?m.actionWord():null;
        String spoken=name+": room temperature "+(m.current==null||!m.known?"no reading":m.currentText().replace("°"," degrees"))+", "+m.tileDetail().toLowerCase(Locale.UK)
            +(word==null?"":", "+word.toLowerCase(Locale.UK))+(live?"":", data is out of date");
        return new CardContent(m.currentText(),m.tileDetail(),"",name,item.ownIcon?null:m.icon(),spoken,m.known&&m.working(),0);
    }
    /** "1302 / 742 W" (production / house) where the title goes, the battery charge big under it; without a battery the pair is the value. */
    private static CardContent energy(DashboardSpec.Item item,Map<String,EntityStates.Entity> states,boolean live,Env env){
        EntityStates.Entity pv=states.get(item.entity),house=states.get(item.loadEntity);
        String pvUnit=unit(pv),houseUnit=unit(house);
        // one unit at the end when both sensors share it, each its own otherwise
        String pair=pvUnit.equals(houseUnit)?number(pv,"")+" / "+number(house,"")+(pvUnit.isEmpty()?"":" "+pvUnit):number(pv,pvUnit)+" / "+number(house,houseUnit);
        String battery=item.batteryEntity==null?null:number(states.get(item.batteryEntity),unit(states.get(item.batteryEntity)));
        String spoken=defaultLabel(item)+": generation "+number(pv,pvUnit)+", home "+number(house,houseUnit)+(battery==null?"":", battery "+battery)+(live?"":", data is out of date");
        if(item.batteryEntity==null)return new CardContent(pair,"","",null,null,spoken,false,0);
        CardContent c=new CardContent(battery,"","",pair,null,spoken,false,0);
        c.valueIcon=batteryIcon(states.get(item.batteryEntity));
        return c;
    }
    /** HA's own battery glyphs in steps of ten: battery-0 .. battery-90, battery (full), battery-unknown without a number. */
    static String batteryIcon(EntityStates.Entity e){
        double v;
        try{if(e==null||!e.known())return "mdi:battery-unknown";v=Double.parseDouble(e.state);}catch(NumberFormatException x){return "mdi:battery-unknown";}
        int step=(int)Math.round(Math.max(0,Math.min(100,v))/10.0)*10;
        return step>=100?"mdi:battery":"mdi:battery-"+step;
    }
    private static String unit(EntityStates.Entity e){String u=e==null?null:e.attribute("unit_of_measurement");return u==null?"":u;}
    /** A known reading with up to one decimal and its unit after a space, a dash otherwise. */
    private static String number(EntityStates.Entity e,String unit){
        if(e==null||!e.known())return "—";
        return unit.isEmpty()?decimal(e.state,"").trim():decimal(e.state,unit);
    }
    static String coverState(String state){
        switch(state){case "open":return "Open";case "closed":return "Closed";case "opening":return "Opening…";case "closing":return "Closing…";default:return state;}
    }
}
