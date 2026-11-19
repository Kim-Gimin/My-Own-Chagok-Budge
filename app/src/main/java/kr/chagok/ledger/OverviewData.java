package kr.chagok.ledger;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Shared, whole-KRW calculations for settlements and configurable home totals. */
final class OverviewData {
    static final String[] PERIOD_IDS={"today","week","month","year","all"};
    static final String[] PERIOD_NAMES={"오늘","이번 주","이번 달","올해","전체"};
    // Preserve *_3 as balance in existing home preferences; append investment as *_4.
    static final String[] AMOUNT_NAMES={"수입","지출","저축","합계","투자"};
    static final Set<String> DEFAULT_METRICS=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("today_1","month_1","month_3","all_3")));
    static final class Metric {
        final String id,label;final long amount;
        Metric(String id,String label,long amount){this.id=id;this.label=label;this.amount=amount;}
    }
    static final class MonthTotal {
        final YearMonth month;final long[] totals;
        MonthTotal(YearMonth month,long[] totals){this.month=month;this.totals=totals.clone();}
        long balance(){return Ledger.available(totals);}
    }
    static final class Settlement {
        final List<MonthTotal> months;final long[] totals;
        Settlement(List<MonthTotal> months,long[] totals){this.months=Collections.unmodifiableList(months);this.totals=totals.clone();}
        long balance(){return Ledger.available(totals);}
    }
    static Settlement settle(List<Ledger.Entry> entries,YearMonth from,YearMonth to){
        if(from!=null && to!=null && from.isAfter(to))throw new IllegalArgumentException("시작 월은 끝 월보다 늦을 수 없어요.");
        TreeMap<YearMonth,long[]> groups=new TreeMap<>(Collections.reverseOrder());long[] total=new long[Ledger.TYPES.length];
        for(Ledger.Entry e:entries){YearMonth m=YearMonth.from(LocalDate.parse(e.date));if((from!=null && m.isBefore(from)) || (to!=null && m.isAfter(to)))continue;
            long[] values=groups.get(m);if(values==null){values=new long[Ledger.TYPES.length];groups.put(m,values);}int i=Ledger.index(e.type);values[i]=Math.addExact(values[i],e.amount);total[i]=Math.addExact(total[i],e.amount);
        }
        List<MonthTotal> months=new ArrayList<>();for(Map.Entry<YearMonth,long[]> item:groups.entrySet())months.add(new MonthTotal(item.getKey(),item.getValue()));
        return new Settlement(months,total);
    }
    static List<Metric> metrics(List<Ledger.Entry> entries,LocalDate today){
        long[][] totals=new long[5][Ledger.TYPES.length];LocalDate monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));LocalDate sunday=monday.plusDays(6);
        for(Ledger.Entry e:entries){LocalDate date=LocalDate.parse(e.date);int type=Ledger.index(e.type);
            boolean[] include={date.equals(today),!date.isBefore(monday)&&!date.isAfter(sunday),YearMonth.from(date).equals(YearMonth.from(today)),date.getYear()==today.getYear(),true};
            for(int p=0;p<5;p++)if(include[p])totals[p][type]=Math.addExact(totals[p][type],e.amount);
        }
        List<Metric> result=new ArrayList<>();for(int p=0;p<5;p++)for(int t=0;t<AMOUNT_NAMES.length;t++)result.add(new Metric(PERIOD_IDS[p]+"_"+t,PERIOD_NAMES[p]+" "+AMOUNT_NAMES[t],t==3?Ledger.available(totals[p]):totals[p][t==4?3:t]));return result;
    }
    private OverviewData(){}
}
