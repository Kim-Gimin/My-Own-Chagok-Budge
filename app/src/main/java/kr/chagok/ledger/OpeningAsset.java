package kr.chagok.ledger;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
/** Assets held when the ledger starts. These are never transaction income or saving contributions. */
public final class OpeningAsset {
 public static final String[] KINDS={"현금","저축","투자"};
 public final String id,date,kind,category,memo;public final long amount;
 public OpeningAsset(String id,String date,String kind,long amount,String category,String memo){
  this.id=id==null?"":id.trim();if(this.id.length()>128||this.id.matches(".*[\\p{Cntrl}].*"))throw new IllegalArgumentException("기초자산 ID를 확인하세요.");
  this.date=Ledger.normalizeDate(date);if(!Arrays.asList(KINDS).contains(kind))throw new IllegalArgumentException("자산 종류는 현금, 저축, 투자 중 선택하세요.");this.kind=kind;
  if(amount<1||amount>Ledger.MAX_AMOUNT)throw new IllegalArgumentException("기초자산 금액은 1~999,999,999,999원이에요.");this.amount=amount;
  this.category=category==null||category.trim().isEmpty()?"기타":category.trim();this.memo=memo==null?"":memo.trim();if(this.category.length()>40||this.memo.length()>500)throw new IllegalArgumentException("상세 분류는40자, 메모는500자까지예요.");
 }
 public String fingerprint(){return date+":"+kind+":"+amount+":"+category.length()+":"+category+memo.length()+":"+memo;}
 public OpeningAsset withId(String id){return new OpeningAsset(id,date,kind,amount,category,memo);}
 static void sameDate(List<OpeningAsset> rows){String date=null;for(OpeningAsset a:rows){if(date==null)date=a.date;else if(!date.equals(a.date))throw new IllegalArgumentException("기초자산 기준일은 모두 같은 기록 시작일로 맞춰 주세요.");}}
 static void plan(List<OpeningAsset> input,List<OpeningAsset> existing,Ledger.ImportPlan plan){
  Map<String,OpeningAsset> ids=new HashMap<>();Set<String> prints=new HashSet<>();for(OpeningAsset a:existing){ids.put(a.id,a);prints.add(a.fingerprint());}
  for(OpeningAsset a:input){OpeningAsset old=ids.get(a.id);if(!a.id.isEmpty()&&old!=null){if(old.fingerprint().equals(a.fingerprint()))plan.openingDuplicates++;else plan.errors.add("기초자산: 같은 고유ID의 내용이 기존 자산과 달라요.");}
   else if(a.id.isEmpty()&&prints.contains(a.fingerprint()))plan.openingDuplicates++;
   else{OpeningAsset add=a.id.isEmpty()?a.withId(UUID.randomUUID().toString()):a;plan.openingAdditions.add(add);ids.put(add.id,add);prints.add(add.fingerprint());}}
  try{List<OpeningAsset> all=new ArrayList<>(existing);all.addAll(plan.openingAdditions);sameDate(all);}catch(IllegalArgumentException ex){plan.errors.add(ex.getMessage());}
 }
 static Map<String,Long> balances(List<OpeningAsset> opening,List<Ledger.Entry> entries,String asOf){
  String to=Ledger.normalizeDate(asOf),from="0000-01-01";for(OpeningAsset a:opening)if(a.date.compareTo(to)<=0&&(from.equals("0000-01-01")||a.date.compareTo(from)<0))from=a.date;
  Map<String,Long> out=new TreeMap<>();long cash=0;for(OpeningAsset a:opening)if(a.date.compareTo(to)<=0){if(a.kind.equals("현금"))cash=Math.addExact(cash,a.amount);else add(out,a.kind+" · "+a.category,a.amount);}
  for(Ledger.Entry e:entries)if(e.date.compareTo(from)>=0&&e.date.compareTo(to)<=0){long delta=e.type.equals("수입")||e.type.equals("저축회수")?e.amount:-e.amount;cash=Math.addExact(cash,delta);if(e.type.equals("저축")||e.type.equals("저축회수"))add(out,"저축 · "+e.category,e.effect());else if(e.type.equals("투자"))add(out,"투자 · "+e.category,e.amount);}
  out.put("현금 · 거래 후 잔액",cash);return out;
 }
 private static void add(Map<String,Long> map,String key,long amount){map.put(key,Math.addExact(map.getOrDefault(key,0L),amount));}
 String encode(){return encodeAll(Collections.singletonList(this));}
 static String encodeAll(List<OpeningAsset> list){try{if(list.size()>Ledger.MAX_IMPORT_ROWS)throw new IOException("limit");ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeInt(0x4f415331);o.writeInt(list.size());for(OpeningAsset a:list){o.writeUTF(a.id);o.writeUTF(a.date);o.writeUTF(a.kind);o.writeLong(a.amount);o.writeUTF(a.category);o.writeUTF(a.memo);}o.flush();return Base64.getEncoder().encodeToString(b.toByteArray());}catch(IOException ex){throw new IllegalArgumentException("기초자산을 저장하지 못했어요.",ex);}}
 static List<OpeningAsset> decodeAll(String value){try{if(value.length()>16000000)throw new IOException("limit");DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)));if(in.readInt()!=0x4f415331)throw new IOException("version");int n=in.readInt();if(n<0||n>Ledger.MAX_IMPORT_ROWS)throw new IOException("count");List<OpeningAsset> out=new ArrayList<>();Set<String> ids=new HashSet<>();for(int i=0;i<n;i++){OpeningAsset a=new OpeningAsset(in.readUTF(),in.readUTF(),in.readUTF(),in.readLong(),in.readUTF(),in.readUTF());if(a.id.isEmpty()||!ids.add(a.id))throw new IOException("ID");out.add(a);}if(in.read()!=-1)throw new IOException("trailing");return out;}catch(Exception ex){throw new IllegalArgumentException("기초자산 파일이 손상됐어요.",ex);}}
}
