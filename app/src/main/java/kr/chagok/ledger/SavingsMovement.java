package kr.chagok.ledger;

import java.io.*;
import java.util.*;

/** Maturity allocation. Only interest/loss, net saving and new investment affect cash flow.
 *  Allocations may exceed the received amount; the shortfall is paid from available money (available() < 0). */
final class SavingsMovement {
    final String id,date,source,savingCategory,investmentCategory,memo;
    final long principal,received,saving,investment;
    SavingsMovement(String id,String date,String source,long principal,long received,long saving,String savingCategory,long investment,String investmentCategory,String memo){
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("이동 ID를 확인하세요.");
        this.id=id;this.date=Ledger.normalizeDate(date);this.source=name(source);this.savingCategory=name(savingCategory);this.investmentCategory=name(investmentCategory);
        if(principal<1||principal>Ledger.MAX_AMOUNT)throw new IllegalArgumentException("이미 저축으로 기록한 원금을 입력하세요.");
        for(long n:new long[]{received,saving,investment})if(n<0||n>Ledger.MAX_AMOUNT)throw new IllegalArgumentException("금액은 0~999,999,999,999원의 정수예요.");
        this.principal=principal;this.received=received;this.saving=saving;this.investment=investment;
        this.memo=memo==null?"":memo.trim();if(this.memo.length()>300)throw new IllegalArgumentException("메모는 300자까지 입력하세요.");
    }
    static String name(String s){if(s==null||s.trim().isEmpty()||s.trim().length()>40)throw new IllegalArgumentException("상세 분류는 1~40자로 입력하세요.");return s.trim();}
    long interest(){return received-principal;}
    long available(){return received-saving-investment;}
    String prefix(){return "move:"+id+":";}
    List<Ledger.Entry> entries(){
        List<Ledger.Entry> out=new ArrayList<>();String note="저축 해지·배분 · "+source+" → "+savingCategory+" / "+investmentCategory+(memo.isEmpty()?"":" · "+memo);
        if(interest()>0)out.add(new Ledger.Entry(prefix()+"income",date,"수입",interest(),"저축 해지 이자",note,0));
        if(interest()<0)out.add(new Ledger.Entry(prefix()+"loss",date,"지출",-interest(),"저축 해지 손실",note,0));
        long net=saving-principal;
        if(net!=0)out.add(new Ledger.Entry(prefix()+"saving",date,net>0?"저축":"저축회수",Math.abs(net),net>0?savingCategory:source,note,0));
        if(investment>0)out.add(new Ledger.Entry(prefix()+"investment",date,"투자",investment,investmentCategory,note,0));
        return out;
    }
    String encode(){return encodeAll(Collections.singletonList(this));}
    static String encodeAll(List<SavingsMovement> list){try{if(list.size()>10000)throw new IllegalArgumentException("이동은 10,000건까지 저장할 수 있어요.");ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeInt(0x534d5631);o.writeInt(list.size());for(SavingsMovement m:list){o.writeUTF(m.id);o.writeUTF(m.date);o.writeUTF(m.source);o.writeLong(m.principal);o.writeLong(m.received);o.writeLong(m.saving);o.writeUTF(m.savingCategory);o.writeLong(m.investment);o.writeUTF(m.investmentCategory);o.writeUTF(m.memo);}o.flush();return Base64.getEncoder().encodeToString(b.toByteArray());}catch(IOException ex){throw new IllegalArgumentException("이동을 저장하지 못했어요.",ex);}}
    static List<SavingsMovement> decodeAll(String text){try{if(text.length()>16_000_000)throw new IOException("limit");DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(text)));if(in.readInt()!=0x534d5631)throw new IOException("version");int n=in.readInt();if(n<0||n>10000)throw new IOException("count");List<SavingsMovement> out=new ArrayList<>();Set<String> ids=new HashSet<>();for(int i=0;i<n;i++){SavingsMovement m=new SavingsMovement(in.readUTF(),in.readUTF(),in.readUTF(),in.readLong(),in.readLong(),in.readLong(),in.readUTF(),in.readLong(),in.readUTF(),in.readUTF());if(!ids.add(m.id))throw new IOException("duplicate");out.add(m);}if(in.read()!=-1)throw new IOException("trailing");return out;}catch(Exception ex){throw new IllegalArgumentException("저축 이동 파일이 손상됐어요.",ex);}}
}
