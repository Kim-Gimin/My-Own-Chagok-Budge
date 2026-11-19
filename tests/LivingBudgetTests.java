package kr.chagok.ledger;
import java.io.*;
import java.time.*;
import java.util.*;

public final class LivingBudgetTests {
 static int checks;
 static void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("Expected "+a+", actual "+b);}
 static Ledger.Entry e(String id,String date,String type,long amount,String cat){return new Ledger.Entry(id,date,type,amount,cat,"",0);}
 static void rejects(Runnable action){checks++;try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected invalid selection rejection");}
 public static void main(String[] args)throws Exception{
  YearMonth month=YearMonth.of(2026,11);PlanningData.Limit limit=new PlanningData.Limit("*","지출","",1000000);
  List<Ledger.Entry> rows=new ArrayList<>(Arrays.asList(e("food","2026-11-01","지출",200000,"식비"),e("cafe","2026-11-02","지출",50000,"카페"),e("shop","2026-11-03","지출",100000,"쇼핑"),e("trip","2026-11-04","지출",300000,"출장비"),e("prior-food","2026-10-01","지출",10000,"식비"),e("prior-trip","2026-10-01","지출",50000,"출장비"),e("income","2026-11-01","수입",3000000,"식비"),e("saving","2026-11-01","저축",70000,"식비"),e("invest","2026-11-01","투자",80000,"식비")));
  PlanningData p=new PlanningData();p.put(limit);String legacy=p.encode();eq(false,p.hasLivingFilter());eq(650000L,p.budgetProgress(limit,month,rows).actual);eq(350000L,p.budgetProgress(limit,month,rows).remaining);eq(0x43485031,new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(legacy))).readInt());
  p.selectLivingCategories(Arrays.asList("식비","카페","쇼핑"));PlanningData.Progress result=p.budgetProgress(limit,month,rows);eq(350000L,result.actual);eq(650000L,result.remaining);eq(1000000L,result.target);eq(35.0,result.percent);
  eq(650000L,Ledger.totals(Ledger.month(rows,month))[1]);eq(650000L,PlanningData.progress(limit,month,rows).actual);eq(300000L,p.budgetProgress(new PlanningData.Limit("*","지출","출장비",500000),month,rows).actual);eq(70000L,p.budgetProgress(new PlanningData.Limit("*","저축","",100000),month,rows).actual);eq(80000L,p.budgetProgress(new PlanningData.Limit("*","투자","",100000),month,rows).actual);
  eq(10000L,p.budgetProgress(limit,month.minusMonths(1),rows).actual);
  rows.add(e("new","2026-11-05","지출",5000,"신규분류"));eq(350000L,p.budgetProgress(limit,month,rows).actual);
  Set<String> selected=new HashSet<>(p.livingCategories());selected.add("신규분류");p.selectLivingCategories(selected);eq(355000L,p.budgetProgress(limit,month,rows).actual);
  rows.set(0,e("food","2026-11-01","지출",180000,"식비"));eq(335000L,p.budgetProgress(limit,month,rows).actual);rows.removeIf(row->row.id.equals("cafe"));eq(285000L,p.budgetProgress(limit,month,rows).actual);eq(585000L,Ledger.totals(Ledger.month(rows,month))[1]);
  String encoded=p.encode();eq(0x43485032,new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded))).readInt());PlanningData restored=PlanningData.decode(encoded);eq(true,restored.hasLivingFilter());eq(p.livingCategories(),restored.livingCategories());eq(encoded,restored.encode());eq(285000L,restored.budgetProgress(limit,month,rows).actual);
  PlanningData old=PlanningData.decode(legacy);eq(false,old.hasLivingFilter());eq(legacy,old.encode());eq(585000L,old.budgetProgress(limit,month,rows).actual);
  PlanningData merged=PlanningData.merge(old,p);eq(p.livingCategories(),merged.livingCategories());eq(false,old.hasLivingFilter());
  PlanningData current=new PlanningData();current.selectLivingCategories(Collections.singleton("출장비"));eq(Collections.singleton("출장비"),PlanningData.merge(current,p).livingCategories());eq(Collections.singleton("출장비"),PlanningData.merge(current,old).livingCategories());
  p.selectLivingCategories(Collections.emptySet());eq(0L,p.budgetProgress(limit,month,rows).actual);eq(1000000L,p.budgetProgress(limit,month,rows).remaining);restored=PlanningData.decode(p.encode());eq(true,restored.hasLivingFilter());eq(0,restored.livingCategories().size());eq(0L,restored.budgetProgress(limit,month,rows).actual);eq(0L,PlanningData.merge(p,current).budgetProgress(limit,month,rows).actual);
  p.selectLivingCategories(Arrays.asList("食費","카페 : 1","식비"));ByteArrayOutputStream zip=new ByteArrayOutputStream();BackupArchive.write(zip,rows,p);BackupArchive.Snapshot archive=BackupArchive.read(new ByteArrayInputStream(zip.toByteArray()));eq(p.livingCategories(),archive.planning.livingCategories());eq(rows.size(),archive.entries.size());eq(180000L,archive.planning.budgetProgress(limit,month,archive.entries).actual);
  rejects(()->p.selectLivingCategories(Collections.singleton("")));rejects(()->p.selectLivingCategories(Collections.singleton(String.join("",Collections.nCopies(41,"가")))));rejects(()->p.selectLivingCategories(null));
  rejects(()->PlanningData.decode(encoded.substring(0,encoded.length()-4)));byte[] tail=Arrays.copyOf(Base64.getDecoder().decode(encoded),Base64.getDecoder().decode(encoded).length+1);rejects(()->PlanningData.decode(Base64.getEncoder().encodeToString(tail)));
  System.out.println("PASS: "+checks+" living-budget selection/backup checks");
 }
}
