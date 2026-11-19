package kr.chagok.ledger;
import java.io.*;import java.util.*;
public final class CategoryTests {
 static int n;static void eq(Object a,Object b){n++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 static Ledger.Entry e(String id,String type,String cat,long amount){return new Ledger.Entry(id,"2026-11-02",type,amount,cat,"중간  메모 ",0);}
 static String limits(String first,String second)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeInt(0x43485031);o.writeInt(2);for(int i=0;i<2;i++){o.writeUTF("*");o.writeUTF("지출");o.writeUTF(i==0?first:second);o.writeLong(i==0?100:200);}o.writeInt(0);o.writeInt(0);return Base64.getEncoder().encodeToString(b.toByteArray());}
 static String legacyMove(boolean detailed)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeInt(detailed?0x534d5632:0x534d5631);o.writeInt(1);o.writeUTF("legacy-space");o.writeUTF("2026-11-02");o.writeUTF("\u00a0적금\u3000");o.writeLong(1000);o.writeLong(1010);o.writeLong(500);o.writeUTF("\u200b예금\ufeff");o.writeLong(400);o.writeUTF("주식\u00a0");o.writeUTF("원래 메모");if(detailed){o.writeBoolean(true);o.writeLong(1000);o.writeLong(0);o.writeBoolean(true);}return Base64.getEncoder().encodeToString(b.toByteArray());}
 public static void main(String[] args)throws Exception{
  eq("",Ledger.cleanCategory(null));for(String edge:new String[]{" ","\t","\n","\u00a0","\u3000","\ufeff","\u200b","\u202f"}){eq("식비",Ledger.cleanCategory(edge+"식비"+edge));eq("",Ledger.cleanCategory(edge));eq("식"+edge+"비",Ledger.cleanCategory("식"+edge+"비"));}
  eq("기타",e("empty","지출","\u200b\u00a0",1).category);eq("미국 투자",e("stock","투자","\u3000미국 투자\ufeff",1).category);
  PlanningData plan=new PlanningData();eq(0,CategoryNames.choices("지출",Collections.emptyList(),plan,Collections.emptySet(),Collections.emptySet()).size());
  plan.put(new PlanningData.Limit("*","지출","\u00a0예산분류\u3000",10));plan.put(new PlanningData.Favorite("f","틀","지출",10,"반복없는틀",""));plan.put(new PlanningData.Repeat("r","반복","지출",10,"반복분류","","MONTHLY",java.time.LocalDate.of(2026,11,2),2,11,true));plan.selectLivingCategories(Arrays.asList("\u00a0생활분류\u200b"));
  List<Ledger.Entry> entries=Arrays.asList(e("food","지출","식비",10),e("trip","지출","출장비",100),e("income","수입","식비",1000),e("saving","저축","식비",200),e("invest","투자","식비",300),e("recover","저축회수","식비",20));
  Set<String> choices=new TreeSet<>(CategoryNames.choices("지출",entries,plan,Arrays.asList("직접추가","\u00a0식비"),Arrays.asList("출장비")));eq(new TreeSet<>(Arrays.asList("식비","예산분류","반복없는틀","반복분류","생활분류","직접추가")),choices);eq(false,choices.contains("카페·간식"));
  eq(0,plan.livingEntries(entries).size());plan.selectLivingCategories(Arrays.asList("식비"));eq(1,plan.livingEntries(entries).size());eq(10L,Ledger.totals(plan.livingEntries(entries))[1]);eq(110L,Ledger.totals(entries)[1]);eq("food",plan.livingEntries(entries).get(0).id);plan.selectLivingCategories(Collections.emptySet());eq(0,plan.livingEntries(entries).size());eq(2,new PlanningData().livingEntries(entries).size());
  eq(plan.encode(),PlanningData.decode(plan.encode()).encode());PlanningData noFilter=new PlanningData();eq(false,PlanningData.decode(noFilter.encode()).hasLivingFilter());
  List<String> log=new ArrayList<>();PlanningData cleaned=PlanningData.decode(limits("\u00a0식비","식비"),log);eq(1,cleaned.limits.size());eq(200L,cleaned.limits.get(0).amount);eq(1,log.size());cleaned=PlanningData.decode(limits("식비","\u3000식비"));eq(100L,cleaned.limits.get(0).amount);eq(1,cleaned.limits.size());
  for(boolean detailed:new boolean[]{false,true}){SavingsMovement m=SavingsMovement.decodeAll(legacyMove(detailed)).get(0);eq("적금",m.source);eq("예금",m.savingCategory);eq("주식",m.investmentCategory);for(Ledger.Entry row:m.entries())eq("저축 해지·배분 · \u00a0적금\u3000 → \u200b예금\ufeff / 주식\u00a0 · 원래 메모",row.memo);SavingsMovement again=SavingsMovement.decodeAll(m.encode()).get(0);eq(m.encode(),again.encode());for(int i=0;i<m.entries().size();i++)eq(m.entries().get(i).fingerprint(),again.entries().get(i).fingerprint());}
  eq("적금",new OpeningAsset("basis","2026-01-01","저축",1,"\ufeff적금\u3000","").category);eq("기타",new OpeningAsset("basis","2026-01-01","저축",1,"\ufeff","").category);
  System.out.println("PASS: "+n+" category normalization/calendar checks");
 }
}
