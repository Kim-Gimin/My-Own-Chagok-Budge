package kr.chagok.ledger;

import java.io.*;
import java.time.*;
import java.util.*;

/** Planning is separate from actual entries; no scheduled amount enters totals before confirmation. */
final class PlanningData {
    static final int MAX_ITEMS=1000;
    final List<Limit> limits=new ArrayList<>();
    final List<Repeat> repeats=new ArrayList<>();
    final List<Favorite> favorites=new ArrayList<>();
    static String title(String value){String s=value==null?"":value.trim();if(s.isEmpty()||s.length()>40)throw new IllegalArgumentException("이름은 1~40자로 입력하세요.");return s;}
    static String id(String value){if(value==null||!value.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("설정 ID를 확인하세요.");return value;}
    static final class Limit {
        final String month,type,category;final long amount;
        Limit(String month,String type,String category,long amount){
            if(!"*".equals(month)){YearMonth ym=YearMonth.parse(month);Ledger.normalizeDate(ym.atDay(1).toString());if(!ym.toString().equals(month))throw new IllegalArgumentException("월을 확인하세요.");}
            this.month=month;this.type=Ledger.normalizeType(type);if("수입".equals(this.type))throw new IllegalArgumentException("수입은 예산·납입 목표 대상이 아니에요.");
            this.category=category==null?"":category.trim();if(this.category.length()>40)throw new IllegalArgumentException("상세 분류는 40자까지 입력하세요.");this.amount=Ledger.parseAmount(String.valueOf(amount));
        }
        String key(){return month+":"+type+":"+category;}
    }
    static final class Favorite {
        final String id,name,type,category,memo;final long amount;
        Favorite(String id,String name,String type,long amount,String category,String memo){
            this.id=PlanningData.id(id);this.name=title(name);Ledger.Entry e=new Ledger.Entry("","2000-01-01",type,amount,category,memo,0);this.type=e.type;this.amount=e.amount;this.category=e.category;this.memo=e.memo;
        }
        Ledger.Entry entry(LocalDate date,String entryId){return new Ledger.Entry(entryId,date.toString(),type,amount,category,memo,0);}
    }
    static final class Repeat {
        final String id,name,type,category,memo,cadence;final long amount;final LocalDate next;final int anchorDay,anchorMonth;final boolean enabled;
        Repeat(String id,String name,String type,long amount,String category,String memo,String cadence,LocalDate next,int day,int month,boolean enabled){
            this.id=PlanningData.id(id);this.name=title(name);Ledger.Entry e=new Ledger.Entry("",next.toString(),type,amount,category,memo,0);this.type=e.type;this.amount=e.amount;this.category=e.category;this.memo=e.memo;
            if(!Arrays.asList("MONTHLY","WEEKLY","YEARLY").contains(cadence)||day<1||day>31||month<1||month>12)throw new IllegalArgumentException("반복 주기를 확인하세요.");
            this.cadence=cadence;this.next=next;anchorDay=day;anchorMonth=month;this.enabled=enabled;
        }
        String entryId(){return "repeat:"+id+":"+next;}
        boolean due(LocalDate today){return enabled&&!next.isAfter(today);}
        Repeat advance(){
            LocalDate date;
            try{if("WEEKLY".equals(cadence))date=next.plusWeeks(1);else if("YEARLY".equals(cadence)){YearMonth ym=YearMonth.of(next.getYear()+1,anchorMonth);date=ym.atDay(Math.min(anchorDay,ym.lengthOfMonth()));}else{YearMonth ym=YearMonth.from(next).plusMonths(1);date=ym.atDay(Math.min(anchorDay,ym.lengthOfMonth()));}if(date.getYear()>9999)throw new DateTimeException("end");}
            catch(DateTimeException ex){return with(next,false);}
            return with(date,enabled);
        }
        Repeat with(LocalDate date,boolean enabled){return new Repeat(id,name,type,amount,category,memo,cadence,date,anchorDay,anchorMonth,enabled);}
    }
    void put(Limit limit){limits.removeIf(v->v.key().equals(limit.key()));if(limits.size()>=MAX_ITEMS)throw new IllegalArgumentException("예산·목표는 최대 1,000개예요.");limits.add(limit);}
    void put(Favorite favorite){favorites.removeIf(v->v.id.equals(favorite.id));if(favorites.size()>=MAX_ITEMS)throw new IllegalArgumentException("자주 쓰는 기록은 최대 1,000개예요.");favorites.add(favorite);}
    void put(Repeat repeat){repeats.removeIf(v->v.id.equals(repeat.id));if(repeats.size()>=MAX_ITEMS)throw new IllegalArgumentException("반복 기록은 최대 1,000개예요.");repeats.add(repeat);}
    Repeat repeat(String id){for(Repeat r:repeats)if(r.id.equals(id))return r;throw new IllegalArgumentException("반복 기록이 변경됐어요. 다시 열어 주세요.");}
    List<Limit> effective(YearMonth month,String type){
        Map<String,Limit> result=new TreeMap<>();for(Limit l:limits)if(l.type.equals(type)&&l.month.equals("*"))result.put(l.category,l);
        for(Limit l:limits)if(l.type.equals(type)&&l.month.equals(month.toString()))result.put(l.category,l);return new ArrayList<>(result.values());
    }
    static final class Progress {
        final long target,actual,remaining;final double percent;
        Progress(long target,long actual){this.target=target;this.actual=actual;remaining=Math.subtractExact(target,actual);percent=actual*100.0/target;}
    }
    static Progress progress(Limit limit,YearMonth month,List<Ledger.Entry> entries){
        long actual=0;for(Ledger.Entry e:entries)if(e.date.startsWith(month.toString())&&e.type.equals(limit.type)&&(limit.category.isEmpty()||e.category.equals(limit.category)))actual=Math.addExact(actual,e.amount);
        return new Progress(limit.amount,actual);
    }
    static final class MonthTotal {
        final YearMonth month;final long[] totals;
        MonthTotal(YearMonth month,long[] totals){this.month=month;this.totals=totals;}
    }
    static List<MonthTotal> trend(List<Ledger.Entry> entries,YearMonth end,int count){
        if(count!=6&&count!=12)throw new IllegalArgumentException("6개월 또는 12개월을 선택하세요.");
        List<MonthTotal> result=new ArrayList<>();YearMonth from=end.minusMonths(count-1);if(from.getYear()<1900)from=YearMonth.of(1900,1);
        Map<String,long[]> map=new HashMap<>();for(Ledger.Entry e:entries){YearMonth m=YearMonth.from(LocalDate.parse(e.date));if(!m.isBefore(from)&&!m.isAfter(end)){long[] totals=map.computeIfAbsent(m.toString(),k->new long[4]);int kind=Ledger.index(e.type);totals[kind]=Math.addExact(totals[kind],e.effect());}}
        for(YearMonth m=from;!m.isAfter(end);m=m.plusMonths(1))result.add(new MonthTotal(m,map.getOrDefault(m.toString(),new long[4])));return result;
    }
    static final class Change {
        final long current,previous,difference;final Double percent;
        Change(long current,long previous){this.current=current;this.previous=previous;difference=Math.subtractExact(current,previous);percent=previous==0?null:difference*100.0/previous;}
    }
    static Change change(List<Ledger.Entry> entries,YearMonth month,String type,String category){
        long current=0,previous=0;String before=month.minusMonths(1).toString();for(Ledger.Entry e:entries)if(Ledger.matches(e,type)&&(category==null||category.equals(e.category))){if(e.date.startsWith(month.toString()))current=Math.addExact(current,e.effect());else if(e.date.startsWith(before))previous=Math.addExact(previous,e.effect());}return new Change(current,previous);
    }
    static Map<String,Change> categoryChanges(List<Ledger.Entry> entries,YearMonth month,String type){
        Map<String,long[]> totals=new TreeMap<>();String current=month.toString(),previous=month.minusMonths(1).toString();for(Ledger.Entry e:entries)if(Ledger.matches(e,type)){int slot=e.date.startsWith(current)?0:e.date.startsWith(previous)?1:-1;if(slot>=0){long[] values=totals.computeIfAbsent(e.category,k->new long[2]);values[slot]=Math.addExact(values[slot],e.effect());}}
        Map<String,Change> result=new TreeMap<>();for(Map.Entry<String,long[]> item:totals.entrySet())result.put(item.getKey(),new Change(item.getValue()[0],item.getValue()[1]));return result;
    }
    /** Existing keys win, including the advanced cursor of a recurring transaction. */
    static PlanningData merge(PlanningData existing,PlanningData incoming){
        PlanningData out=decode(existing.encode());Set<String> keys=new HashSet<>();for(Limit l:out.limits)keys.add(l.key());for(Limit l:incoming.limits)if(keys.add(l.key()))out.put(l);
        keys.clear();for(Repeat r:out.repeats)keys.add(r.id);for(Repeat r:incoming.repeats)if(keys.add(r.id))out.put(r);
        keys.clear();for(Favorite f:out.favorites)keys.add(f.id);for(Favorite f:incoming.favorites)if(keys.add(f.id))out.put(f);return out;
    }
    String encode(){
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x43485031);out.writeInt(limits.size());
            for(Limit l:limits){out.writeUTF(l.month);out.writeUTF(l.type);out.writeUTF(l.category);out.writeLong(l.amount);}
            out.writeInt(repeats.size());for(Repeat r:repeats){out.writeUTF(r.id);out.writeUTF(r.name);out.writeUTF(r.type);out.writeLong(r.amount);out.writeUTF(r.category);out.writeUTF(r.memo);out.writeUTF(r.cadence);out.writeUTF(r.next.toString());out.writeInt(r.anchorDay);out.writeInt(r.anchorMonth);out.writeBoolean(r.enabled);}
            out.writeInt(favorites.size());for(Favorite f:favorites){out.writeUTF(f.id);out.writeUTF(f.name);out.writeUTF(f.type);out.writeLong(f.amount);out.writeUTF(f.category);out.writeUTF(f.memo);}out.flush();return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException ex){throw new IllegalStateException("계획을 저장하지 못했어요.",ex);}
    }
    static int count(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>MAX_ITEMS)throw new IOException("Too many settings");return n;}
    static PlanningData decode(String encoded){
        PlanningData p=new PlanningData();if(encoded==null||encoded.isEmpty())return p;
        if(encoded.length()>4_000_000)throw new IllegalArgumentException("계획 파일이 너무 커요.");
        try{DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));if(in.readInt()!=0x43485031)throw new IOException("Unsupported planning version");
            Set<String> keys=new HashSet<>();int n=count(in);for(int i=0;i<n;i++){Limit l=new Limit(in.readUTF(),in.readUTF(),in.readUTF(),in.readLong());if(!keys.add(l.key()))throw new IOException("Duplicate limit");p.put(l);}
            keys.clear();n=count(in);for(int i=0;i<n;i++){Repeat r=new Repeat(in.readUTF(),in.readUTF(),in.readUTF(),in.readLong(),in.readUTF(),in.readUTF(),in.readUTF(),LocalDate.parse(in.readUTF()),in.readInt(),in.readInt(),in.readBoolean());if(!keys.add(r.id))throw new IOException("Duplicate repeat");p.put(r);}
            keys.clear();n=count(in);for(int i=0;i<n;i++){Favorite f=new Favorite(in.readUTF(),in.readUTF(),in.readUTF(),in.readLong(),in.readUTF(),in.readUTF());if(!keys.add(f.id))throw new IOException("Duplicate favorite");p.put(f);}if(in.read()!=-1)throw new IOException("Unexpected trailing data");return p;
        }catch(Exception ex){throw new IllegalArgumentException("계획 파일이 손상됐거나 지원하지 않는 형식이에요.",ex);}
    }
}
