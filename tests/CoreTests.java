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
    private static void historyChecks()throws Exception{
        List<Ledger.Entry> entries=Arrays.asList(new Ledger.Entry("a","2025-12-31","수입",1000,"급여","급여 입금",0),new Ledger.Entry("b","2026-01-01","지출",100,"식비","점심 커피",0),new Ledger.Entry("c","2026-01-01","저축",200,"적금","매월 저축",0),new Ledger.Entry("d","2026-02-01","지출",50,"교통","BUS 교통비",0),new Ledger.Entry("e","2026-02-02","지출",70,"식비","",0));
        HistoryData.Result data=HistoryData.build(entries,null,"","",Collections.emptySet(),0);eq(5,data.entries.size());eq(580L,Ledger.available(data.totals));eq(3,data.months.size());eq(YearMonth.of(2026,2),data.months.get(0).month);eq(-120L,Ledger.available(data.months.get(0).totals));eq(-300L,Ledger.available(data.months.get(1).days.get(0).totals));eq("e",data.entries.get(0).id);
        data=HistoryData.build(entries,YearMonth.of(2026,1),"","",Collections.emptySet(),0);eq(2,data.entries.size());eq(-300L,Ledger.available(data.totals));
        data=HistoryData.build(entries,null,"지출","",Collections.emptySet(),0);eq(3,data.entries.size());eq(-220L,Ledger.available(data.totals));
        data=HistoryData.build(entries,null,"","  bus ",Collections.emptySet(),0);eq(1,data.entries.size());eq("d",data.entries.get(0).id);
        eq(0,HistoryData.build(entries,null,"","식비",Collections.emptySet(),0).entries.size());eq(0,HistoryData.build(entries,null,"","없는 메모",Collections.emptySet(),0).entries.size());
        data=HistoryData.build(entries,null,"","",new HashSet<>(Arrays.asList("식비","적금")),1);eq(3,data.entries.size());eq(-370L,Ledger.available(data.totals));eq("식비",data.months.get(0).category);eq("적금",data.months.get(2).category);eq("e",data.entries.get(0).id);
        eq(1,HistoryData.build(entries,YearMonth.of(2026,1),"지출","커피",Collections.singleton("식비"),1).entries.size());eq(0,HistoryData.build(entries,YearMonth.of(2026,2),"지출","커피",Collections.singleton("식비"),1).entries.size());eq(0,HistoryData.build(Collections.emptyList(),null,"","",Collections.emptySet(),0).months.size());
        AmountKeypad pad=new AmountKeypad("");eq("0",pad.digits());eq(true,pad.press("00"));eq("0",pad.digits());pad.press("1");pad.press("2");pad.press("00");eq(1200L,pad.value());pad.press("⌫");eq("120",pad.digits());pad.clear();eq("0",pad.digits());eq(false,pad.press("."));pad=new AmountKeypad("999999999999");eq(false,pad.press("0"));eq(999999999999L,pad.value());eq("42",new AmountKeypad("00042").digits());eq("0",new AmountKeypad("wrong").digits());
        List<Ledger.Entry> many=new ArrayList<>();for(int i=0;i<105;i++)many.add(new Ledger.Entry("many"+i,"2026-01-01","지출",1,"식비","",0));data=HistoryData.build(many,null,"","",Collections.emptySet(),0);eq(105,data.months.get(0).count);eq(-105L,Ledger.available(data.months.get(0).days.get(0).totals));
    }
    private static void periodChecks()throws Exception{
        LocalDate today=LocalDate.of(2026,10,29);YearMonth month=YearMonth.of(2024,2);
        StatisticsPeriod feb=StatisticsPeriod.of(0,month,2026,today,today,today);eq(LocalDate.of(2024,2,1),feb.from);eq(LocalDate.of(2024,2,29),feb.to);eq(true,feb.contains(LocalDate.of(2024,2,29)));eq(false,feb.contains(LocalDate.of(2024,3,1)));
        StatisticsPeriod year=StatisticsPeriod.of(1,month,2025,today,today,today);eq(LocalDate.of(2025,1,1),year.from);eq(LocalDate.of(2025,12,31),year.to);
        StatisticsPeriod ytd=StatisticsPeriod.of(2,month,2025,today,today,today);eq(LocalDate.of(2026,1,1),ytd.from);eq(today,ytd.to);eq(false,ytd.contains(today.plusDays(1)));eq(false,ytd.contains(LocalDate.of(2025,12,31)));
        LocalDate start=LocalDate.of(2025,12,31),end=LocalDate.of(2026,1,1);StatisticsPeriod custom=StatisticsPeriod.of(3,month,2025,start,end,today);eq(true,custom.contains(start));eq(true,custom.contains(end));eq(false,custom.contains(end.plusDays(1)));
        List<Ledger.Entry> entries=Arrays.asList(entry("a",start.minusDays(1).toString(),"지출",1),entry("b",start.toString(),"지출",2),entry("c",end.toString(),"저축",3),entry("d",end.plusDays(1).toString(),"수입",4));eq(2,custom.entries(entries).size());eq(-5L,Ledger.available(Ledger.totals(custom.entries(entries))));
        eq(1,StatisticsPeriod.of(3,month,2026,end,end,today).entries(entries).size());eq(0,custom.entries(Collections.emptyList()).size());rejects(()->StatisticsPeriod.of(3,month,2026,end,start,today));rejects(()->StatisticsPeriod.of(4,month,2026,start,end,today));rejects(()->StatisticsPeriod.of(1,month,1899,start,end,today));
        eq(LocalDate.of(9999,12,31),StatisticsPeriod.of(1,month,9999,start,end,today).to);eq(LocalDate.of(1900,1,1),StatisticsPeriod.of(1,month,1900,start,end,today).from);
    }
    private static void overviewChecks()throws Exception{
        List<Ledger.Entry> entries=Arrays.asList(entry("a","2025-12-31","수입",1000),entry("b","2026-01-01","저축",100),entry("c","2026-01-02","지출",50),entry("d","2026-01-04","수입",500),entry("e","2026-01-05","지출",20),entry("f","2026-02-01","지출",30));
        OverviewData.Settlement all=OverviewData.settle(entries,null,null);eq(3,all.months.size());eq(YearMonth.of(2026,2),all.months.get(0).month);eq(1300L,all.balance());
        OverviewData.Settlement jan=OverviewData.settle(entries,YearMonth.of(2026,1),YearMonth.of(2026,1));eq(1,jan.months.size());eq(330L,jan.balance());eq(100L,jan.totals[2]);eq(-30L,OverviewData.settle(entries,YearMonth.of(2026,2),YearMonth.of(2026,12)).balance());rejects(()->OverviewData.settle(entries,YearMonth.of(2027,1),YearMonth.of(2026,12)));
        Map<String,Long> metrics=new HashMap<>();for(OverviewData.Metric m:OverviewData.metrics(entries,LocalDate.of(2026,1,4)))metrics.put(m.id,m.amount);eq(20,metrics.size());eq(500L,metrics.get("today_3"));eq(1350L,metrics.get("week_3"));eq(330L,metrics.get("month_3"));eq(300L,metrics.get("year_3"));eq(1300L,metrics.get("all_3"));metrics.clear();for(OverviewData.Metric m:OverviewData.metrics(entries,LocalDate.of(2026,1,5)))metrics.put(m.id,m.amount);eq(-20L,metrics.get("week_3"));eq(0,OverviewData.settle(Collections.emptyList(),null,null).months.size());
    }
    private static void near(float expected,float actual){checks++;if(Math.abs(expected-actual)>0.001f)throw new AssertionError("expected="+expected+", actual="+actual);}
    private static void iconGeometryChecks(){
        // 0.6.1의 통계(41x36)·기타 화면(36x42) 칸에서도 가로·세로 같은 비율로 가운데 그린다.
        for(int[] box:new int[][]{{41,36,5,5,5,5},{36,42,5,5,5,5},{48,48,12,12,12,12},{23,23,0,0,0,0},{126,135,34,34,34,34}}){
            float[] f=IconGeometry.fit(box[0],box[1],box[2],box[3],box[4],box[5]);
            float w=box[0]-box[2]-box[4],h=box[1]-box[3]-box[5],side=f[0]*IconGeometry.GRID;
            near(Math.min(w,h),side);near(box[2]+w/2f,f[1]+side/2f);near(box[3]+h/2f,f[2]+side/2f);
        }
        float[] header=IconGeometry.fit(48,48,12,12,12,12);near(1f,header[0]);near(12f,header[1]);near(12f,header[2]);
        float[] empty=IconGeometry.fit(10,10,6,6,6,6);near(0f,empty[0]);
        // 톱니바퀴: 8개 톱니, 24 격자 안(선 굵기 포함), 좌우·상하 대칭, 위쪽 가운데 톱니.
        float[] o=IconGeometry.gearOutline();eq(0,o.length%2);
        float minX=99,maxX=-99,minY=99,maxY=-99;int outer=0;boolean ring=true;
        for(int i=0;i<o.length;i+=2){double r=Math.hypot(o[i]-12,o[i+1]-12);ring&=r>=IconGeometry.GEAR_ROOT-0.001&&r<=IconGeometry.GEAR_OUTER+0.001;if(Math.abs(r-IconGeometry.GEAR_OUTER)<0.001)outer++;minX=Math.min(minX,o[i]);maxX=Math.max(maxX,o[i]);minY=Math.min(minY,o[i+1]);maxY=Math.max(maxY,o[i+1]);}
        eq(true,ring);eq(IconGeometry.GEAR_TEETH*5,outer);eq(true,minX-0.9f>=0&&minY-0.9f>=0&&maxX+0.9f<=24&&maxY+0.9f<=24);
        near(12-maxX,minX-12);near(12-maxY,minY-12);near(12-IconGeometry.GEAR_OUTER,minY);
        eq(true,IconGeometry.GEAR_HOLE>1.5f&&IconGeometry.GEAR_HOLE+0.9f<IconGeometry.GEAR_ROOT-0.9f);
    }
    public static void main(String[] args)throws Exception {
        overviewChecks();
        periodChecks();
        iconGeometryChecks();
        historyChecks();
        String hash=PinCrypto.create("0842");eq(true,PinCrypto.verify("0842",hash));eq(false,PinCrypto.verify("842",hash));eq(false,PinCrypto.verify("9999",hash));eq(false,PinCrypto.verify("0842","bad"));eq(false,hash.equals(PinCrypto.create("0842")));rejects(()->PinCrypto.create("12"));rejects(()->PinCrypto.create("abcd"));
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
        StatisticsData.Summary stats=StatisticsData.aggregate(data,"지출");eq(500000L,stats.total);eq(1,stats.categories.size());eq(100.0,stats.percent(500000));
        stats=StatisticsData.aggregate(Collections.emptyList(),"저축");eq(0L,stats.total);eq(0.0,stats.percent(0));eq(0,stats.chartCategories.size());
        List<Ledger.Entry> statisticsInput=new ArrayList<>();
        statisticsInput.add(new Ledger.Entry("a","2026-10-01","지출",500,"식비","",0));
        statisticsInput.add(new Ledger.Entry("b","2026-10-01","지출",500,"식비","",0));
        statisticsInput.add(new Ledger.Entry("c","2026-10-01","수입",9000,"월급","",0));
        statisticsInput.add(new Ledger.Entry("d","2026-10-01","지출",1,"작은 분류","",0));
        stats=StatisticsData.aggregate(statisticsInput,"지출");eq(1001L,stats.total);eq(2,stats.categories.size());eq("식비",stats.categories.get(0).name);eq(true,stats.hasTinyCategory);
        for(int i=0;i<14;i++)statisticsInput.add(new Ledger.Entry("group"+i,"2026-10-01","지출",i+1,"분류"+i,"",0));
        stats=StatisticsData.aggregate(statisticsInput,"지출");eq(16,stats.categories.size());eq(10,stats.chartCategories.size());eq(7,stats.groupedCount);
        long chartTotal=0;for(StatisticsData.Category category:stats.chartCategories)chartTotal+=category.amount;eq(stats.total,chartTotal);
        eq(9000L,StatisticsData.aggregate(statisticsInput,"수입").total);
        System.out.println("PASS: "+checks+" checks (including 10,000-row Korean XLSX roundtrip and category statistics)");
    }
}
