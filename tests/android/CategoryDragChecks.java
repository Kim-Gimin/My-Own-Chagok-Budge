package kr.chagok.ledger;
import android.content.*;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;

/** Screen-coordinate input through Android's input dispatcher, not direct row dispatch. */
final class CategoryDragChecks {
 static final String LONG="매우긴상세분류이름가나다라마바사아자차카타파하가나다라마바사";
 static void pointer(DeviceChecks t,long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);t.sendPointerSync(e);e.recycle();}
 static ScrollView scroll(DeviceChecks t){return (ScrollView)t.windowDesc("상세 분류 목록");}
 static View row(DeviceChecks t,String name){return CategoryOrderChecks.rowOf(t,name);}
 static List<String> names(DeviceChecks t){return CategoryOrderChecks.visible(t);}
 static void settled(DeviceChecks t)throws Exception{Thread.sleep(420);t.waitForIdleSync();}
 static float[] point(DeviceChecks t,String name,boolean grip)throws Exception{
  final View[] item={null};t.runOnMainSync(()->{ScrollView s=scroll(t);item[0]=row(t,name);s.scrollTo(0,item[0].getTop()+item[0].getHeight()/2-s.getHeight()/2);});t.waitForIdleSync();Thread.sleep(80);int[] at=new int[2];t.runOnMainSync(()->item[0].getLocationOnScreen(at));float d=item[0].getResources().getDisplayMetrics().density;return new float[]{at[0]+(grip?22:100)*d,at[1]+item[0].getHeight()/2f};
 }
 static void move(DeviceChecks t,long down,float x,float start,float end)throws Exception{for(int i=1;i<=12;i++){Thread.sleep(16);pointer(t,down,MotionEvent.ACTION_MOVE,x,start+(end-start)*i/12f);}t.waitForIdleSync();}
 static String orderPrefs(Context c){return c.getSharedPreferences("category_choices",0).getAll().toString();}
 static void resetVisuals(DeviceChecks t){ViewGroup rows=(ViewGroup)scroll(t).getChildAt(0);for(int i=0;i<rows.getChildCount();i++){t.eq(0f,rows.getChildAt(i).getTranslationY());t.eq(0f,rows.getChildAt(i).getTranslationZ());}}
 static AccessibilityNodeInfo node(DeviceChecks t,String text)throws Exception{
  AccessibilityNodeInfo root=null;for(int i=0;i<20;i++){root=t.getUiAutomation().getRootInActiveWindow();AccessibilityNodeInfo found=find(root,text);if(found!=null)return found;Thread.sleep(100);}
  StringBuilder trace=new StringBuilder("wanted="+text+"\n");dump(root,trace,0);try(java.io.Writer out=new java.io.OutputStreamWriter(new java.io.FileOutputStream(new java.io.File(t.getTargetContext().getExternalFilesDir(null),"drag-accessibility.txt")),java.nio.charset.StandardCharsets.UTF_8)){out.write(trace.toString());}return null;
 }
 static void dump(AccessibilityNodeInfo n,StringBuilder text,int depth){if(n==null)return;text.append(depth).append(' ').append(n.getClassName()).append(' ').append(n.getContentDescription()).append(' ').append(n.getText()).append(" children=").append(n.getChildCount()).append('\n');for(int i=0;i<n.getChildCount();i++)dump(n.getChild(i),text,depth+1);}
 static AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text){if(n==null)return null;CharSequence label=n.getContentDescription();if(label!=null&&label.toString().startsWith(text))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo got=find(n.getChild(i),text);if(got!=null)return got;}return null;}
 static void longMove(DeviceChecks t,Context c)throws Exception{
  List<String> before=names(t);String first=before.get(0),prefs=orderPrefs(c);float[] p=point(t,first,false);long down=SystemClock.uptimeMillis();pointer(t,down,MotionEvent.ACTION_DOWN,p[0],p[1]);Thread.sleep(ViewConfiguration.getLongPressTimeout()+100);t.waitForIdleSync();t.eq(true,row(t,first).getTranslationZ()>0);
  View second=row(t,before.get(1));int[] at=new int[2];t.runOnMainSync(()->second.getLocationOnScreen(at));float target=at[1]+second.getHeight()/2f+5;move(t,down,p[0],p[1],target);t.eq(before,names(t));t.eq(prefs,orderPrefs(c));pointer(t,down,MotionEvent.ACTION_UP,p[0],target);settled(t);t.eq(CategoryOrder.moveTo(before,0,1),names(t));t.eq(names(t),CategoryOrderChecks.choices(c,"지출"));
 }
 static void cancelMove(DeviceChecks t,Context c)throws Exception{
  List<String> before=names(t);String first=before.get(0),prefs=orderPrefs(c);float[] p=point(t,first,true);long down=SystemClock.uptimeMillis();pointer(t,down,MotionEvent.ACTION_DOWN,p[0],p[1]);float target=p[1]+row(t,first).getHeight()*2.2f;move(t,down,p[0],p[1],target);t.eq(true,row(t,first).getTranslationY()>0);pointer(t,down,MotionEvent.ACTION_CANCEL,p[0],target);settled(t);t.eq(before,names(t));t.eq(prefs,orderPrefs(c));resetVisuals(t);
 }
 static void auto(DeviceChecks t,Context c,boolean downwards)throws Exception{
  List<String> before=names(t);autoName(t,c,before.get(downwards?0:before.size()-1),downwards);
 }
 static void autoName(DeviceChecks t,Context c,String name,boolean downwards)throws Exception{
  List<String> before=names(t);int from=before.indexOf(name),to=downwards?before.size()-1:0;float[] p=point(t,name,true);ScrollView s=scroll(t);int[] at=new int[2];t.runOnMainSync(()->s.getLocationOnScreen(at));float d=s.getResources().getDisplayMetrics().density,target=downwards?at[1]+s.getHeight()-4*d:at[1]+4*d;long down=SystemClock.uptimeMillis();pointer(t,down,MotionEvent.ACTION_DOWN,p[0],p[1]);move(t,down,p[0],p[1],target);
  long deadline=SystemClock.uptimeMillis()+5500;final boolean[] more={true};while(SystemClock.uptimeMillis()<deadline){t.runOnMainSync(()->more[0]=s.canScrollVertically(downwards?1:-1));if(!more[0])break;Thread.sleep(40);}
  t.eq(false,more[0]);t.eq(true,downwards?s.getScrollY()>0:s.getScrollY()==0);t.eq(before,names(t));t.eq(before,CategoryOrderChecks.choices(c,"지출"));t.screenshot(c,"drag-autoscroll-"+downwards+"-"+t.mode);pointer(t,down,MotionEvent.ACTION_UP,p[0],target);settled(t);t.eq(CategoryOrder.moveTo(before,from,to),names(t));t.eq(names(t),CategoryOrderChecks.choices(c,"지출"));
 }
 static void run(DeviceChecks t)throws Exception{
  Context c=t.getTargetContext();CategoryOrderChecks.fixture(c);CategoryStore store=new CategoryStore(c);for(int i=1;i<=25;i++)store.remember("지출",String.format(Locale.ROOT,"분류 %02d",i));store.remember("지출",LONG);boolean dark=t.mode.equals("dragdark");new AppPreferences(c).dark(dark);if(dark)new AppPreferences(c).fontId("serif");MainActivity app=StartupChecks.start(t,false);CategoryOrderChecks.open(t,app);String original=CategoryOrderChecks.data(c);t.eq(true,names(t).size()>20);
  // Short movement on a name scrolls; holding the name starts a drag instead.
  List<String> initial=names(t);float[] p=point(t,initial.get(0),false);long down=SystemClock.uptimeMillis();pointer(t,down,MotionEvent.ACTION_DOWN,p[0],p[1]);float target=p[1]-130*app.getResources().getDisplayMetrics().density;move(t,down,p[0],p[1],target);Thread.sleep(180);pointer(t,down,MotionEvent.ACTION_MOVE,p[0],target);pointer(t,down,MotionEvent.ACTION_UP,p[0],target);settled(t);t.eq(true,scroll(t).getScrollY()>0);t.eq(initial,names(t));t.eq(initial,CategoryOrderChecks.choices(c,"지출"));
  longMove(t,c);cancelMove(t,c);
  // A real accessibility-tree action is the same operation offered by TalkBack's action menu.
  String first=names(t).get(0);point(t,first,true);AccessibilityNodeInfo accessible=node(t,"지출 "+first+" 상세 분류 ");t.eq(true,accessible!=null);boolean found=false;for(AccessibilityNodeInfo.AccessibilityAction a:accessible.getActionList())if(a.getId()==CategoryDrag.ACTION_MOVE_DOWN)found=true;t.eq(true,found);List<String> before=names(t);t.eq(true,accessible.performAction(CategoryDrag.ACTION_MOVE_DOWN));settled(t);t.eq(CategoryOrder.moveTo(before,0,1),names(t));
  // One drop writes the order once; a tap/no-op and CANCEL do not write it.
  final int[] writes={0};SharedPreferences preferences=c.getSharedPreferences("category_choices",0);SharedPreferences.OnSharedPreferenceChangeListener listener=(shared,key)->{if(key.equals("order_지출"))writes[0]++;};preferences.registerOnSharedPreferenceChangeListener(listener);
  try{auto(t,c,true);t.eq(1,writes[0]);auto(t,c,false);t.eq(2,writes[0]);cancelMove(t,c);t.eq(2,writes[0]);p=point(t,names(t).get(0),true);down=SystemClock.uptimeMillis();pointer(t,down,MotionEvent.ACTION_DOWN,p[0],p[1]);pointer(t,down,MotionEvent.ACTION_UP,p[0],p[1]);settled(t);t.eq(2,writes[0]);}finally{preferences.unregisterOnSharedPreferenceChangeListener(listener);}
  // A taller row must also reach both ends, not stop at the adjacent short row.
  autoName(t,c,LONG,true);autoName(t,c,LONG,false);
  // The variable-height long name can be moved relative to a shorter neighbour.
  before=names(t);int from=before.indexOf(LONG),to=from==before.size()-1?from-1:from+1;p=point(t,LONG,true);View neighbour=row(t,before.get(to));int[] at=new int[2];t.runOnMainSync(()->neighbour.getLocationOnScreen(at));target=at[1]+neighbour.getHeight()/2f+(to>from?6:-6);down=SystemClock.uptimeMillis();pointer(t,down,MotionEvent.ACTION_DOWN,p[0],p[1]);move(t,down,p[0],p[1],target);t.screenshot(c,"drag-long-name-"+t.mode);pointer(t,down,MotionEvent.ACTION_UP,p[0],target);settled(t);t.eq(CategoryOrder.moveTo(before,from,to),names(t));
  // Structural actions cannot invalidate the active gesture. Dismissal cancels it.
  before=names(t);String name=before.get(0),saved=orderPrefs(c);p=point(t,name,true);down=SystemClock.uptimeMillis();pointer(t,down,MotionEvent.ACTION_DOWN,p[0],p[1]);move(t,down,p[0],p[1],p[1]+row(t,name).getHeight()*1.2f);CategoryOrderChecks.click(t,"지출 "+name+" 상세 분류 삭제");CategoryOrderChecks.click(t,"관리에서 상세 분류 추가");CategoryOrderChecks.click(t,"수입 상세 분류 관리");t.eq(before,names(t));t.eq(saved,orderPrefs(c));t.eq(false,t.activeText("지출 상세 분류 추가"));
  t.clickText("닫기");pointer(t,down,MotionEvent.ACTION_CANCEL,p[0],p[1]);settled(t);t.eq(saved,orderPrefs(c));CategoryOrderChecks.open(t,app);t.eq(before,names(t));t.screenshot(c,"drag-manager-"+t.mode);t.eq(original,CategoryOrderChecks.data(c));
  // Ordinary delete still works when no drag is active, without deleting the ledger.
  CategoryOrderChecks.click(t,"지출 "+name+" 상세 분류 삭제");t.eq(false,names(t).contains(name));t.eq(original,CategoryOrderChecks.data(c));t.clickText("닫기");t.waitForIdleSync();StartupChecks.close(t,app);
 }
}
