package kr.chagok.ledger;

import java.util.*;

/**
 * 하단 메뉴 구성(1.0.0). 메뉴에 있는 기능 중 3~6개를 골라 순서대로 하단에 놓는다.
 * 저장 형식은 로컬 환경설정 문자열 "BNV1:id,id,..."이며 DB·백업 형식과 무관하다.
 * 알 수 없는 항목·중복은 버리고, 정리 후 개수가 범위를 벗어나면 기본 배치로 돌아간다.
 */
final class BottomNav {
    static final int MIN=3,MAX=6;
    static final String PREFIX="BNV1:";
    /** 대화상자로 여는 기능. 화면 번호(tab)와 겹치지 않는 음수다. */
    static final int OPEN_MOVEMENT=-1,OPEN_OPENING=-2;

    static final class Item {
        final String id,label,menuName,icon;final int target;
        Item(String id,String label,String menuName,String icon,int target){this.id=id;this.label=label;this.menuName=menuName;this.icon=icon;this.target=target;}
        /** 화면으로 이동하는 항목만 현재 화면 표시(선택됨)를 가질 수 있다. */
        boolean screen(){return target>=0;}
    }

    static final List<Item> ITEMS=Collections.unmodifiableList(Arrays.asList(
        new Item("settlement","결산","결산","summary",4),
        new Item("statistics","통계","원형·막대 통계","pie",2),
        new Item("home","홈","홈","home",5),
        new Item("calendar","달력","달력","calendar",0),
        new Item("records","내역","내역","list",1),
        new Item("files","엑셀","엑셀 백업·가져오기","file",3),
        new Item("planning","예산","예산·목표·반복·자주 쓰는 기록","budget",7),
        new Item("trend","추이","월별 추이·전월 비교","trend",8),
        new Item("opening","기초자산","기초자산·현재 보유","wallet",OPEN_OPENING),
        new Item("movement","해지·배분","저축 해지·배분","split",OPEN_MOVEMENT)));

    static final List<String> DEFAULT=Collections.unmodifiableList(Arrays.asList("settlement","statistics","home","calendar","records","files"));

    private BottomNav(){}

    static Item item(String id){for(Item item:ITEMS)if(item.id.equals(id))return item;return null;}

    /** 저장 문자열을 읽는다. 없거나 손상됐거나 범위를 벗어나면 기본 배치(0.9.2까지의 하단 6개)를 돌려준다. */
    static List<String> parse(String raw){
        if(raw==null||!raw.startsWith(PREFIX))return new ArrayList<>(DEFAULT);
        List<String> ids=new ArrayList<>();
        for(String part:raw.substring(PREFIX.length()).split(",",-1)){String id=part.trim();if(item(id)!=null&&!ids.contains(id))ids.add(id);}
        return valid(ids)?ids:new ArrayList<>(DEFAULT);
    }

    static boolean valid(List<String> ids){
        if(ids==null||ids.size()<MIN||ids.size()>MAX)return false;
        Set<String> seen=new HashSet<>();
        for(String id:ids)if(id==null||item(id)==null||!seen.add(id))return false;
        return true;
    }

    /** 저장 직전에 검사한다. 잘못된 구성은 저장하지 않는다. */
    static String encode(List<String> ids){
        if(!valid(ids))throw new IllegalArgumentException("하단 메뉴는 서로 다른 "+MIN+"~"+MAX+"개를 골라야 해요.");
        return PREFIX+String.join(",",ids);
    }

    static List<Item> items(List<String> ids){List<Item> out=new ArrayList<>();for(String id:ids){Item item=item(id);if(item!=null)out.add(item);}return out;}

    /** 하단에 없는 항목을 전체 목록 순서대로 돌려준다. */
    static List<String> available(List<String> ids){List<String> out=new ArrayList<>();for(Item item:ITEMS)if(!ids.contains(item.id))out.add(item.id);return out;}

    static boolean canAdd(List<String> ids){return ids.size()<MAX;}
    static boolean canRemove(List<String> ids){return ids.size()>MIN;}

    /** 끝에 추가한다. 이미 있거나 가득 차면 그대로 둔다. */
    static List<String> add(List<String> ids,String id){List<String> out=new ArrayList<>(ids);if(item(id)!=null&&!out.contains(id)&&out.size()<MAX)out.add(id);return out;}

    /** 뺀다. 최소 개수에 도달했으면 그대로 둔다. */
    static List<String> remove(List<String> ids,String id){List<String> out=new ArrayList<>(ids);if(out.size()>MIN)out.remove(id);return out;}

    /** 끌어서 놓기: from 자리의 항목을 to 자리로 옮긴다. 범위 밖이면 그대로 둔다. */
    static List<String> moveTo(List<String> ids,int from,int to){
        List<String> out=new ArrayList<>(ids);
        if(from<0||to<0||from>=out.size()||to>=out.size()||from==to)return out;
        out.add(to,out.remove(from));return out;
    }

    /** 현재 화면(tab)에 해당하는 하단 항목 위치. 없으면 -1. */
    static int activeIndex(List<String> ids,int tab){for(int i=0;i<ids.size();i++){Item item=item(ids.get(i));if(item!=null&&item.screen()&&item.target==tab)return i;}return -1;}
}
