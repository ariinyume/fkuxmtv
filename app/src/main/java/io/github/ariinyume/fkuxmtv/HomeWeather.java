package io.github.ariinyume.fkuxmtv;

import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Optional HTTPS weather, city-level IP lookup, and explicit city search. No location permission. */
final class HomeWeather {
    static final class City {
        final String name,region;final double lat,lon;
        City(String name,String region,double lat,double lon){this.name=name;this.region=region;this.lat=lat;this.lon=lon;}
        String label(){return name+(region.isEmpty()?"":" · "+region);}
    }
    static final class Reading {
        final String city;final double temperature;final int code;final boolean day;final long observed;
        Reading(String city,double temperature,int code,boolean day,long observed){this.city=city;this.temperature=temperature;this.code=code;this.day=day;this.observed=observed;}
    }
    interface Cities {void done(List<City> cities,String error);}
    private final SharedPreferences prefs;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable changed;
    private boolean active,destroyed,busy;
    private int generation;
    private long attempted;
    private Reading reading;
    private String status="尚未获取天气";
    HomeWeather(Context context,Runnable changed){
        prefs=context.getSharedPreferences("home-weather",0);this.changed=changed;
        try{JSONObject o=new JSONObject(prefs.getString("cache","{}"));reading=parse(o);}catch(Exception ignored){}
    }
    boolean enabled(){return prefs.getBoolean("enabled",false);}
    boolean automatic(){return prefs.getBoolean("automatic",true);}
    String city(){return automatic()?prefs.getString("auto_name","尚未识别"):prefs.getString("manual_name","尚未选择");}
    String status(){if(!enabled())return "天气已关闭";if(busy)return "正在获取天气…";if(current()!=null&&status.equals("尚未获取天气"))return "已缓存 · "+WeatherPolicy.description(reading.code);return status;}
    Reading current(){return enabled()&&reading!=null&&WeatherPolicy.fresh(reading.observed,System.currentTimeMillis(),WeatherPolicy.MAX_AGE)?reading:null;}
    private boolean commit(SharedPreferences.Editor edit){if(!edit.commit())return false;generation++;busy=false;attempted=0;reading=null;prefs.edit().remove("cache").apply();changed.run();refresh(true);return true;}
    boolean enabled(boolean value){return commit(prefs.edit().putBoolean("enabled",value));}
    boolean autoCity(){return commit(prefs.edit().putBoolean("automatic",true).remove("auto_at"));}
    boolean select(City city){return commit(prefs.edit().putBoolean("automatic",false).putString("manual_name",city.name).putString("manual_lat",String.valueOf(city.lat)).putString("manual_lon",String.valueOf(city.lon)));}
    void active(boolean value){active=value;ui.removeCallbacks(poll);if(active){refresh();ui.postDelayed(poll,60_000);}}
    private final Runnable poll=new Runnable(){public void run(){if(!active||destroyed)return;changed.run();refresh();ui.postDelayed(this,60_000);}};
    void refresh(){refresh(false);}
    private void refresh(boolean explicit){
        long now=System.currentTimeMillis();
        if(destroyed||(!active&&!explicit)||!enabled()||busy||WeatherPolicy.fresh(attempted,now,WeatherPolicy.REFRESH))return;
        long fetched=prefs.getLong("fetched",0);
        if(current()!=null&&WeatherPolicy.fresh(fetched,now,WeatherPolicy.REFRESH))return;
        attempted=now;busy=true;final int request=generation;final boolean auto=automatic();
        final String name=city(), lat=prefs.getString(auto?"auto_lat":"manual_lat",""),lon=prefs.getString(auto?"auto_lon":"manual_lon","");
        final boolean locate=auto&&!WeatherPolicy.fresh(prefs.getLong("auto_at",0),now,WeatherPolicy.LOCATION_AGE);
        worker.execute(()->{
            try{
                City resolved;
                if(locate||lat.isEmpty()||lon.isEmpty()){
                    if(!auto)throw new IOException("请先选择城市");
                    JSONObject ip=get("https://ipwho.is/");
                    if(!ip.optBoolean("success"))throw new IOException("城市识别暂不可用");
                    resolved=localized(new City(ip.getString("city"),"",ip.getDouble("latitude"),ip.getDouble("longitude")),ip.optString("country_code"));
                }else resolved=new City(name,"",Double.parseDouble(lat),Double.parseDouble(lon));
                final City city=resolved;
                if(!WeatherPolicy.coordinates(city.lat,city.lon)||city.name.trim().isEmpty())throw new IOException("城市信息不可用");
                JSONObject data=get("https://api.open-meteo.com/v1/forecast?latitude="+city.lat+"&longitude="+city.lon+"&current=temperature_2m,weather_code,is_day&timeformat=unixtime");
                JSONObject weather=data.getJSONObject("current");
                if(!"°C".equals(data.getJSONObject("current_units").getString("temperature_2m")))throw new IOException("温度单位不可用");
                JSONObject cache=new JSONObject().put("city",city.name).put("temperature",weather.getDouble("temperature_2m")).put("code",weather.getInt("weather_code")).put("day",weather.getInt("is_day")==1).put("observed",weather.getLong("time")*1000L);
                Reading result=parse(cache);
                if(!WeatherPolicy.fresh(result.observed,System.currentTimeMillis(),WeatherPolicy.MAX_AGE))throw new IOException("天气数据已过期");
                ui.post(()->{if(destroyed||request!=generation)return;busy=false;reading=result;status="已更新 · "+WeatherPolicy.description(result.code);
                    SharedPreferences.Editor e=prefs.edit().putString("cache",cache.toString()).putLong("fetched",System.currentTimeMillis());
                    if(auto)e.putString("auto_name",city.name).putString("auto_lat",String.valueOf(city.lat)).putString("auto_lon",String.valueOf(city.lon));
                    if(auto&&(locate||lat.isEmpty()||lon.isEmpty()))e.putLong("auto_at",System.currentTimeMillis());
                    e.apply();changed.run();});
            }catch(Exception failure){ui.post(()->{if(destroyed||request!=generation)return;busy=false;status="暂时无法更新，可检查网络或手动选择城市";changed.run();});}
        });
    }
    void search(String query,Cities callback){
        final String value=query.trim();if(value.length()<2||value.length()>60){callback.done(Collections.emptyList(),"请输入至少两个字母或汉字");return;}
        worker.execute(()->{
            List<City> results=new ArrayList<>();String error="";
            try{
                JSONArray data=get("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(value,"UTF-8")+"&count=8&language=zh&format=json").optJSONArray("results");
                if(data!=null)for(int i=0;i<data.length();i++){JSONObject c=data.getJSONObject(i);double lat=c.getDouble("latitude"),lon=c.getDouble("longitude");if(WeatherPolicy.coordinates(lat,lon))results.add(new City(c.getString("name"),c.optString("admin1")+" · "+c.optString("country"),lat,lon));}
                if(results.isEmpty())error="没有找到城市，试试完整名称或拼音";
            }catch(Exception failure){error="搜索暂不可用，请稍后重试";}
            final String message=error;ui.post(()->{if(!destroyed)callback.done(results,message);});
        });
    }
    private static City localized(City city,String country){
        // A translated name is optional. Never replace the IP coordinates with a namesake city.
        try{
            JSONArray rows=get("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(city.name,"UTF-8")+"&count=8&language=zh&countryCode="+URLEncoder.encode(country,"UTF-8")).optJSONArray("results");
            double nearest=35;String label=city.name;
            if(rows!=null)for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);double lat=row.getDouble("latitude"),lon=row.getDouble("longitude");
                double dy=(lat-city.lat)*111,dx=(lon-city.lon)*111*Math.cos(Math.toRadians(city.lat));double distance=Math.hypot(dx,dy);
                if(distance<nearest){nearest=distance;label=row.getString("name");}
            }
            return new City(label,city.region,city.lat,city.lon);
        }catch(Exception ignored){return city;}
    }
    private static Reading parse(JSONObject o)throws JSONException{
        double t=o.getDouble("temperature");int code=o.getInt("code");String city=o.getString("city");
        if(!WeatherPolicy.valid(t,code)||city.trim().isEmpty())throw new JSONException("Invalid weather");
        return new Reading(city,t,code,o.getBoolean("day"),o.getLong("observed"));
    }
    private static JSONObject get(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","GuiZangTV/WeatherTrial");
        try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>262144)throw new IOException("Response too large");out.write(buffer,0,n);}
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        }finally{c.disconnect();}
    }
    void destroy(){destroyed=true;generation++;ui.removeCallbacksAndMessages(null);worker.shutdownNow();}
    void dump(java.io.PrintWriter out){out.println("HomeWeather: enabled="+enabled()+" automatic="+automatic()+" active="+active+" busy="+busy+" visible="+(current()!=null)+" status="+status);}
}
