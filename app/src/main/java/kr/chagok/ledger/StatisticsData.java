package kr.chagok.ledger;

import java.util.*;

/** One category aggregation feeds both the chart and the full amount list. */
final class StatisticsData {
    static final int MAX_CHART_CATEGORIES=10;
    static final class Category {
        final String name;
        final long amount;
        Category(String name,long amount){this.name=name;this.amount=amount;}
    }
    static final class Summary {
        final String type;
        final long total;
        final List<Category> categories,chartCategories;
        final int groupedCount;
        final boolean hasTinyCategory;
        Summary(String type,long total,List<Category> categories,List<Category> chart,int grouped,boolean tiny){
            this.type=type;this.total=total;this.categories=Collections.unmodifiableList(categories);
            chartCategories=Collections.unmodifiableList(chart);groupedCount=grouped;hasTinyCategory=tiny;
        }
        long chartTotal(){long value=0;for(Category c:chartCategories)value=Math.addExact(value,c.amount);return value;}
        double percent(long amount){long positive=0;for(Category c:categories)if(c.amount>0)positive+=c.amount;return positive==0||amount<=0?0:amount*100.0/positive;}
    }
    /** Both overview charts use these same positive totals from the selected period. */
    static final class Comparison {
        private final long[] amounts;
        final long total,maximum,outgoing,balance;
        final Summary chart;
        Comparison(long[] amounts) {
            this.amounts=amounts.clone();long sum=0,max=0;
            List<Category> all=new ArrayList<>(),visible=new ArrayList<>();
            for(int i=0;i<Ledger.TYPES.length;i++) {
                sum=Math.addExact(sum,Math.max(0,amounts[i]));max=Math.max(max,amounts[i]);
                Category value=new Category(Ledger.TYPES[i],amounts[i]);all.add(value);if(value.amount>0)visible.add(value);
            }
            total=sum;maximum=max;outgoing=Math.addExact(Math.addExact(amounts[1],amounts[2]),amounts[3]);balance=Ledger.available(amounts);
            chart=new Summary("전체",total,all,visible,0,false);
        }
        long amount(int type){return amounts[type];}
        double percent(int type){return total==0?0:Math.max(0,amounts[type])*100.0/total;}
        double barFraction(int type){return maximum==0?0:Math.max(0,amounts[type])/(double)maximum;}
        boolean hasIncome(){return amounts[0]>0;}
        double incomePercent(int type){
            if(!hasIncome())throw new IllegalStateException("수입이 없어 수입 대비 비율을 계산할 수 없어요.");
            return amounts[type]*100.0/amounts[0];
        }
    }
    static Comparison compare(List<Ledger.Entry> entries){return new Comparison(Ledger.totals(entries));}
    /** Money kept: saving (net of withdrawals) + investment. */
    static long saved(long[] totals){return Math.addExact(totals[2],totals[3]);}
    /** Percent of income: expense, saving, investment, saved (saving+investment), remaining. Null without income. */
    static double[] incomeShares(long[] totals){
        if(totals[0]<=0)return null;double income=totals[0];
        return new double[]{totals[1]*100.0/income,totals[2]*100.0/income,totals[3]*100.0/income,saved(totals)*100.0/income,Ledger.available(totals)*100.0/income};
    }
    static Summary aggregate(List<Ledger.Entry> entries,String type){
        String kind=Ledger.normalizeType(type);Map<String,Long> groups=new HashMap<>();long total=0;
        for(Ledger.Entry entry:entries)if(Ledger.matches(entry,kind)){
            total=Math.addExact(total,entry.effect());
            groups.put(entry.category,Math.addExact(groups.getOrDefault(entry.category,0L),entry.effect()));
        }
        List<Category> categories=new ArrayList<>();for(Map.Entry<String,Long> g:groups.entrySet())categories.add(new Category(g.getKey(),g.getValue()));
        categories.sort((a,b)->{int amount=Long.compare(b.amount,a.amount);return amount!=0?amount:a.name.compareTo(b.name);});
        boolean tiny=false;for(Category c:categories)if(c.amount*100.0/total<1)tiny=true;
        List<Category> chart=new ArrayList<>();int grouped=0;
        List<Category> positive=new ArrayList<>();for(Category c:categories)if(c.amount>0)positive.add(c);
        if(positive.size()<=MAX_CHART_CATEGORIES)chart.addAll(positive);
        else {
            chart.addAll(positive.subList(0,MAX_CHART_CATEGORIES-1));long rest=0;
            for(int i=MAX_CHART_CATEGORIES-1;i<positive.size();i++)rest=Math.addExact(rest,positive.get(i).amount);
            grouped=positive.size()-(MAX_CHART_CATEGORIES-1);chart.add(new Category("그 외 "+grouped+"개",rest));
        }
        return new Summary(kind,total,categories,chart,grouped,tiny);
    }
    private StatisticsData(){}
}
