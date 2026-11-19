package kr.chagok.ledger;

import android.content.*;
import android.graphics.Typeface;
import java.util.*;
import java.time.*;

final class AppPreferences {
    private final Context context;private final SharedPreferences prefs;
    AppPreferences(Context context){this.context=context;prefs=context.getSharedPreferences("app_preferences",Context.MODE_PRIVATE);}
    boolean dark(){return prefs.getBoolean("dark",false);}
    void dark(boolean enabled){prefs.edit().putBoolean("dark",enabled).commit();}
    int startTab(){int tab=prefs.getInt("start_tab",2);return Arrays.asList(4,2,5,0,1).contains(tab)?tab:2;}
    void startTab(int tab){prefs.edit().putInt("start_tab",tab).commit();}
    String fontId(){return prefs.getString("font","system");}
    void fontId(String id){prefs.edit().putString("font",id).commit();}
    Typeface font(){return typeface(fontId());}
    Typeface typeface(String id){try{if("gothic".equals(id))return Typeface.createFromAsset(context.getAssets(),"fonts/NanumGothic-Regular.ttf");if("serif".equals(id))return Typeface.createFromAsset(context.getAssets(),"fonts/NanumMyeongjo-Regular.ttf");}catch(Exception ignored){}return Typeface.DEFAULT;}
    Set<String> homeMetrics(){return new HashSet<>(prefs.getStringSet("home_metrics",OverviewData.DEFAULT_METRICS));}
    void homeMetrics(Set<String> values){prefs.edit().putStringSet("home_metrics",new HashSet<>(values)).commit();}
    int recordSort(){return prefs.getInt("record_sort",HistoryData.DATE)==HistoryData.CATEGORY?HistoryData.CATEGORY:HistoryData.DATE;}
    void recordSort(int value){prefs.edit().putInt("record_sort",value).commit();}
    int statisticsMode(){int mode=prefs.getInt("statistics_mode",0);return mode>=0&&mode<=3?mode:0;}
    int statisticsYear(){return Math.max(1900,Math.min(9999,prefs.getInt("statistics_year",LocalDate.now().getYear())));}
    YearMonth statisticsMonth(){try{return YearMonth.parse(prefs.getString("statistics_month",YearMonth.now().toString()));}catch(Exception e){return YearMonth.now();}}
    LocalDate statisticsDate(boolean start){try{return LocalDate.parse(prefs.getString(start?"statistics_from":"statistics_to",(start?LocalDate.now().withDayOfMonth(1):LocalDate.now()).toString()));}catch(Exception e){return start?LocalDate.now().withDayOfMonth(1):LocalDate.now();}}
    void statistics(int mode,YearMonth month,int year,LocalDate from,LocalDate to){prefs.edit().putInt("statistics_mode",mode).putString("statistics_month",month.toString()).putInt("statistics_year",year).putString("statistics_from",from.toString()).putString("statistics_to",to.toString()).commit();}
    boolean hasPin(){return !pinHash().isEmpty();}
    private String pinHash(){return prefs.getString("pin_hash","");}
    void setPinHash(String hash){prefs.edit().putString("pin_hash",hash).putInt("pin_failures",0).putLong("pin_until",0).commit();}
    long blockedSeconds(){return Math.max(0,(prefs.getLong("pin_until",0)-System.currentTimeMillis()+999)/1000);}
    synchronized boolean checkPin(String pin){
        if(blockedSeconds()>0)return false;
        if(PinCrypto.verify(pin,pinHash())){prefs.edit().putInt("pin_failures",0).putLong("pin_until",0).commit();return true;}
        int failed=prefs.getInt("pin_failures",0)+1;SharedPreferences.Editor edit=prefs.edit().putInt("pin_failures",failed);
        if(failed>=5)edit.putLong("pin_until",System.currentTimeMillis()+Math.min(300000L,30000L*(failed-4)));edit.commit();return false;
    }
}
