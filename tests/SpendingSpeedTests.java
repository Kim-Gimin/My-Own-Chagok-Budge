package kr.chagok.ledger;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public final class SpendingSpeedTests {
    private static int checks;
    private static void eq(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError("expected="+expected+", actual="+actual);}
    private static Ledger.Entry entry(String id,String date,String type,long amount,String category){return new Ledger.Entry(id,date,type,amount,category,"가상 검사",0);}
    private static void amounts(SpendingSpeedData.Result r,long current,long previous,long previousFull,long budget){
        eq(current,r.currentTotal);eq(previous,r.previousComparableTotal);eq(previousFull,r.previousTotal);eq(budget,r.budgetTarget);
        eq(r.daysShown,r.current.size());eq(r.daysShown,r.previous.size());
        eq(current,r.current.get(r.current.size()-1));eq(previous,r.previous.get(r.previous.size()-1));
    }
    public static void main(String[] args)throws Exception{
        YearMonth month=YearMonth.of(2026,11);LocalDate today=LocalDate.of(2026,11,3);
        List<Ledger.Entry> entries=Arrays.asList(
                entry("c1","2026-11-01","지출",100,"식비"),entry("c2","2026-11-03","지출",50,"교통"),
                entry("c3","2026-11-02","지출",400,"공과금"),entry("future","2026-11-30","지출",999,"식비"),
                entry("p1","2026-10-01","지출",70,"식비"),entry("p2","2026-10-03","지출",30,"교통"),
                entry("p3","2026-10-02","지출",600,"공과금"),entry("p4","2026-10-31","지출",900,"식비"),
                entry("income","2026-11-01","수입",10000,"식비"),entry("saving","2026-11-01","저축",10000,"식비"),
                entry("investment","2026-11-01","투자",10000,"식비"),entry("recovery","2026-11-01","저축회수",10000,"식비"));
        PlanningData plan=new PlanningData();PlanningData.Limit overall=new PlanningData.Limit("*","지출","",1900);
        PlanningData.Limit food=new PlanningData.Limit("*","지출","식비",500),tax=new PlanningData.Limit("*","지출","공과금",800);
        plan.put(overall);plan.put(food);plan.put(tax);plan.selectLivingCategories(new HashSet<>(Arrays.asList("식비","교통")));
        String saved=plan.encode();
        SpendingSpeedData.Result budget=SpendingSpeedData.forBudget(entries,month,today,plan,overall);
        amounts(budget,150L,100L,1000L,1900L);eq(Arrays.asList(100L,100L,150L),budget.current);eq(Arrays.asList(70L,70L,100L),budget.previous);
        amounts(SpendingSpeedData.totalExpenses(entries,month,today),550L,700L,1600L,0L);
        amounts(SpendingSpeedData.forBudget(entries,month,today,plan,food),100L,70L,970L,500L);
        // Individual category budgets are independent of the living-category selection.
        amounts(SpendingSpeedData.forBudget(entries,month,today,plan,tax),400L,600L,600L,800L);
        eq(saved,plan.encode());eq(12,entries.size());eq("future",entries.get(3).id);eq(999L,entries.get(3).amount);
        plan.selectLivingCategories(Collections.emptySet());
        amounts(SpendingSpeedData.forBudget(entries,month,today,plan,overall),0L,0L,0L,1900L);
        amounts(SpendingSpeedData.totalExpenses(entries,month,today),550L,700L,1600L,0L);
        amounts(SpendingSpeedData.forBudget(entries,month,today,plan,food),100L,70L,970L,500L);
        PlanningData unfiltered=new PlanningData();
        amounts(SpendingSpeedData.forBudget(entries,month,today,unfiltered,overall),550L,700L,1600L,1900L);
        amounts(SpendingSpeedData.totalExpenses(Collections.emptyList(),month,today),0L,0L,0L,0L);
        amounts(SpendingSpeedData.forBudget(Collections.emptyList(),month,today,plan,tax),0L,0L,0L,800L);
        PlanningData.Limit specificMonth=new PlanningData.Limit("2026-11","지출","식비",700);plan.put(specificMonth);
        PlanningData.Limit effective=null;for(PlanningData.Limit limit:plan.effective(month,"지출"))if(limit.category.equals("식비"))effective=limit;
        eq(specificMonth,effective);eq(700L,SpendingSpeedData.forBudget(entries,month,today,plan,effective).budgetTarget);
        eq(500L,plan.effective(month.minusMonths(1),"지출").stream().filter(l->l.category.equals("식비")).findFirst().get().amount);
        checks++;try{SpendingSpeedData.forBudget(entries,month,today,plan,new PlanningData.Limit("*","저축","식비",1));throw new AssertionError("Saving goal accepted as expense budget");}catch(IllegalArgumentException expected){}
        // Month lengths and leap years must use equal day cutoffs in both charts.
        List<Ledger.Entry> leap=Arrays.asList(entry("l1","2024-01-31","지출",10,"식비"),entry("l2","2024-01-29","지출",20,"식비"),entry("l3","2024-02-28","지출",5,"식비"),entry("l4","2024-02-29","지출",30,"식비"));
        YearMonth feb=YearMonth.of(2024,2);SpendingSpeedData.Result current=SpendingSpeedData.totalExpenses(leap,feb,LocalDate.of(2024,2,28));
        amounts(current,5L,0L,30L,0L);eq(28,current.daysShown);eq(true,current.currentMonth);
        SpendingSpeedData.Result complete=SpendingSpeedData.totalExpenses(leap,feb,LocalDate.of(2024,3,1));
        amounts(complete,35L,20L,30L,0L);eq(29,complete.daysShown);eq(false,complete.currentMonth);
        amounts(SpendingSpeedData.forBudget(leap,feb,LocalDate.of(2024,3,1),unfiltered,food),35L,20L,30L,500L);
        List<Ledger.Entry> large=Collections.singletonList(entry("large","2026-11-01","지출",999999999999L,"식비"));
        amounts(SpendingSpeedData.totalExpenses(large,month,today),999999999999L,0L,0L,0L);
        System.out.println("PASS: "+checks+" budget/total expense chart checks");
    }
}
