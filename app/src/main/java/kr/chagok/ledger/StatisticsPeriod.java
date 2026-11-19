package kr.chagok.ledger;

import java.time.*;
import java.util.*;

/** Inclusive dates shared by the chart, totals, savings rate and category detail. */
final class StatisticsPeriod {
    static final int MONTH=0,YEAR=1,YTD=2,CUSTOM=3;
    final int mode;
    final LocalDate from,to;
    private StatisticsPeriod(int mode,LocalDate from,LocalDate to){
        if(from==null||to==null||from.getYear()<1900||to.getYear()>9999||from.isAfter(to))
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없어요.");
        this.mode=mode;this.from=from;this.to=to;
    }
    static StatisticsPeriod of(int mode,YearMonth month,int year,LocalDate from,LocalDate to,LocalDate today){
        switch(mode){
            case MONTH:return new StatisticsPeriod(mode,month.atDay(1),month.atEndOfMonth());
            case YEAR:return new StatisticsPeriod(mode,LocalDate.of(year,1,1),LocalDate.of(year,12,31));
            case YTD:return new StatisticsPeriod(mode,today.withDayOfYear(1),today);
            case CUSTOM:return new StatisticsPeriod(mode,from,to);
            default:throw new IllegalArgumentException("통계 기간을 선택하세요.");
        }
    }
    boolean contains(LocalDate date){return !date.isBefore(from)&&!date.isAfter(to);}
    List<Ledger.Entry> entries(List<Ledger.Entry> all){
        List<Ledger.Entry> result=new ArrayList<>();
        for(Ledger.Entry entry:all)if(contains(LocalDate.parse(entry.date)))result.add(entry);
        return result;
    }
}
