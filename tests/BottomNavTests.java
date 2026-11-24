package kr.chagok.ledger;
import java.util.*;

/** 1.0.0 하단 메뉴 구성 계약. 화면 없이 순수 Java로 검사한다. */
public final class BottomNavTests {
 static int checks;interface Action{void run()throws Exception;}
 static void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("expected="+a+", actual="+b);}
 static void ok(boolean v,String what){checks++;if(!v)throw new AssertionError(what);}
 static void rejects(Action a)throws Exception{checks++;try{a.run();}catch(Exception good){return;}throw new AssertionError("expected rejection");}
 static List<String> ids(String... values){return Arrays.asList(values);}
 public static void main(String[] args)throws Exception{
  // 기존 사용자는 업데이트 후에도 0.9.2와 같은 하단 6개(라벨·아이콘·화면·순서)를 본다.
  List<String> def=BottomNav.parse(null);eq(BottomNav.DEFAULT,def);eq(6,def.size());
  String[] oldLabels={"결산","통계","홈","달력","내역","엑셀"},oldIcons={"summary","pie","home","calendar","list","file"};int[] oldTargets={4,2,5,0,1,3};
  List<BottomNav.Item> defItems=BottomNav.items(def);
  for(int i=0;i<6;i++){eq(oldLabels[i],defItems.get(i).label);eq(oldIcons[i],defItems.get(i).icon);eq(oldTargets[i],defItems.get(i).target);ok(defItems.get(i).screen(),"default items are screens");}
  // 후보: 메뉴의 기능 10개. 설정·설명서는 넣지 않는다(설정은 상단 톱니, 두 화면은 하단 메뉴를 숨김).
  eq(10,BottomNav.ITEMS.size());Set<String> seenIds=new HashSet<>();Set<Integer> seenTargets=new HashSet<>();
  for(BottomNav.Item item:BottomNav.ITEMS){ok(seenIds.add(item.id),"unique id "+item.id);ok(seenTargets.add(item.target),"unique target "+item.target);ok(!item.label.isEmpty()&&item.label.length()<=5,"short label "+item.label);ok(!item.menuName.isEmpty(),"menu name");ok(item.target!=6&&item.target!=9,"no settings/guide");ok(item.id.matches("[a-z]+"),"plain id");eq(item,BottomNav.item(item.id));}
  eq(BottomNav.OPEN_OPENING,BottomNav.item("opening").target);eq(BottomNav.OPEN_MOVEMENT,BottomNav.item("movement").target);ok(!BottomNav.item("opening").screen(),"dialog item");ok(!BottomNav.item("movement").screen(),"dialog item");
  eq(7,BottomNav.item("planning").target);eq(8,BottomNav.item("trend").target);eq(null,BottomNav.item("settings"));eq(null,BottomNav.item(null));
  // 저장·읽기 왕복
  List<String> custom=ids("home","planning","records","movement");String enc=BottomNav.encode(custom);eq("BNV1:home,planning,records,movement",enc);eq(custom,BottomNav.parse(enc));
  for(int n=BottomNav.MIN;n<=BottomNav.MAX;n++){List<String> pick=new ArrayList<>();for(int i=0;i<n;i++)pick.add(BottomNav.ITEMS.get(BottomNav.ITEMS.size()-1-i).id);eq(pick,BottomNav.parse(BottomNav.encode(pick)));}
  // 잘못된 저장값: 모르는 항목·중복·공백은 정리하고, 범위를 벗어나면 기본 배치.
  eq(ids("home","trend","files"),BottomNav.parse("BNV1:home, trend ,unknown,home,files"));
  eq(def,BottomNav.parse(""));eq(def,BottomNav.parse("home,trend,files"));eq(def,BottomNav.parse("BNV2:home,trend,files"));eq(def,BottomNav.parse("BNV1:"));eq(def,BottomNav.parse("BNV1:home,trend"));eq(def,BottomNav.parse("BNV1:home,home,home,trend"));
  eq(def,BottomNav.parse("BNV1:settlement,statistics,home,calendar,records,files,planning"));eq(def,BottomNav.parse("BNV1:,,,"));eq(def,BottomNav.parse("BNV1:settings,guide,menu"));
  rejects(()->BottomNav.encode(ids("home","trend")));rejects(()->BottomNav.encode(ids("home","trend","files","records","calendar","planning","opening")));
  rejects(()->BottomNav.encode(ids("home","home","trend")));rejects(()->BottomNav.encode(ids("home","trend","settings")));rejects(()->BottomNav.encode(null));rejects(()->BottomNav.encode(ids("home","trend",null)));
  // 추가·빼기는 3~6개를 지키고 원본 목록을 바꾸지 않는다.
  List<String> three=ids("home","calendar","records");eq(three,BottomNav.remove(three,"home"));ok(!BottomNav.canRemove(three),"min");ok(BottomNav.canAdd(three),"can add");
  eq(ids("home","calendar","records","trend"),BottomNav.add(three,"trend"));eq(three,BottomNav.add(three,"home"));eq(three,BottomNav.add(three,"nope"));eq(ids("home","calendar","records"),three);
  List<String> six=new ArrayList<>(def);eq(six,BottomNav.add(six,"trend"));ok(!BottomNav.canAdd(six),"max");ok(BottomNav.canRemove(six),"can remove");
  eq(ids("settlement","statistics","calendar","records","files"),BottomNav.remove(six,"home"));eq(def,six);eq(six,BottomNav.remove(six,"trend"));
  eq(ids("planning","trend","opening","movement"),BottomNav.available(def));eq(BottomNav.ITEMS.size()-3,BottomNav.available(three).size());ok(!BottomNav.available(three).contains("home"),"available excludes chosen");
  // 끌어서 놓기
  eq(ids("home","settlement","statistics","calendar","records","files"),BottomNav.moveTo(def,2,0));eq(ids("statistics","home","calendar","records","files","settlement"),BottomNav.moveTo(def,0,5));
  eq(def,BottomNav.moveTo(def,2,2));eq(def,BottomNav.moveTo(def,-1,2));eq(def,BottomNav.moveTo(def,0,6));eq(def,BottomNav.moveTo(def,6,0));
  for(int a=0;a<6;a++)for(int b=0;b<6;b++){List<String> moved=BottomNav.moveTo(def,a,b);eq(def.get(a),moved.get(b));eq(new HashSet<>(def),new HashSet<>(moved));eq(def,BottomNav.moveTo(moved,b,a));ok(BottomNav.valid(moved),"moved valid");}
  eq(BottomNav.DEFAULT,def);
  // 현재 화면 표시: 화면 항목만, 위치는 사용자 순서 기준.
  eq(2,BottomNav.activeIndex(def,5));eq(1,BottomNav.activeIndex(def,2));eq(-1,BottomNav.activeIndex(def,7));eq(-1,BottomNav.activeIndex(def,6));eq(-1,BottomNav.activeIndex(def,9));
  List<String> withDialogs=ids("opening","movement","trend");eq(-1,BottomNav.activeIndex(withDialogs,-1));eq(-1,BottomNav.activeIndex(withDialogs,-2));eq(2,BottomNav.activeIndex(withDialogs,8));
  eq(0,BottomNav.activeIndex(ids("planning","home","records"),7));eq(1,BottomNav.activeIndex(ids("planning","home","records"),5));
  System.out.println("PASS: "+checks+" bottom navigation selection/order/codec checks");
 }
}
