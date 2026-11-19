package kr.chagok.ledger;
import java.util.*;
/** Pure category choices shared by entry, management and living-budget controls. */
final class CategoryNames {
 static String kind(String type){String k=Ledger.normalizeType(type);return k.equals("저축회수")?"저축":k;}
 static Set<String> clean(Collection<String> names){Set<String> result=new TreeSet<>();for(String raw:names){String s=Ledger.cleanCategory(raw);if(!s.isEmpty()){if(s.length()>40)throw new IllegalArgumentException("상세 분류는40자까지예요.");result.add(s);}}return result;}
 static List<String> choices(String type,List<Ledger.Entry> entries,PlanningData plan,Collection<String> added,Collection<String> deleted){String k=kind(type);Set<String> names=new TreeSet<>(clean(added));for(Ledger.Entry e:entries)if(Ledger.matches(e,k))names.add(e.category);for(PlanningData.Limit l:plan.limits)if(kind(l.type).equals(k)&&!l.category.isEmpty())names.add(l.category);for(PlanningData.Favorite f:plan.favorites)if(kind(f.type).equals(k))names.add(f.category);for(PlanningData.Repeat r:plan.repeats)if(kind(r.type).equals(k))names.add(r.category);if(k.equals("지출"))names.addAll(plan.livingCategories());names=clean(names);names.removeAll(clean(deleted));return new ArrayList<>(names);}
}
