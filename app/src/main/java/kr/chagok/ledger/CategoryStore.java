package kr.chagok.ledger;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Reusable category choices, kept separately for income, expense and saving. */
final class CategoryStore {
    private final SharedPreferences prefs;
    private static final String[][] DEFAULTS = {
        {"월급", "부수입", "상여금", "용돈", "이자", "기타"},
        {"식비", "카페·간식", "교통", "쇼핑", "생활용품", "주거·통신", "의료·건강", "문화·여가", "교육", "여행", "경조사", "기타"},
        {"적금", "예금", "비상금", "청약", "기타"}
    };
    CategoryStore(Context context) { prefs=context.getSharedPreferences("category_choices",Context.MODE_PRIVATE); }
    List<String> choices(String type,List<Ledger.Entry> entries) {
        String kind=Ledger.normalizeType(type);
        LinkedHashSet<String> names=new LinkedHashSet<>(Arrays.asList(DEFAULTS[Ledger.index(kind)]));
        names.addAll(new TreeSet<>(prefs.getStringSet(kind,Collections.emptySet())));
        TreeSet<String> used=new TreeSet<>();
        for(Ledger.Entry entry:entries) if(entry.type.equals(kind)) used.add(entry.category);
        names.addAll(used);
        return new ArrayList<>(names);
    }
    boolean remember(String type,String name) {
        String kind=Ledger.normalizeType(type), cleaned=validate(name);
        Set<String> names=new HashSet<>(prefs.getStringSet(kind,Collections.emptySet()));
        if(Arrays.asList(DEFAULTS[Ledger.index(kind)]).contains(cleaned) || !names.add(cleaned)) return false;
        if(!prefs.edit().putStringSet(kind,names).commit()) throw new IllegalStateException("분류를 저장하지 못했어요. 다시 시도해 주세요.");
        return true;
    }
    private static String validate(String name) {
        String value=name==null?"":name.trim();
        if(value.isEmpty()) throw new IllegalArgumentException("분류 이름을 입력해 주세요.");
        if(value.length()>40) throw new IllegalArgumentException("분류는 40자까지 입력할 수 있어요.");
        return value;
    }
}
