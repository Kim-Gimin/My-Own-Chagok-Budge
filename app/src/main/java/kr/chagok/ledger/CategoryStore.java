package kr.chagok.ledger;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Detailed categories, kept separately for each transaction type. */
final class CategoryStore {
    private final SharedPreferences prefs;
    CategoryStore(Context context) { prefs=context.getSharedPreferences("category_choices",Context.MODE_PRIVATE); }
    List<String> choices(String type,List<Ledger.Entry> entries){return choices(type,entries,new PlanningData());}
    List<String> choices(String type,List<Ledger.Entry> entries,PlanningData plan) {
        String kind=kind(type);
        List<String> available=CategoryNames.choices(kind,entries,plan,prefs.getStringSet(kind,Collections.emptySet()),prefs.getStringSet("deleted_"+kind,Collections.emptySet()));
        return CategoryOrder.apply(available,savedOrder(kind));
    }

    private List<String> savedOrder(String kind){
        try{return CategoryOrder.decode(prefs.getString("order_"+kind,""));}
        catch(IllegalArgumentException|ClassCastException ex){return Collections.emptyList();}
    }
    boolean move(String type,String name,int delta,List<Ledger.Entry> entries,PlanningData plan){
        String kind=kind(type);List<String> before=choices(kind,entries,plan),after=CategoryOrder.move(before,name,delta);
        if(before.equals(after))return false;
        String value=CategoryOrder.encode(after,savedOrder(kind));
        if(!prefs.edit().putString("order_"+kind,value).commit())throw new IllegalStateException("상세 분류 순서를 저장하지 못했어요. 다시 시도해 주세요.");
        return true;
    }

    boolean remember(String type,String name) {return remember(type,name,null,null);}
    boolean remember(String type,String name,List<Ledger.Entry> entries,PlanningData plan) {
        String kind=kind(type), cleaned=validate(name);
        Set<String> names=new HashSet<>(CategoryNames.clean(prefs.getStringSet(kind,Collections.emptySet())));
        Set<String> deleted=new HashSet<>(CategoryNames.clean(prefs.getStringSet("deleted_"+kind,Collections.emptySet())));
        boolean restored=deleted.remove(cleaned),added=names.add(cleaned);
        if(!restored&&!added)return false;
        SharedPreferences.Editor edit=prefs.edit().putStringSet(kind,names).putStringSet("deleted_"+kind,deleted);
        List<String> order=savedOrder(kind);
        if(!order.isEmpty()&&!order.contains(cleaned)){
            List<String> next=entries==null?new ArrayList<>(order):choices(kind,entries,plan);
            if(!next.contains(cleaned))next.add(cleaned);
            edit.putString("order_"+kind,CategoryOrder.encode(next,order));
        }
        if(!edit.commit()) throw new IllegalStateException("상세 분류를 저장하지 못했어요. 다시 시도해 주세요.");
        return true;
    }
    boolean delete(String type,String name){
        String kind=kind(type),cleaned=validate(name);Set<String> names=new HashSet<>(CategoryNames.clean(prefs.getStringSet(kind,Collections.emptySet()))),deleted=new HashSet<>(CategoryNames.clean(prefs.getStringSet("deleted_"+kind,Collections.emptySet())));
        boolean changed=deleted.add(cleaned),removed=names.remove(cleaned);if(!changed&&!removed)return false;
        if(!prefs.edit().putStringSet(kind,names).putStringSet("deleted_"+kind,deleted).commit())throw new IllegalStateException("상세 분류를 삭제하지 못했어요. 다시 시도해 주세요.");return true;
    }
    List<String> deleted(String type){return new ArrayList<>(new TreeSet<>(CategoryNames.clean(prefs.getStringSet("deleted_"+kind(type),Collections.emptySet()))));}
    static String kind(String type) { return CategoryNames.kind(type); }
    private static String validate(String name) {
        String value=Ledger.cleanCategory(name);
        if(value.isEmpty()) throw new IllegalArgumentException("상세 분류 이름을 입력해 주세요.");
        if(value.length()>40) throw new IllegalArgumentException("상세 분류는 40자까지 입력할 수 있어요.");
        return value;
    }
}
