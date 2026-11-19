package kr.chagok.ledger;

import java.text.Collator;
import java.time.*;
import java.util.*;

/** One filtered history supplies rows and complete day/month/result totals. */
final class HistoryData {
    static final int DATE=0,CATEGORY=1;
    static final class Day {
        final String date;final List<Ledger.Entry> entries=new ArrayList<>();final long[] totals=new long[Ledger.TYPES.length];
        Day(String date){this.date=date;}
    }
    static final class Month {
        final YearMonth month;final String category;final List<Day> days=new ArrayList<>();final long[] totals=new long[Ledger.TYPES.length];int count;
        Month(YearMonth month,String category){this.month=month;this.category=category;}
    }
    static final class Result {
        final List<Ledger.Entry> entries;final List<Month> months;final long[] totals;
        Result(List<Ledger.Entry> entries,List<Month> months,long[] totals){this.entries=Collections.unmodifiableList(entries);this.months=Collections.unmodifiableList(months);this.totals=totals;}
    }
    static Result build(List<Ledger.Entry> all,YearMonth month,String type,String memo,Set<String> categories,int sort){
        String query=memo.trim().toLowerCase(Locale.KOREA);List<Ledger.Entry> entries=new ArrayList<>();
        for(Ledger.Entry entry:all)if((month==null||entry.date.startsWith(month.toString()+"-"))&&(type.isEmpty()||entry.type.equals(type))&&(categories.isEmpty()||categories.contains(entry.category))&&(query.isEmpty()||entry.memo.toLowerCase(Locale.KOREA).contains(query)))entries.add(entry);
        Collator collator=Collator.getInstance(Locale.KOREA);
        entries.sort((a,b)->{if(sort==CATEGORY){int c=collator.compare(a.category,b.category);if(c==0)c=a.category.compareTo(b.category);if(c!=0)return c;}return b.date.compareTo(a.date);});
        List<Month> groups=new ArrayList<>();long[] total=new long[Ledger.TYPES.length];Month current=null;Day day=null;
        for(Ledger.Entry entry:entries){YearMonth yearMonth=YearMonth.from(LocalDate.parse(entry.date));String category=sort==CATEGORY?entry.category:"";
            if(current==null||!current.month.equals(yearMonth)||!current.category.equals(category)){current=new Month(yearMonth,category);groups.add(current);day=null;}
            if(day==null||!day.date.equals(entry.date)){day=new Day(entry.date);current.days.add(day);}
            day.entries.add(entry);current.count++;int i=Ledger.index(entry.type);day.totals[i]=Math.addExact(day.totals[i],entry.amount);current.totals[i]=Math.addExact(current.totals[i],entry.amount);total[i]=Math.addExact(total[i],entry.amount);
        }
        return new Result(entries,groups,total);
    }
    private HistoryData(){}
}
