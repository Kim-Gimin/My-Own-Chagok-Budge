package kr.chagok.ledger;
import java.io.*;
import java.util.*;

/** Preference ordering contracts; all names are synthetic. */
public final class CategoryOrderTests {
 static int checks;interface Action{void run()throws Exception;}
 static void eq(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError("expected="+a+", actual="+b);}
 static void rejects(Action a)throws Exception{checks++;try{a.run();}catch(Exception good){return;}throw new AssertionError("expected rejection");}
 static List<String> names(String... values){return Arrays.asList(values);}
 static String malformed(int magic,int count,String... names)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeInt(magic);o.writeInt(count);for(String n:names)o.writeUTF(n);o.close();return Base64.getEncoder().encodeToString(b.toByteArray());}
 public static void main(String[] args)throws Exception{
  List<String> all=names("교통","식비","카페"),order=names("식비","교통","카페");
  eq(all,CategoryOrder.apply(all,Collections.emptyList()));eq(order,CategoryOrder.apply(all,order));
  eq(order,CategoryOrder.apply(all,names("없는 분류","식비","식비","교통","카페")));
  eq(names("식비","교통","카페","간식"),CategoryOrder.apply(names("간식","교통","식비","카페"),order));
  eq(names("식비","교통"),CategoryOrder.apply(names("교통","식비"),order));
  eq(order,CategoryOrder.move(all,"식비",-1));eq(names("교통","카페","식비"),CategoryOrder.move(all,"식비",1));
  eq(all,CategoryOrder.move(all,"교통",-1));eq(all,CategoryOrder.move(all,"카페",1));eq(all,CategoryOrder.move(all,"없음",1));
  rejects(()->CategoryOrder.move(all,"식비",0));rejects(()->CategoryOrder.move(all,"식비",2));eq(all,names("교통","식비","카페"));
  String encoded=CategoryOrder.encode(order,Collections.emptyList());eq(order,CategoryOrder.decode(encoded));eq(encoded,CategoryOrder.encode(CategoryOrder.decode(encoded),Collections.emptyList()));
  eq(order,CategoryOrder.decode(CategoryOrder.encode(names("\u00a0식비\u3000","교통","식비","카페"),Collections.emptyList())));
  List<String> hidden=CategoryOrder.decode(CategoryOrder.encode(names("식비","교통"),order));eq(order,hidden);eq(names("식비","교통"),CategoryOrder.apply(names("교통","식비"),hidden));eq(order,CategoryOrder.apply(all,hidden));
  List<String> special=names("내 분류 : / A_Z","내  분류","  끝공백  ","한".repeat(40));eq(names("내 분류 : / A_Z","내  분류","끝공백","한".repeat(40)),CategoryOrder.decode(CategoryOrder.encode(special,Collections.emptyList())));
  rejects(()->CategoryOrder.encode(names("한".repeat(41)),Collections.emptyList()));
  eq(Collections.emptyList(),CategoryOrder.decode(null));eq(Collections.emptyList(),CategoryOrder.decode(""));
  rejects(()->CategoryOrder.decode("not-base64"));rejects(()->CategoryOrder.decode(malformed(0,0)));rejects(()->CategoryOrder.decode(malformed(0x434f5231,-1)));rejects(()->CategoryOrder.decode(malformed(0x434f5231,10001)));
  rejects(()->CategoryOrder.decode(malformed(0x434f5231,1)));rejects(()->CategoryOrder.decode(malformed(0x434f5231,1,"")));rejects(()->CategoryOrder.decode(malformed(0x434f5231,1," 식비")));rejects(()->CategoryOrder.decode(malformed(0x434f5231,1,"한".repeat(41))));rejects(()->CategoryOrder.decode(malformed(0x434f5231,2,"식비","식비")));rejects(()->CategoryOrder.decode(malformed(0x434f5231,0,"tail")));
  List<String> many=new ArrayList<>();for(int i=0;i<10000;i++)many.add(String.format(Locale.ROOT,"분류%05d",i));Collections.reverse(many);eq(many,CategoryOrder.decode(CategoryOrder.encode(many,Collections.emptyList())));eq(many,CategoryOrder.apply(many,many));many.add("초과");rejects(()->CategoryOrder.encode(many,Collections.emptyList()));
  // Applying an order cannot add removed/unknown categories or change input collections.
  List<String> before=new ArrayList<>(all);eq(3,CategoryOrder.apply(all,names("식비","숨김","계획 없음")).size());eq(before,all);
  System.out.println("PASS: "+checks+" category order/codec/boundary checks");
 }
}
