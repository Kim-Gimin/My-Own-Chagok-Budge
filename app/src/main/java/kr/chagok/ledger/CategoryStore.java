package kr.chagok.ledger;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Detailed categories, kept separately for each transaction type. */
final class CategoryStore {
    private final SharedPreferences prefs;
    private static final String[][] DEFAULTS = {
        {"월급", "부수입", "상여금", "용돈", "이자", "기타"},
        {"식비", "카페·간식", "교통", "쇼핑", "생활용품", "주거·통신", "의료·건강", "문화·여가", "교육", "여행", "경조사", "기타"},
        {"적금", "예금", "비상금", "청약", "기타"},
        {"주식", "ETF", "펀드", "채권", "가상자산", "기타"}
    };
    CategoryStore(Context context) { prefs=context.getSharedPreferences("category_choices",Context.MODE_PRIVATE); }
    List<String> choices(String type,List<Ledger.Entry> entries) {
        String kind=kind(type);
        LinkedHashSet<String> names=new LinkedHashSet<>(Arrays.asList(DEFAULTS[Ledger.index(kind)]));
        names.addAll(new TreeSet<>(prefs.getStringSet(kind,Collections.emptySet())));
        TreeSet<String> used=new TreeSet<>();
        for(Ledger.Entry entry:entries) if(Ledger.matches(entry,kind)) used.add(entry.category);
        names.addAll(used);
        names.removeAll(prefs.getStringSet("deleted_"+kind,Collections.emptySet()));
        return new ArrayList<>(names);
    }
    boolean remember(String type,String name) {
        String kind=kind(type), cleaned=validate(name);
        Set<String> names=new HashSet<>(prefs.getStringSet(kind,Collections.emptySet()));
        Set<String> deleted=new HashSet<>(prefs.getStringSet("deleted_"+kind,Collections.emptySet()));
        boolean restored=deleted.remove(cleaned),added=!Arrays.asList(DEFAULTS[Ledger.index(kind)]).contains(cleaned)&&names.add(cleaned);
        if(!restored&&!added)return false;
        if(!prefs.edit().putStringSet(kind,names).putStringSet("deleted_"+kind,deleted).commit()) throw new IllegalStateException("상세 분류를 저장하지 못했어요. 다시 시도해 주세요.");
        return true;
    }
    boolean delete(String type,String name){
        String kind=kind(type),cleaned=validate(name);Set<String> names=new HashSet<>(prefs.getStringSet(kind,Collections.emptySet())),deleted=new HashSet<>(prefs.getStringSet("deleted_"+kind,Collections.emptySet()));
        boolean changed=deleted.add(cleaned),removed=names.remove(cleaned);if(!changed&&!removed)return false;
        if(!prefs.edit().putStringSet(kind,names).putStringSet("deleted_"+kind,deleted).commit())throw new IllegalStateException("상세 분류를 삭제하지 못했어요. 다시 시도해 주세요.");return true;
    }
    List<String> deleted(String type){return new ArrayList<>(new TreeSet<>(prefs.getStringSet("deleted_"+kind(type),Collections.emptySet())));}
    static String kind(String type) { String normalized=Ledger.normalizeType(type);return normalized.equals("저축회수")?"저축":normalized; }
    private static String validate(String name) {
        String value=name==null?"":name.trim();
        if(value.isEmpty()) throw new IllegalArgumentException("상세 분류 이름을 입력해 주세요.");
        if(value.length()>40) throw new IllegalArgumentException("상세 분류는 40자까지 입력할 수 있어요.");
        return value;
    }
}
