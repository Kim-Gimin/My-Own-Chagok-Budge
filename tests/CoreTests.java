package kr.chagok.ledger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

public final class CoreTests {
    private static int checks;
    private interface Check { void run() throws Exception; }
    private static void eq(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("expected="+expected+", actual="+actual);}
    private static void rejects(Check run) throws Exception {checks++;try{run.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    private static Ledger.Entry entry(String id,String date,String type,long amount){return new Ledger.Entry(id,date,type,amount,"분류","메모",2);}
    private static XlsxIO.ReadResult roundtrip(List<Ledger.Entry> input) throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();XlsxIO.write(out,input,false);return XlsxIO.read(new ByteArrayInputStream(out.toByteArray()));}
    private static String s(String cell,String text){return "<c r=\""+cell+"\" t=\"inlineStr\"><is><t>"+text+"</t></is></c>";}
    private static String headers(){return "<row r=\"1\">"+s("A1","날짜")+s("B1","구분")+s("C1","금액")+s("D1","분류")+s("E1","메모")+s("F1","고유ID")+"</row>";}
    private static byte[] fixture(String sheet,boolean date1904,String shared) throws Exception {
        Map<String,String> parts=new LinkedHashMap<>();
        parts.put("xl/workbook.xml","<workbook xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><workbookPr date1904=\""+(date1904?"1":"0")+"\"/><sheets><sheet name=\"내역\" r:id=\"r1\"/></sheets></workbook>");
        parts.put("xl/_rels/workbook.xml.rels","<Relationships><Relationship Id=\"r1\" Target=\"worksheets/actual.xml\"/></Relationships>");
        parts.put("xl/worksheets/actual.xml","<worksheet><sheetData>"+sheet+"</sheetData></worksheet>");
        if(shared!=null)parts.put("xl/sharedStrings.xml",shared);
        return zip(parts);
    }
    private static byte[] zip(Map<String,String> parts)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){for(Map.Entry<String,String> p:parts.entrySet()){z.putNextEntry(new ZipEntry(p.getKey()));z.write(p.getValue().getBytes(StandardCharsets.UTF_8));z.closeEntry();}}return out.toByteArray();}
    private static XlsxIO.ReadResult read(byte[] bytes)throws Exception{return XlsxIO.read(new ByteArrayInputStream(bytes));}
    public static void main(String[] args)throws Exception {
        eq("2024-02-29",Ledger.normalizeDate("2024/2/29")); eq("2026-10-27",Ledger.normalizeDate("20261027"));
        eq("2026-10-27",Ledger.normalizeDate("2026-10-27T00:00:00.000Z"));
        rejects(()->Ledger.normalizeDate("2025-02-29"));rejects(()->Ledger.normalizeDate("2026-04-31"));rejects(()->Ledger.normalizeDate("1899-01-01"));rejects(()->Ledger.normalizeDate(""));
        eq(1234L,Ledger.parseAmount("1,234원"));eq(1000L,Ledger.parseAmount("1E3"));eq(999999999999L,Ledger.parseAmount("999999999999"));
        for(String amount:new String[]{"0","-1","1.5","1000000000000","NaN","","9223372036854775808"})rejects(()->Ledger.parseAmount(amount));
        eq("수입",Ledger.normalizeType("INCOME"));eq("지출",Ledger.normalizeType("expense"));eq("저축",Ledger.normalizeType("savings"));rejects(()->Ledger.normalizeType("이체"));
        List<Ledger.Entry> data=Arrays.asList(entry("i","2026-10-01","수입",3000000),entry("e","2026-10-02","지출",500000),entry("s","2026-10-03","저축",1000000));
        long[] totals=Ledger.totals(data);eq(3000000L,totals[0]);eq(500000L,totals[1]);eq(1000000L,totals[2]);eq(1500000L,Ledger.available(totals));eq(-8L,Ledger.available(new long[]{2,4,6}));
        eq(3,Ledger.month(data,YearMonth.of(2026,10)).size());eq(0,Ledger.month(data,YearMonth.of(2026,11)).size());
        Ledger.ImportPlan plan=Ledger.planImport(data,data);eq(3,plan.duplicates);eq(0,plan.additions.size());
        plan=Ledger.planImport(Collections.singletonList(entry("i","2026-10-01","수입",100)),data);eq(1,plan.errors.size());eq(0,plan.additions.size());
        plan=Ledger.planImport(Arrays.asList(data.get(0).withId(""),data.get(0).withId("")),Collections.emptyList());eq(1,plan.additions.size());eq(1,plan.duplicates);
        plan=Ledger.planImport(Collections.singletonList(data.get(0).withId("different-id")),data);eq(1,plan.additions.size());
        plan=Ledger.planImport(Arrays.asList(data.get(0),entry("i","2026-10-01","수입",1)),Collections.emptyList());eq(1,plan.errors.size());
        XlsxIO.ReadResult result=roundtrip(data);eq(3,result.entries.size());eq(0,result.invalidRows);
        for(int i=0;i<3;i++){eq(data.get(i).id,result.entries.get(i).id);eq(data.get(i).fingerprint(),result.entries.get(i).fingerprint());}
        Ledger.Entry special=new Ledger.Entry("special","2024-02-29","저축",Ledger.MAX_AMOUNT,"여행 & 쇼핑 <적금>","=SUM(A1:A9)\n따옴표 \"와\" 한글 🐱",2);
        result=roundtrip(Collections.singletonList(special));eq(special.fingerprint(),result.entries.get(0).fingerprint());
        for(String date:new String[]{"1900-01-01","1900-02-28","1900-03-01","9999-12-31"})eq(date,roundtrip(Collections.singletonList(entry("x",date,"수입",1))).entries.get(0).date);
        ByteArrayOutputStream template=new ByteArrayOutputStream();XlsxIO.write(template,Collections.emptyList(),true);eq(0,read(template.toByteArray()).entries.size());
        String numeric="<row r=\"2\"><c r=\"A2\"><v>46200</v></c>"+s("B2","저축")+"<c r=\"C2\"><v>100000</v></c></row>";
        result=read(fixture(headers()+numeric,false,null));eq(1,result.entries.size());eq("2026-06-27",result.entries.get(0).date);eq("기타",result.entries.get(0).category);
        result=read(fixture(headers()+numeric,true,null));eq(LocalDate.of(1904,1,1).plusDays(46200).toString(),result.entries.get(0).date);
        result=read(fixture(headers()+numeric.replace("46200","60"),false,null));eq(1,result.invalidRows);
        result=read(fixture(headers()+numeric.replace("46200","46200.75"),false,null));eq("2026-06-27",result.entries.get(0).date);
        String formula=numeric.replace("<v>100000</v>","<f>10*10000</f><v>100000</v>");result=read(fixture(headers()+formula,false,null));eq(1,result.invalidRows);eq(0,result.entries.size());
        result=read(fixture(headers()+numeric.replace("100000","-100"),false,null));eq(1,result.invalidRows);
        String shared="<sst><si><t>2026-10-27</t></si><si><r><t>저</t></r><r><t>축</t></r></si></sst>";
        String sharedRow="<row r=\"2\"><c r=\"A2\" t=\"s\"><v>0</v></c><c r=\"B2\" t=\"s\"><v>1</v></c><c r=\"C2\"><v>1E5</v></c></row>";
        result=read(fixture(headers()+sharedRow,false,shared));eq("저축",result.entries.get(0).type);eq(100000L,result.entries.get(0).amount);
        rejects(()->read(fixture("<row r=\"1\">"+s("A1","잘못된 양식")+"</row>",false,null)));
        rejects(()->read("not xlsx".getBytes(StandardCharsets.UTF_8)));
        Map<String,String> malicious=new HashMap<>();malicious.put("xl/workbook.xml","<!DOCTYPE x [<!ENTITY bomb SYSTEM 'file:///should-never-read'>]><workbook>&bomb;</workbook>");rejects(()->read(zip(malicious)));
        String reordered="<row r=\"1\">"+s("A1","금액")+s("B1","구분")+s("C1","날짜")+"</row><row r=\"2\"><c r=\"A2\"><v>50</v></c>"+s("B2","지출")+s("C2","2026-10-27")+"</row>";
        result=read(fixture(reordered,false,null));eq(50L,result.entries.get(0).amount);
        List<Ledger.Entry> many=new ArrayList<>();String memo=String.join("",Collections.nCopies(500,"가"));
        for(int i=0;i<10000;i++)many.add(new Ledger.Entry("id-"+i,"2026-10-27","지출",1234,"식비",memo,i+2));
        result=roundtrip(many);eq(10000,result.entries.size());eq(0,result.invalidRows);eq(memo,result.entries.get(9999).memo);
        many.add(entry("extra","2026-10-27","수입",1));rejects(()->roundtrip(many));
        if(args.length>0){result=XlsxIO.read(Files.newInputStream(Paths.get(args[0])));eq(0,result.invalidRows);eq(3,result.entries.size());eq(3000000L,Ledger.totals(result.entries)[0]);eq(1000000L,Ledger.totals(result.entries)[2]);}
        System.out.println("PASS: "+checks+" checks (including 10,000-row Korean XLSX roundtrip)");
    }
}
