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
        double percent(long amount){return total==0?0:amount*100.0/total;}
    }
    static Summary aggregate(List<Ledger.Entry> entries,String type){
        String kind=Ledger.normalizeType(type);Map<String,Long> groups=new HashMap<>();long total=0;
        for(Ledger.Entry entry:entries)if(entry.type.equals(kind)){
            total=Math.addExact(total,entry.amount);
            groups.put(entry.category,Math.addExact(groups.getOrDefault(entry.category,0L),entry.amount));
        }
        List<Category> categories=new ArrayList<>();for(Map.Entry<String,Long> g:groups.entrySet())categories.add(new Category(g.getKey(),g.getValue()));
        categories.sort((a,b)->{int amount=Long.compare(b.amount,a.amount);return amount!=0?amount:a.name.compareTo(b.name);});
        boolean tiny=false;for(Category c:categories)if(c.amount*100.0/total<1)tiny=true;
        List<Category> chart=new ArrayList<>();int grouped=0;
        if(categories.size()<=MAX_CHART_CATEGORIES)chart.addAll(categories);
        else {
            chart.addAll(categories.subList(0,MAX_CHART_CATEGORIES-1));long rest=0;
            for(int i=MAX_CHART_CATEGORIES-1;i<categories.size();i++)rest=Math.addExact(rest,categories.get(i).amount);
            grouped=categories.size()-(MAX_CHART_CATEGORIES-1);chart.add(new Category("그 외 "+grouped+"개",rest));
        }
        return new Summary(kind,total,categories,chart,grouped,tiny);
    }
    private StatisticsData(){}
}
