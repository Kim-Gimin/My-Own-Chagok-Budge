package kr.chagok.ledger;

import java.util.*;

/** Compare the old/new ledger at every balance boundary, without rewriting historical data. */
final class AssetConsistency {
    static final class State {
        final List<OpeningAsset> opening;final List<Ledger.Entry> entries;final List<SavingsMovement> movements;
        State(List<OpeningAsset> a,List<Ledger.Entry> e,List<SavingsMovement> m){opening=a;entries=e;movements=m;}
    }
    static String start(List<OpeningAsset> rows){return rows.isEmpty()?"0000-01-01":rows.get(0).date;}
    static void validate(State before,State after){
        OpeningAsset.sameDate(after.opening);
        Map<String,SavingsMovement> old=new HashMap<>();for(SavingsMovement m:before.movements)old.put(m.id,m);
        Set<String> categories=new HashSet<>();
        Map<String,Ledger.Entry> beforeEntries=OpeningAsset.entryMap(before.entries),afterEntries=OpeningAsset.entryMap(after.entries);
        for(SavingsMovement m:after.movements){SavingsMovement prior=old.get(m.id);if(!OpeningAsset.hasMovementEntries(m,afterEntries)&&(prior==null||OpeningAsset.hasMovementEntries(prior,beforeEntries)))throw new IllegalArgumentException("이동과 연결 내역이 일치하지 않아요. 저축 해지·배분에서 이동 전체를 수정하세요.");}
        for(SavingsMovement m:before.movements){categories.add(m.source);categories.add(m.savingCategory);}
        for(SavingsMovement m:after.movements)if(m.detailed){
            categories.add(m.source);categories.add(m.savingCategory);SavingsMovement prior=old.get(m.id);
            if(m.date.compareTo(start(after.opening))<0&&(prior==null||prior.date.compareTo(start(before.opening))>=0||!m.encode().equals(prior.encode())))throw new IllegalArgumentException("해지 날짜가 기초자산 기준일보다 빨라요. 기준일과 기존 납입 기록을 확인하세요.");
            long missing=missingBasis(m,after.opening),was=prior==null?0:missingBasis(prior,before.opening);
            if(missing>was)throw new IllegalArgumentException("이 이동에 필요한 이전 원금이 부족해져요. 기초자산과 해지·배분을 함께 확인하세요.");
            OpeningAsset linked=m.opening();if(linked!=null){boolean found=false;for(OpeningAsset a:after.opening)if(a.id.equals(linked.id)&&a.fingerprint().equals(linked.fingerprint()))found=true;if(!found)throw new IllegalArgumentException("이동과 연결된 기초자산이 일치하지 않아요.");}
        }
        if(categories.isEmpty())return;
        Map<String,Long> oldMissing=sourceShortfalls(before),newMissing=sourceShortfalls(after);
        for(Map.Entry<String,Long> missing:newMissing.entrySet())if(missing.getValue()>oldMissing.getOrDefault(missing.getKey(),0L))throw new IllegalArgumentException("해지 직전의 저축 원금이 부족해요. 같은 분류에 다시 넣더라도 기존 보유 원금을 먼저 확인하세요.");
        SortedMap<String,Map<String,Long>> left=timeline(before),right=timeline(after);SortedSet<String> dates=new TreeSet<>(left.keySet());dates.addAll(right.keySet());
        Map<String,Long> a=new HashMap<>(),b=new HashMap<>();
        for(String date:dates){Set<String> changed=new HashSet<>();apply(a,left.get(date),changed);apply(b,right.get(date),changed);for(String category:changed)if(categories.contains(category)&&b.getOrDefault(category,0L)<Math.min(0L,a.getOrDefault(category,0L)))throw new IllegalArgumentException(date+"의 ‘"+category+"’ 저축 원금이 부족해져요. 이미 해지한 원금의 재사용이나 기존 납입·기초자산의 변경 여부를 확인하세요.");}
    }
    private static long missingBasis(SavingsMovement m,List<OpeningAsset> rows){if(!m.detailed||m.previousPrincipal==0)return 0;long basis=0;for(OpeningAsset a:rows)if(a.kind.equals("저축")&&a.category.equals(m.source)&&a.date.compareTo(m.date)<=0)basis=Math.addExact(basis,a.amount);return Math.max(0,m.previousPrincipal-basis);}
    private static void apply(Map<String,Long> balances,Map<String,Long> deltas,Set<String> changed){if(deltas==null)return;for(Map.Entry<String,Long> d:deltas.entrySet()){balances.put(d.getKey(),Math.addExact(balances.getOrDefault(d.getKey(),0L),d.getValue()));changed.add(d.getKey());}}
    private static void add(SortedMap<String,Map<String,Long>> timeline,String date,String category,long amount){Map<String,Long> day=timeline.computeIfAbsent(date,k->new HashMap<>());day.put(category,Math.addExact(day.getOrDefault(category,0L),amount));}
    private static SortedMap<String,Map<String,Long>> timeline(State state){
        SortedMap<String,Map<String,Long>> out=new TreeMap<>();String from=start(state.opening);
        for(OpeningAsset a:state.opening)if(a.kind.equals("저축"))add(out,a.date,a.category,a.amount);
        for(Ledger.Entry e:state.entries)if((e.type.equals("저축")||e.type.equals("저축회수"))&&e.date.compareTo(from)>=0)add(out,e.date,e.category,e.effect());
        Map<String,Ledger.Entry> entries=OpeningAsset.entryMap(state.entries);
        for(SavingsMovement m:state.movements)if(!m.detailed&&m.date.compareTo(from)>=0&&OpeningAsset.hasMovementEntries(m,entries)){long moved=Math.min(m.principal,m.saving);add(out,m.date,m.source,-moved);add(out,m.date,m.savingCategory,moved);}
        return out;
    }
    private static Map<String,Long> sourceShortfalls(State state){
        String from=start(state.opening);Map<String,Ledger.Entry> entries=OpeningAsset.entryMap(state.entries);Set<String> linked=new HashSet<>();
        SortedMap<String,List<SavingsMovement>> moves=new TreeMap<>();SortedMap<String,Map<String,Long>> ordinary=new TreeMap<>();
        for(SavingsMovement m:state.movements)if(m.date.compareTo(from)>=0&&OpeningAsset.hasMovementEntries(m,entries)){moves.computeIfAbsent(m.date,k->new ArrayList<>()).add(m);for(Ledger.Entry e:m.entries())linked.add(e.id);}
        for(OpeningAsset a:state.opening)if(a.kind.equals("저축"))add(ordinary,a.date,a.category,a.amount);
        for(Ledger.Entry e:state.entries)if(e.date.compareTo(from)>=0&&!linked.contains(e.id)&&(e.type.equals("저축")||e.type.equals("저축회수")))add(ordinary,e.date,e.category,e.effect());
        SortedSet<String> dates=new TreeSet<>(ordinary.keySet());dates.addAll(moves.keySet());Map<String,Long> balances=new HashMap<>(),missing=new HashMap<>();
        for(String date:dates){apply(balances,ordinary.get(date),new HashSet<>());for(SavingsMovement m:moves.getOrDefault(date,Collections.emptyList())){long source=balances.getOrDefault(m.source,0L);if(m.detailed)missing.put(m.id,Math.max(0,Math.subtractExact(m.principal,source)));balances.put(m.source,Math.subtractExact(source,m.principal));balances.put(m.savingCategory,Math.addExact(balances.getOrDefault(m.savingCategory,0L),m.saving));}}
        return missing;
    }
    private AssetConsistency(){}
}
