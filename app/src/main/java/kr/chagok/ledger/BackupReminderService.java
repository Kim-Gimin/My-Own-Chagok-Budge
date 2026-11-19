package kr.chagok.ledger;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.Build;

public final class BackupReminderService extends JobService {
    static final int JOB=260613,NOTICE=260614;
    static boolean allowed(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==android.content.pm.PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    static void schedule(Context c){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(!new BackupStatus(c).enabled()||!allowed(c)){scheduler.cancel(JOB);return;}
        if(scheduler.getPendingJob(JOB)==null)scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(c,BackupReminderService.class)).setPeriodic(24L*60*60*1000).build());
    }
    @Override public boolean onStartJob(JobParameters params){check(this,System.currentTimeMillis());return false;}
    @Override public boolean onStopJob(JobParameters params){return true;}
    static boolean check(Context c,long now){
        BackupStatus status=new BackupStatus(c);if(!status.shouldNotify(now)||!allowed(c))return false;
        NotificationManager manager=c.getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("backup_reminder","백업 알림",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent intent=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class).putExtra("open_backup",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,"backup_reminder").setSmallIcon(android.R.drawable.ic_menu_save).setContentTitle("차곡가계부 백업 알림").setContentText("최근 전체 백업을 확인하고 소중한 기록을 보관하세요.").setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(intent).setAutoCancel(true).build();
        try{manager.notify(NOTICE,n);status.notified(now);return true;}catch(SecurityException ex){return false;}
    }
}
