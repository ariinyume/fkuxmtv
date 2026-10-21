package io.github.ariinyume.fkuxmtv;

/** Pure freshness/validation rules shared by network and cached weather. */
final class WeatherPolicy {
    static final long REFRESH=30*60_000L, MAX_AGE=2*60*60_000L, LOCATION_AGE=24*60*60_000L;
    static boolean fresh(long at,long now,long age){return at>0&&now>=at&&now-at<age;}
    static boolean coordinates(double lat,double lon){return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
    static boolean valid(double temperature,int code){return Double.isFinite(temperature)&&temperature>=-100&&temperature<=70&&!description(code).isEmpty();}
    static String description(int code){
        switch(code){
            case 0:return "晴";case 1:return "晴间多云";case 2:return "多云";case 3:return "阴";
            case 45:case 48:return "雾";
            case 51:case 53:case 55:return "毛毛雨";case 56:case 57:case 66:case 67:return "冻雨";
            case 61:return "小雨";case 63:return "中雨";case 65:return "大雨";
            case 71:return "小雪";case 73:return "中雪";case 75:return "大雪";case 77:return "雪粒";
            case 80:case 81:case 82:return "阵雨";case 85:case 86:return "阵雪";
            case 95:return "雷雨";case 96:case 99:return "雷雨冰雹";default:return "";
        }
    }
    static String icon(int code,boolean day){
        if(code==0)return day?"sun":"moon";
        if(code==1||code==2)return "partly";
        if(code==3)return "cloud";
        if(code==45||code==48)return "fog";
        if(code>=95)return "storm";
        if((code>=71&&code<=77)||code==85||code==86)return "snow";
        return "rain";
    }
}
