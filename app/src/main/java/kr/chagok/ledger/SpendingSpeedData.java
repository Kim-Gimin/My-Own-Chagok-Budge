package kr.chagok.ledger;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Cumulative expense pace for the selected month and the comparable days of the previous month. */
final class SpendingSpeedData {
    static final class Result {
        final YearMonth month, previousMonth;
        final boolean currentMonth;
        final int daysShown;
        final long currentTotal, previousComparableTotal, previousTotal, budgetTarget;
        final List<Long> current, previous;

        Result(YearMonth month, YearMonth previousMonth, boolean currentMonth, int daysShown,
               long currentTotal, long previousComparableTotal, long previousTotal,
               long budgetTarget, List<Long> current, List<Long> previous) {
            this.month=month;this.previousMonth=previousMonth;this.currentMonth=currentMonth;
            this.daysShown=daysShown;this.currentTotal=currentTotal;
            this.previousComparableTotal=previousComparableTotal;this.previousTotal=previousTotal;
            this.budgetTarget=budgetTarget;this.current=current;this.previous=previous;
        }
    }
    private SpendingSpeedData() {}

    static Result build(List<Ledger.Entry> entries, YearMonth month, LocalDate today,
                        long budgetTarget, Set<String> livingCategories) {
        YearMonth previousMonth=month.minusMonths(1);
        boolean currentMonth=month.equals(YearMonth.from(today));
        int daysShown=currentMonth?today.getDayOfMonth():month.lengthOfMonth();
        daysShown=Math.max(1,Math.min(daysShown,month.lengthOfMonth()));
        long[] currentByDay=new long[daysShown],previousByDay=new long[daysShown];long previousTotal=0;
        for(Ledger.Entry entry:entries){
            if(!"지출".equals(entry.type))continue;
            if(livingCategories!=null&&!livingCategories.contains(entry.category))continue;
            LocalDate date;try{date=LocalDate.parse(entry.date);}catch(RuntimeException ignored){continue;}
            YearMonth entryMonth=YearMonth.from(date);int day=date.getDayOfMonth();
            if(entryMonth.equals(month)&&day<=daysShown)currentByDay[day-1]+=entry.amount;
            if(entryMonth.equals(previousMonth)){previousTotal+=entry.amount;if(day<=daysShown)previousByDay[day-1]+=entry.amount;}
        }
        List<Long> current=cumulative(currentByDay),previous=cumulative(previousByDay);
        return new Result(month,previousMonth,currentMonth,daysShown,current.get(current.size()-1),
                previous.get(previous.size()-1),previousTotal,Math.max(0,budgetTarget),current,previous);
    }

    private static List<Long> cumulative(long[] daily){List<Long> result=new ArrayList<>();long total=0;for(long amount:daily){total+=amount;result.add(total);}return result;}
}
