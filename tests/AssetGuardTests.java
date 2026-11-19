package kr.chagok.ledger;
import java.util.*;
public final class AssetGuardTests {
 static int count;static void eq(Object expected,Object actual){count++;if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}
 static void rejects(Runnable r){try{r.run();throw new AssertionError("Invalid state accepted");}catch(IllegalArgumentException ok){count++;}}
 static AssetConsistency.State state(List<OpeningAsset> assets,List<Ledger.Entry> entries,List<SavingsMovement> moves){return new AssetConsistency.State(assets,entries,moves);}
 public static void main(String[] args){
  OpeningAsset base=new OpeningAsset("base","2026-01-01","저축",20000000,"적금","");Ledger.Entry paid=new Ledger.Entry("paid","2026-01-01","저축",20000000,"적금","",0);
  AssetConsistency.State before=state(Collections.emptyList(),Arrays.asList(paid),Collections.emptyList());
  SavingsMovement m=new SavingsMovement("example","2026-07-01","적금",20000000,25000000,21000000,"예금",3000000,"주식","",0,0,false,true);
  List<Ledger.Entry> rows=new ArrayList<>(before.entries);rows.addAll(m.entries());AssetConsistency.State after=state(Collections.emptyList(),rows,Arrays.asList(m));AssetConsistency.validate(before,after);eq(1000000L,m.available());eq(1000000L,Ledger.available(Ledger.totals(rows))-Ledger.available(Ledger.totals(before.entries)));eq(0L,OpeningAsset.balances(after.opening,rows,m.date,after.movements).get("저축 · 적금"));
  SavingsMovement again=new SavingsMovement("again","2026-07-02","적금",20000000,20000000,20000000,"다른 예금",0,"주식","",0,0,false,true);List<Ledger.Entry> twice=new ArrayList<>(rows);twice.addAll(again.entries());rejects(()->AssetConsistency.validate(after,state(Collections.emptyList(),twice,Arrays.asList(m,again))));
  List<Ledger.Entry> reduced=new ArrayList<>(rows);reduced.set(0,new Ledger.Entry("paid",paid.date,paid.type,1,paid.category,"",0));rejects(()->AssetConsistency.validate(after,state(Collections.emptyList(),reduced,Arrays.asList(m))));
  SavingsMovement legacy=new SavingsMovement("old","2026-07-01","적금",20000000,25000000,21000000,"예금",3000000,"주식","");List<Ledger.Entry> oldRows=legacy.entries();Map<String,Long> oldHoldings=OpeningAsset.balances(Arrays.asList(base),oldRows,legacy.date,Arrays.asList(legacy));eq(0L,oldHoldings.get("저축 · 적금"));eq(21000000L,oldHoldings.get("저축 · 예금"));eq(1000000L,oldHoldings.get("현금 · 거래 후 잔액"));eq(0L,OpeningAsset.balances(Arrays.asList(base),oldRows,"2026-06-30",Arrays.asList(legacy)).get("현금 · 거래 후 잔액"));eq(20000000L,OpeningAsset.balances(Arrays.asList(base),oldRows,"2026-06-30",Arrays.asList(legacy)).get("저축 · 적금"));
  for(long saving:new long[]{0,10000000,20000000,21000000}){SavingsMovement old=new SavingsMovement("old","2026-07-01","적금",20000000,25000000,saving,"예금",0,"주식","");Map<String,Long> h=OpeningAsset.balances(Arrays.asList(base),old.entries(),old.date,Arrays.asList(old));eq(0L,h.get("저축 · 적금"));eq(saving,h.getOrDefault("저축 · 예금",0L));eq(25000000-saving,h.get("현금 · 거래 후 잔액"));}
  AssetConsistency.State legacyState=state(Arrays.asList(base),oldRows,Arrays.asList(legacy));rejects(()->AssetConsistency.validate(legacyState,state(Collections.emptyList(),oldRows,Arrays.asList(legacy))));
  SavingsMovement next=new SavingsMovement("next","2026-08-01","예금",21000000,21000000,21000000,"새 예금",0,"주식","",0,0,false,true);List<Ledger.Entry> chain=new ArrayList<>(oldRows);chain.addAll(next.entries());AssetConsistency.validate(legacyState,state(Arrays.asList(base),chain,Arrays.asList(legacy,next)));count++;
  // Persisted incomplete history is not rewritten or blocked when it stays unchanged.
  SavingsMovement self=new SavingsMovement("self","2026-07-01","적금",20000000,20000000,20000000,"적금",0,"주식","",0,0,false,true);
  rejects(()->AssetConsistency.validate(state(Collections.emptyList(),Collections.emptyList(),Collections.emptyList()),state(Collections.emptyList(),self.entries(),Arrays.asList(self))));
  AssetConsistency.validate(before,state(Collections.emptyList(),new ArrayList<Ledger.Entry>(){{add(paid);addAll(self.entries());}},Arrays.asList(self)));count++;
  AssetConsistency.State incomplete=state(Collections.emptyList(),m.entries(),Arrays.asList(m));AssetConsistency.validate(incomplete,incomplete);count++;
  // Unrelated ordinary cash transactions can be incomplete; this feature does not invent bank balances.
  List<Ledger.Entry> expense=new ArrayList<>(rows);expense.add(new Ledger.Entry("cash","2026-08-01","지출",Ledger.MAX_AMOUNT,"기타","",0));AssetConsistency.validate(after,state(Collections.emptyList(),expense,Arrays.asList(m)));count++;
  System.out.println("PASS: "+count+" asset consistency checks");
 }
}
