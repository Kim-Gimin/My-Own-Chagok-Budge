package kr.chagok.ledger;

import java.io.*;
import java.util.*;

/** Preference-only ordering. Category identity and ledger records remain unchanged. */
final class CategoryOrder {
    private static final int MAGIC=0x434f5231, LIMIT=10000;
    static List<String> apply(Collection<String> available,Collection<String> saved){
        Set<String> remaining=new LinkedHashSet<>(CategoryNames.clean(available));
        List<String> result=new ArrayList<>();
        for(String name:saved){String clean=Ledger.cleanCategory(name);if(remaining.remove(clean))result.add(clean);}
        result.addAll(remaining);return result;
    }
    static List<String> move(List<String> visible,String name,int delta){
        if(delta!=-1&&delta!=1)throw new IllegalArgumentException("한 칸씩 위나 아래로 이동하세요.");
        List<String> result=new ArrayList<>(visible);int index=result.indexOf(Ledger.cleanCategory(name));
        if(index>=0&&index+delta>=0&&index+delta<result.size())Collections.swap(result,index,index+delta);
        return result;
    }
    /** Moves one visible item to a new slot, as a drag and drop does. */
    static List<String> moveTo(List<String> visible,int from,int to){
        if(from<0||from>=visible.size()||to<0||to>=visible.size())throw new IllegalArgumentException("옮길 위치를 찾지 못했어요.");
        List<String> result=new ArrayList<>(visible);result.add(to,result.remove(from));return result;
    }
    /** Accepts a full new order only when it is a permutation of the current visible list. */
    static List<String> reorder(List<String> visible,List<String> proposed){
        List<String> current=new ArrayList<>(),next=new ArrayList<>();for(String n:visible)current.add(Ledger.cleanCategory(n));for(String n:proposed)next.add(Ledger.cleanCategory(n));
        if(current.size()!=next.size()||new HashSet<>(next).size()!=next.size()||!new HashSet<>(current).equals(new HashSet<>(next)))throw new IllegalArgumentException("분류 목록이 바뀌었어요. 다시 시도해 주세요.");
        return next;
    }
    static String encode(Collection<String> visible,Collection<String> previous){
        // Retain hidden names for restoration without reviving them in visible choices.
        LinkedHashSet<String> names=new LinkedHashSet<>();
        for(Collection<String> group:Arrays.asList(visible,previous))for(String raw:group){String name=Ledger.cleanCategory(raw);if(!name.isEmpty()){if(name.length()>40)throw new IllegalArgumentException("상세 분류는40자까지예요.");names.add(name);}}
        if(names.size()>LIMIT)throw new IllegalArgumentException("상세 분류 순서는10,000개까지 저장할 수 있어요.");
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(MAGIC);out.writeInt(names.size());for(String name:names)out.writeUTF(name);out.flush();return Base64.getEncoder().encodeToString(bytes.toByteArray());}
        catch(IOException ex){throw new IllegalStateException("상세 분류 순서를 저장하지 못했어요.",ex);}
    }
    static List<String> decode(String value){
        if(value==null||value.isEmpty())return Collections.emptyList();
        try{if(value.length()>4000000)throw new IOException("size");DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)));if(in.readInt()!=MAGIC)throw new IOException("version");int count=in.readInt();if(count<0||count>LIMIT)throw new IOException("count");List<String> result=new ArrayList<>();Set<String> seen=new HashSet<>();for(int i=0;i<count;i++){String name=in.readUTF();if(name.isEmpty()||name.length()>40||!name.equals(Ledger.cleanCategory(name))||!seen.add(name))throw new IOException("name");result.add(name);}if(in.read()!=-1)throw new IOException("tail");return result;}
        catch(Exception ex){throw new IllegalArgumentException("상세 분류 순서 설정을 읽지 못했어요.",ex);}
    }
}
