package kr.chagok.ledger;
import java.io.*;
import java.util.*;
/** Deterministic synthetic amounts; no user records are used. */
public final class MaturityTests {
 static int n;static void eq(Object a,Object b){n++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 static SavingsMovement m(long p,long r,long s,long i){return new SavingsMovement("synthetic","2026-09-15","예시적금",p,r,s,"예금",i,"투자","");}
 public static void main(String[] args)throws Exception{
  SavingsMovement example=m(1000000,1200000,1100000,50000);long[] t=Ledger.totals(example.entries());eq(200000L,t[0]);eq(100000L,t[2]);eq(50000L,t[3]);eq(50000L,Ledger.available(t));eq(50000L,example.available());
  for(long principal:new long[]{1,100,10000})for(long received:new long[]{0,100,20000})for(int a=0;a<3;a++){
   long saving=received*a/2,investment=(received-saving)/2;SavingsMovement m=m(principal,received,saving,investment);eq(m.available(),Ledger.available(Ledger.totals(m.entries())));eq(m.encode(),SavingsMovement.decodeAll(m.encode()).get(0).encode());
   ByteArrayOutputStream b=new ByteArrayOutputStream();XlsxIO.write(b,m.entries(),false);XlsxIO.ReadResult parsed=XlsxIO.read(new ByteArrayInputStream(b.toByteArray()));eq(0,parsed.invalidRows);eq(m.available(),Ledger.available(Ledger.totals(parsed.entries)));
  }
  SavingsMovement recovered=m(1000000,1200000,0,50000);eq(1150000L,Ledger.available(Ledger.totals(recovered.entries())));eq(-1000000L,Ledger.totals(recovered.entries())[2]);StatisticsData.Summary stat=StatisticsData.aggregate(recovered.entries(),"저축");eq(0,stat.chartCategories.size());eq(0.0,stat.percent(-1000000));eq(0.0,StatisticsData.compare(recovered.entries()).barFraction(2));
  eq(0,m(100,100,100,0).entries().size());
  SavingsMovement synthetic=m(1000000,1200000,900000,300000);long[] u=Ledger.totals(synthetic.entries());eq(200000L,u[0]);eq(0L,u[1]);eq(-100000L,u[2]);eq(300000L,u[3]);eq(0L,Ledger.available(u));eq(0L,synthetic.available());eq(3,synthetic.entries().size());
  eq("저축회수",synthetic.entries().get(1).type);eq("예시적금",synthetic.entries().get(1).category);eq(100000L,synthetic.entries().get(1).amount);
  SavingsMovement topUp=m(1000000,1000000,900000,300000);long[] tu=Ledger.totals(topUp.entries());eq(0L,tu[0]);eq(-100000L,tu[2]);eq(300000L,tu[3]);eq(-200000L,Ledger.available(tu));eq(-200000L,topUp.available());
  eq(-1L,m(100,99,100,0).available());eq(-1L,Ledger.available(Ledger.totals(m(100,99,100,0).entries())));
  {ByteArrayOutputStream ub=new ByteArrayOutputStream();XlsxIO.write(ub,synthetic.entries(),false);XlsxIO.ReadResult ur=XlsxIO.read(new ByteArrayInputStream(ub.toByteArray()));eq(0,ur.invalidRows);eq(0L,Ledger.available(Ledger.totals(ur.entries)));}
  for(long[] invalid:new long[][]{{0,100,100,0},{100,-1,0,0},{100,100,-1,0},{100,100,0,-1},{100,100,Ledger.MAX_AMOUNT+1,0}}){try{m(invalid[0],invalid[1],invalid[2],invalid[3]);throw new AssertionError("accepted invalid allocation");}catch(IllegalArgumentException expected){n++;}}
  ByteArrayOutputStream b=new ByteArrayOutputStream();BackupArchive.write(b,recovered.entries(),new PlanningData(),Collections.singletonList(recovered));BackupArchive.Snapshot restored=BackupArchive.read(new ByteArrayInputStream(b.toByteArray()));eq(recovered.encode(),restored.movements.get(0).encode());eq(1150000L,Ledger.available(Ledger.totals(restored.entries)));
  eq(200000L,Math.addExact(u[2],u[3])); // Net saving plus investment; no later display-ratio helper.
  System.out.println("PASS: "+n+" maturity/allocation checks");
 }
}
