package kr.chagok.ledger;
import android.content.*;
import java.time.*;

final class BackupStatus {
    static final long WEEK=7L*24*60*60*1000;
    private final SharedPreferences prefs;
    BackupStatus(Context context){prefs=context.getSharedPreferences("backup_status",Context.MODE_PRIVATE);if(!prefs.contains("first_seen"))prefs.edit().putLong("first_seen",System.currentTimeMillis()).commit();}
    long last(){return prefs.getLong("last_full",0);}
    String kind(){return prefs.getString("last_kind","");}
    int count(){return prefs.getInt("last_count",0);}
    void success(String kind,int count){prefs.edit().putLong("last_full",System.currentTimeMillis()).putString("last_kind",kind).putInt("last_count",count).commit();}
    boolean stale(long now){return now-Math.max(last(),prefs.getLong("first_seen",now))>=WEEK;}
    boolean enabled(){return prefs.getBoolean("notify",false);}
    void enabled(boolean on){prefs.edit().putBoolean("notify",on).commit();}
    boolean shouldNotify(long now){return enabled()&&stale(now)&&now-prefs.getLong("last_notified",0)>=24L*60*60*1000;}
    void notified(long now){prefs.edit().putLong("last_notified",now).commit();}
}
