package kr.chagok.ledger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

/** Synthetic fixtures only: time validation, old file compatibility and round trips. */
public final class TimeTests {
 static int checks; interface Action{void run()throws Exception;}
 static void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("expected="+a+", actual="+b);}
 static void rejects(Action a)throws Exception{checks++;try{a.run();}catch(Exception ok){return;}throw new AssertionError("expected rejection");}
 static Ledger.Entry e(String id,String clock){return new Ledger.Entry(id,"2026-06-27",clock,"지출",100,"식비","시간 합성 예시",0);}
 static SavingsMovement decode(String value){return SavingsMovement.decodeAll(value).get(0);}
 static int magic(String value)throws Exception{return new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value))).readInt();}
 static byte[] xlsx(List<Ledger.Entry> rows)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();XlsxIO.write(b,rows,false);return b.toByteArray();}
 static Map<String,byte[]> parts(byte[] data)throws Exception{Map<String,byte[]> p=new LinkedHashMap<>();try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(data))){ZipEntry e;byte[] buf=new byte[8192];while((e=z.getNextEntry())!=null){ByteArrayOutputStream b=new ByteArrayOutputStream();int n;while((n=z.read(buf))!=-1)b.write(buf,0,n);p.put(e.getName(),b.toByteArray());}}return p;}
 static byte[] zip(Map<String,byte[]> p)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){for(Map.Entry<String,byte[]> v:p.entrySet()){z.putNextEntry(new ZipEntry(v.getKey()));z.write(v.getValue());z.closeEntry();}}return b.toByteArray();}
 static String cell(String address,String value){return "<c r=\""+address+"\" t=\"inlineStr\"><is><t>"+value+"</t></is></c>";}
 static XlsxIO.ReadResult fixture(String clock,String date,boolean hasTime,boolean date1904)throws Exception{
  Map<String,byte[]> p=parts(xlsx(Arrays.asList(e("fixture","00:00:00"))));
  String header="<row r=\"1\">"+cell("A1","날짜")+cell("B1","내역")+cell("C1","상세 분류")+cell("D1","금액")+cell("E1","돈의 성격")+cell("F1","고유ID")+(hasTime?cell("G1","시간"):"")+"</row>";
  String row="<row r=\"2\">"+cell("A2",date)+cell("B2","시간 예시")+cell("C2","식비")+cell("D2","100")+cell("E2","지출")+cell("F2","fixture")+(hasTime?clock:"")+"</row>";
  p.put("xl/worksheets/sheet2.xml",("<worksheet><sheetData>"+header+row+"</sheetData></worksheet>").getBytes(StandardCharsets.UTF_8));
  if(date1904){String book=new String(p.get("xl/workbook.xml"),StandardCharsets.UTF_8).replace("<sheets>","<workbookPr date1904=\"1\"/><sheets>");p.put("xl/workbook.xml",book.getBytes(StandardCharsets.UTF_8));}
  return XlsxIO.read(new ByteArrayInputStream(zip(p)));
 }
 public static void main(String[] args)throws Exception{
  for(String v:new String[]{null,"","  ","0:00","00:00:00"})eq("00:00:00",Ledger.normalizeTime(v));
  eq("09:07:00",Ledger.normalizeTime("9:07"));eq("23:59:59",Ledger.normalizeTime("23:59:59"));
  for(String v:new String[]{"24:00:00","-1:00","12:60","12:00:60","1:2","1:02:3","12:00:00Z","0.5","12:00:00.1"})rejects(()->Ledger.normalizeTime(v));
  Ledger.Entry old=new Ledger.Entry("old","2026-06-27","지출",100,"식비","시간 합성 예시",0);
  eq("00:00:00",old.time);eq(old.fingerprint(),e("old","00:00:00").fingerprint());eq("12:34:56",e("a","12:34:56").withId("b").time);
  Ledger.ImportPlan plan=Ledger.planImport(Arrays.asList(e("","12:00:00"),e("","13:00:00"),e("","12:00:00")),Collections.emptyList());eq(2,plan.additions.size());eq(1,plan.duplicates);
  eq(1,Ledger.planImport(Arrays.asList(old),Arrays.asList(e("old","00:00:00"))).duplicates);eq(1,Ledger.planImport(Arrays.asList(e("old","12:00:00")),Arrays.asList(old)).errors.size());
  List<Ledger.Entry> rows=Arrays.asList(e("first","00:00:00"),e("noon","12:30:05"),e("late","23:59:59"));
  HistoryData.Result history=HistoryData.build(rows,null,"","",Collections.emptySet(),HistoryData.DATE);eq("late",history.entries.get(0).id);eq("noon",history.entries.get(1).id);eq(-300L,Ledger.available(history.totals));eq(300L,StatisticsData.aggregate(rows,"지출").total);
  XlsxIO.ReadResult read=XlsxIO.read(new ByteArrayInputStream(xlsx(rows)));eq(0,read.invalidRows);eq(3,read.entries.size());for(Ledger.Entry row:rows)eq(row.fingerprint(),read.entries.stream().filter(x->x.id.equals(row.id)).findFirst().get().fingerprint());
  for(String value:new String[]{"09:30","09:30:01","23:59:59","00:00:00"}){read=fixture(cell("G2",value),"2026-06-27",true,false);eq(0,read.invalidRows);eq(Ledger.normalizeTime(value),read.entries.get(0).time);}
  read=fixture("<c r=\"G2\"><v>0.5</v></c>","2026-06-27",true,false);eq("12:00:00",read.entries.get(0).time);
  for(boolean date1904:new boolean[]{false,true}){read=fixture("","46200.75",false,date1904);eq(0,read.invalidRows);eq("18:00:00",read.entries.get(0).time);eq(date1904?LocalDate.of(1904,1,1).plusDays(46200).toString():"2026-06-27",read.entries.get(0).date);}
  for(boolean hasTime:new boolean[]{false,true}){read=fixture("","2026-06-27",hasTime,false);eq("00:00:00",read.entries.get(0).time);}
  read=fixture("","2026-06-27T14:02:03",false,false);eq("14:02:03",read.entries.get(0).time);
  read=fixture(cell("G2","08:01:02"),"2026-06-27 14:02:03",true,false);eq("08:01:02",read.entries.get(0).time);
  for(String value:new String[]{"24:00:00","-0.1","1","abc","12:99:00"}){read=fixture(cell("G2",value),"2026-06-27",true,false);eq(1,read.invalidRows);eq(0,read.entries.size());}
  read=fixture("<c r=\"G2\"><f>NOW()</f><v>0.5</v></c>","2026-06-27",true,false);eq(1,read.invalidRows);
  read=fixture("<c r=\"G2\" t=\"e\"><v>#VALUE!</v></c>","2026-06-27",true,false);eq(1,read.invalidRows);
  SavingsMovement legacy=new SavingsMovement("legacy","2026-06-27","적금",1000,1010,500,"예금",400,"ETF","합성 이동");
  SavingsMovement detailed=new SavingsMovement("detailed","2026-06-27","적금",1000,1100,500,"예금",400,"ETF","합성 이동",600,50,true,true);
  for(SavingsMovement m:Arrays.asList(legacy,detailed)){String original=m.encode();eq("00:00:00",decode(original).time);eq(original,decode(original).encode());SavingsMovement timed=m.withTime("14:05:36");eq(0x534d5634,magic(timed.encode()));SavingsMovement back=decode(timed.encode());eq("14:05:36",back.time);for(Ledger.Entry entry:back.entries())eq("14:05:36",entry.time);eq(m.available(),back.available());eq(m.interest(),back.interest());}
  for(boolean timed:new boolean[]{false,true})for(boolean movement:new boolean[]{false,true}){List<Ledger.Entry> data=timed?rows:Arrays.asList(old);List<SavingsMovement> moves=movement?Arrays.asList(timed?detailed.withTime("14:05:36"):detailed):Collections.emptyList();List<Ledger.Entry> combined=new ArrayList<>(data);for(SavingsMovement m:moves)combined.addAll(m.entries());ByteArrayOutputStream b=new ByteArrayOutputStream();BackupArchive.write(b,combined,new PlanningData(),moves);eq("CHAGOK-PLANNING-BACKUP-"+(timed?"3":movement?"2":"1"),new String(parts(b.toByteArray()).get("format.txt"),StandardCharsets.UTF_8));BackupArchive.Snapshot restored=BackupArchive.read(new ByteArrayInputStream(b.toByteArray()));eq(combined.size(),restored.entries.size());for(Ledger.Entry v:combined)eq(v.fingerprint(),restored.entries.stream().filter(x->x.id.equals(v.id)).findFirst().get().fingerprint());eq(moves.size(),restored.movements.size());if(movement)eq(moves.get(0).encode(),restored.movements.get(0).encode());}
  if(args.length>0)try(OutputStream out=Files.newOutputStream(Paths.get(args[0]))){XlsxIO.write(out,Collections.emptyList(),true);}
  System.out.println("PASS: "+checks+" transaction time/legacy XLSX/ZIP/movement checks");
 }
}
