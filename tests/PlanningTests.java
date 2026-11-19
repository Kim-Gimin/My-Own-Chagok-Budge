package kr.chagok.ledger;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;

public final class PlanningTests {
    static int checks;
    interface Check {void run()throws Exception;}
    static void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("Expected "+a+", got "+b);}
    static void rejects(Check c)throws Exception{checks++;try{c.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
    static Ledger.Entry e(String id,String date,String type,long amount,String cat){return new Ledger.Entry(id,date,type,amount,cat,"",0);}
    static PlanningData.Repeat repeat(String id,LocalDate date,String cadence){return new PlanningData.Repeat(id,"월급","수입",3000000,"월급","한글 메모",cadence,date,date.getDayOfMonth(),date.getMonthValue(),true);}
    public static void main(String[] args)throws Exception{
        List<Ledger.Entry> data=Arrays.asList(e("i","2026-11-01","수입",10000,"식비"),e("e","2026-11-02","지출",300,"식비"),e("e2","2026-11-03","지출",500,"교통"),e("s","2026-11-03","저축",2000,"적금"),e("v","2026-11-03","투자",1000,"ETF"),e("old","2026-10-03","지출",600,"식비"),e("next","2026-12-01","지출",900,"식비"));
        PlanningData plan=new PlanningData();plan.put(new PlanningData.Limit("*","지출","",1000));plan.put(new PlanningData.Limit("2026-11","지출","",700));plan.put(new PlanningData.Limit("*","지출","식비",400));plan.put(new PlanningData.Limit("2026-11","저축","",3000));plan.put(new PlanningData.Limit("2026-11","투자","ETF",2000));
        YearMonth nov=YearMonth.of(2026,11);List<PlanningData.Limit> limits=plan.effective(nov,"지출");eq(2,limits.size());PlanningData.Progress total=PlanningData.progress(limits.get(0),nov,data);eq(800L,total.actual);eq(-100L,total.remaining);eq(true,total.percent>100);eq(300L,PlanningData.progress(limits.get(1),nov,data).actual);eq(1000L,plan.effective(nov.plusMonths(1),"지출").get(0).amount);eq(2000L,PlanningData.progress(plan.effective(nov,"저축").get(0),nov,data).actual);eq(1000L,PlanningData.progress(plan.effective(nov,"투자").get(0),nov,data).actual);eq(7,data.size());
        rejects(()->new PlanningData.Limit("2026-13","지출","",1));rejects(()->new PlanningData.Limit("2026-11","수입","",1));rejects(()->new PlanningData.Limit("*","지출","",0));rejects(()->new PlanningData.Limit("*","투자","",Ledger.MAX_AMOUNT+1));
        PlanningData.Repeat jan=repeat("jan",LocalDate.of(2026,1,31),"MONTHLY");eq(LocalDate.of(2026,2,28),jan.advance().next);eq(LocalDate.of(2026,3,31),jan.advance().advance().next);eq(false,jan.due(LocalDate.of(2026,1,30)));eq(true,jan.due(LocalDate.of(2026,1,31)));eq(false,jan.with(jan.next,false).due(LocalDate.of(2026,12,31)));eq("repeat:jan:2026-01-31",jan.entryId());
        PlanningData.Repeat leap=repeat("leap",LocalDate.of(2024,2,29),"YEARLY");eq(LocalDate.of(2025,2,28),leap.advance().next);for(int i=0;i<4;i++)leap=leap.advance();eq(LocalDate.of(2028,2,29),leap.next);
        eq(LocalDate.of(2027,1,5),repeat("week",LocalDate.of(2026,12,29),"WEEKLY").advance().next);eq(false,repeat("end",LocalDate.of(9999,12,31),"MONTHLY").advance().enabled);
        for(int year:new int[]{1900,2000,2024,2026,2100,9998})for(int month=1;month<=12;month++){YearMonth ym=YearMonth.of(year,month);PlanningData.Repeat r=new PlanningData.Repeat("r","말일","지출",1,"기타","","MONTHLY",ym.atEndOfMonth(),31,month,true);YearMonth next=ym.plusMonths(1);eq(next.atEndOfMonth(),r.advance().next);}
        PlanningData.Favorite favorite=new PlanningData.Favorite("fav","점심 🥗","지출",12500,"식비", "한글\n:메모");plan.put(favorite);plan.put(jan);
        eq("2026-11-13",favorite.entry(LocalDate.of(2026,11,13),"new-id").date);eq("new-id",favorite.entry(LocalDate.of(2026,11,13),"new-id").id);eq(12500L,favorite.entry(LocalDate.of(2026,11,13),"new-id").amount);
        String encoded=plan.encode();eq(encoded,PlanningData.decode(encoded).encode());eq("한글\n:메모",PlanningData.decode(encoded).favorites.get(0).memo);eq(0,PlanningData.decode("").limits.size());rejects(()->PlanningData.decode("bad"));rejects(()->PlanningData.decode(encoded.substring(0,encoded.length()-8)));byte[] tail=Arrays.copyOf(Base64.getDecoder().decode(encoded),Base64.getDecoder().decode(encoded).length+1);rejects(()->PlanningData.decode(Base64.getEncoder().encodeToString(tail)));
        PlanningData old=new PlanningData();old.put(jan.advance());old.put(new PlanningData.Limit("2026-11","지출","",999));PlanningData merged=PlanningData.merge(old,plan);eq(LocalDate.of(2026,2,28),merged.repeat("jan").next);eq(999L,merged.effective(nov,"지출").get(0).amount);eq(1,merged.favorites.size());eq(1,old.limits.size());eq(0,old.favorites.size());eq(encoded,plan.encode());
        List<PlanningData.MonthTotal> months=PlanningData.trend(data,nov,6);eq(6,months.size());eq(YearMonth.of(2026,6),months.get(0).month);eq(0L,months.get(0).totals[1]);eq(600L,months.get(4).totals[1]);eq(800L,months.get(5).totals[1]);eq(1000L,months.get(5).totals[3]);eq(1,PlanningData.trend(data,YearMonth.of(1900,1),12).size());eq(12,PlanningData.trend(data,YearMonth.of(9999,12),12).size());rejects(()->PlanningData.trend(data,nov,5));
        PlanningData.Change change=PlanningData.change(data,nov,"지출",null);eq(800L,change.current);eq(600L,change.previous);eq(200L,change.difference);eq(true,Math.abs(change.percent-33.3333333)<.0001);eq(-300L,PlanningData.change(data,nov,"지출","식비").difference);eq(null,PlanningData.change(data,nov,"투자",null).percent);eq(null,PlanningData.change(data,YearMonth.of(2000,1),"수입",null).percent);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();BackupArchive.write(bytes,data,plan);BackupArchive.Snapshot restored=BackupArchive.read(new ByteArrayInputStream(bytes.toByteArray()));eq(data.size(),restored.entries.size());eq(encoded,restored.planning.encode());for(Ledger.Entry original:data){Ledger.Entry found=restored.entries.stream().filter(row->row.id.equals(original.id)).findFirst().get();eq(original.fingerprint(),found.fingerprint());}rejects(()->BackupArchive.read(new ByteArrayInputStream(new byte[0])));
        ByteArrayOutputStream bad=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bad)){BackupArchive.part(zip,"../outside",new byte[]{1});}rejects(()->BackupArchive.read(new ByteArrayInputStream(bad.toByteArray())));
        byte[] oversized=new byte[BackupArchive.LIMIT+1];ByteArrayOutputStream bomb=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bomb)){BackupArchive.part(zip,"계획.dat",oversized);}rejects(()->BackupArchive.read(new ByteArrayInputStream(bomb.toByteArray())));
        System.out.println("PASS: "+checks+" planning/backup checks");
    }
}
