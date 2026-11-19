package kr.chagok.ledger;
import java.io.*;import java.util.*;import java.util.zip.*;
public class OpeningTests {
 static int n;static void eq(Object a,Object b){n++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 static void rejects(Runnable r){try{r.run();throw new AssertionError("accepted invalid");}catch(IllegalArgumentException expected){n++;}}
 static SavingsMovement movement(){return new SavingsMovement("synthetic-example","2026-07-15","예시적금",10000000,10200000,8000000,"예금",1500000,"주식","",6500000,80000,true,true);}
 static byte[] export(List<Ledger.Entry> rows,List<OpeningAsset> assets,boolean template)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();XlsxIO.write(out,rows,template,assets);return out.toByteArray();}
 public static void main(String[] args)throws Exception{
  SavingsMovement m=movement();eq(120000L,m.interest());eq(700000L,m.available());eq(5,m.entries().size());eq(6500000L,m.opening().amount);eq("2026-01-01",m.opening().date);
  List<Ledger.Entry> rows=new ArrayList<>();for(int i=1;i<=7;i++)rows.add(new Ledger.Entry("month-"+i,"2026-0"+i+"-01","저축",500000,"예시적금","납입",0));long before=Ledger.available(Ledger.totals(rows));rows.addAll(m.entries());
  long[] totals=Ledger.totals(rows);eq(200000L,totals[0]);eq(0L,totals[1]);eq(1500000L,totals[2]);eq(1500000L,totals[3]);eq(700000L,Ledger.available(totals)-before);eq(4,totals.length);
  eq(8000000L,rows.stream().filter(e->e.id.equals(m.prefix()+"saving")).findFirst().get().amount);eq(10000000L,rows.stream().filter(e->e.type.equals("저축회수")).findFirst().get().amount);
  Map<String,Long> holdings=OpeningAsset.balances(Arrays.asList(m.opening()),rows,"2026-07-15");eq(0L,holdings.get("저축 · 예시적금"));eq(8000000L,holdings.get("저축 · 예금"));eq(1500000L,holdings.get("투자 · 주식"));
  OpeningAsset cash=new OpeningAsset("cash","2026-01-01","현금",3500000,"기존 현금","");holdings=OpeningAsset.balances(Arrays.asList(m.opening(),cash),rows,"2026-07-15");eq(700000L,holdings.get("현금 · 거래 후 잔액"));eq(6500000L,OpeningAsset.balances(Arrays.asList(m.opening()),Collections.emptyList(),"2026-01-01").get("저축 · 예시적금"));eq(1,OpeningAsset.balances(Arrays.asList(m.opening()),Collections.emptyList(),"2025-12-31").size());
  SavingsMovement legacy=new SavingsMovement("legacy","2025-12-31","적금",1000,1010,500,"예금",400,"주식","");List<SavingsMovement> both=SavingsMovement.decodeAll(SavingsMovement.encodeAll(Arrays.asList(legacy,m)));eq(legacy.encode(),both.get(0).encode());eq(m.encode(),both.get(1).encode());eq(false,both.get(0).detailed);eq(true,both.get(1).detailed);eq(m.opening().fingerprint(),both.get(1).opening().fingerprint());
  byte[] xlsx=export(rows,Arrays.asList(m.opening()),false);XlsxIO.ReadResult read=XlsxIO.read(new ByteArrayInputStream(xlsx));eq(0,read.invalidRows);eq(12,read.entries.size());eq(1,read.openingAssets.size());eq(m.opening().id,read.openingAssets.get(0).id);eq(m.opening().fingerprint(),read.openingAssets.get(0).fingerprint());eq(12,Ledger.planImport(read.entries,rows).duplicates);
  Ledger.ImportPlan plan=new Ledger.ImportPlan();OpeningAsset.plan(read.openingAssets,Arrays.asList(m.opening()),plan);eq(1,plan.openingDuplicates);eq(0,plan.openingAdditions.size());
  plan=new Ledger.ImportPlan();OpeningAsset.plan(Arrays.asList(m.opening().withId("")),Arrays.asList(m.opening()),plan);eq(1,plan.openingDuplicates);
  plan=new Ledger.ImportPlan();OpeningAsset.plan(Arrays.asList(new OpeningAsset(m.opening().id,m.opening().date,"저축",1,"다른 분류","")),Arrays.asList(m.opening()),plan);eq(1,plan.errors.size());
  ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.write(out,rows,new PlanningData(),Arrays.asList(m),Arrays.asList(m.opening()));BackupArchive.Snapshot snapshot=BackupArchive.read(new ByteArrayInputStream(out.toByteArray()));eq(12,snapshot.entries.size());eq(1,snapshot.openingAssets.size());eq(m.encode(),snapshot.movements.get(0).encode());
  Map<String,String> parts=RecoveryTests.parts(xlsx);parts.put("xl/worksheets/sheet4.xml",parts.get("xl/worksheets/sheet4.xml").replace(">저축<",">수입<"));out.reset();try(ZipOutputStream zip=new ZipOutputStream(out)){for(Map.Entry<String,String> e:parts.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}}read=XlsxIO.read(new ByteArrayInputStream(out.toByteArray()));eq(1,read.invalidRows);eq(0,read.openingAssets.size());eq(12,read.entries.size());
  rejects(()->new OpeningAsset("x","2026-01-01","수입",1,"기타",""));rejects(()->new OpeningAsset("x","2026-01-01","저축",0,"기타",""));rejects(()->new SavingsMovement("x","2026-07-15","적금",10,10,10,"예금",0,"주식","",11,0,true,true));rejects(()->new SavingsMovement("x","2026-07-15","적금",10,10,10,"예금",0,"주식","",0,0,true,true));
  Ledger.ImportPlan dates=new Ledger.ImportPlan();OpeningAsset.plan(Arrays.asList(new OpeningAsset("different-date","2025-01-01","현금",1,"기존 현금","")),Arrays.asList(m.opening()),dates);eq(1,dates.errors.size());
  eq(m.opening().encode(),OpeningAsset.decodeAll(m.opening().encode()).get(0).encode());
  byte[] template=export(Collections.emptyList(),Collections.emptyList(),true);read=XlsxIO.read(new ByteArrayInputStream(template));eq(0,read.invalidRows);eq(0,read.entries.size());eq(0,read.openingAssets.size());eq(true,RecoveryTests.parts(template).get("xl/worksheets/sheet4.xml").contains("현금,저축,투자"));
  eq(true,RecoveryTests.parts(template).get("xl/worksheets/sheet3.xml").contains("8000000"));
  if(args.length>0)try(OutputStream file=new FileOutputStream(args[0])){file.write(template);}
  System.out.println("PASS: "+n+" opening asset checks");
 }
}
