package kr.chagok.ledger;
import java.io.*;
import java.util.*;
public final class RecoveryTests {
 static int checks;
 static void eq(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}
 static Ledger.Entry e(String id,String type,long amount,String category){return new Ledger.Entry(id,"2026-11-16",type,amount,category,"",0);}
 static Map<String,String> parts(byte[] bytes)throws Exception{Map<String,String> result=new HashMap<>();try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))){java.util.zip.ZipEntry entry;byte[] buffer=new byte[4096];while((entry=zip.getNextEntry())!=null){ByteArrayOutputStream data=new ByteArrayOutputStream();int count;while((count=zip.read(buffer))!=-1)data.write(buffer,0,count);result.put(entry.getName(),new String(data.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));}}return result;}
 public static void main(String[] args)throws Exception{
  eq(4,Ledger.TYPES.length);eq(5,Ledger.INPUT_TYPES.length);eq(5,new HashSet<>(Arrays.asList(Ledger.INPUT_TYPES)).size());
  List<Ledger.Entry> rows=new ArrayList<>();for(int month=1;month<=7;month++)rows.add(new Ledger.Entry("monthly-"+month,"2026-0"+month+"-01","저축",500000,"예시적금","",0));
  long before=Ledger.available(Ledger.totals(rows));eq(-3500000L,before);
  Ledger.Entry recovery=e("manual-recovery","저축회수",3500000,"예시적금");rows.add(recovery);
  eq(0L,Ledger.totals(rows)[0]);eq(0L,Ledger.totals(rows)[1]);eq(0L,Ledger.totals(rows)[2]);eq(3500000L,Ledger.available(Ledger.totals(rows))-before);
  eq(0L,StatisticsData.aggregate(rows,"저축").total);eq(0,StatisticsData.aggregate(rows,"저축").chartCategories.size());
  rows.add(e("interest","수입",120000,"이자"));rows.add(e("grant","수입",80000,"정부기여금"));rows.add(e("new-deposit","저축",1500000,"예금"));rows.add(e("stock","투자",1500000,"주식"));
  long[] totals=Ledger.totals(rows);eq(200000L,totals[0]);eq(0L,totals[1]);eq(1500000L,totals[2]);eq(1500000L,totals[3]);eq(700000L,Ledger.available(totals)-before);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();XlsxIO.write(bytes,rows,false);XlsxIO.ReadResult read=XlsxIO.read(new ByteArrayInputStream(bytes.toByteArray()));eq(0,read.invalidRows);eq(12,read.entries.size());eq(Ledger.available(totals),Ledger.available(Ledger.totals(read.entries)));
  Ledger.ImportPlan duplicates=Ledger.planImport(read.entries,rows);eq(12,duplicates.duplicates);eq(0,duplicates.additions.size());eq(0,duplicates.errors.size());
  eq(1,Ledger.planImport(Arrays.asList(e(recovery.id,"저축회수",3500001,recovery.category)),rows).errors.size());
  bytes.reset();XlsxIO.write(bytes,Collections.emptyList(),true);Map<String,String> parts=parts(bytes.toByteArray());
  eq(true,parts.get("xl/worksheets/sheet1.xml").contains("수입,지출,저축,투자,저축회수"));eq(true,parts.get("xl/worksheets/sheet3.xml").contains("저축회수"));eq(true,parts.get("xl/worksheets/sheet3.xml").contains("입력 예시 9건"));eq(0,XlsxIO.read(new ByteArrayInputStream(bytes.toByteArray())).entries.size());
  if(args.length>0)try(OutputStream out=new FileOutputStream(args[0])){out.write(bytes.toByteArray());}
  System.out.println("PASS: "+checks+" recovery checks");
 }
}
