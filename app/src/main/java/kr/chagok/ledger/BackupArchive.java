package kr.chagok.ledger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Local portable snapshot of entries and planning only. Never contains a PIN or UI preferences. */
final class BackupArchive {
    static final int LIMIT=40*1024*1024;
    static final class Snapshot {final List<Ledger.Entry> entries;final PlanningData planning;Snapshot(List<Ledger.Entry> entries,PlanningData planning){this.entries=entries;this.planning=planning;}}
    static void write(OutputStream stream,List<Ledger.Entry> entries,PlanningData planning)throws Exception{
        ByteArrayOutputStream xlsx=new ByteArrayOutputStream();XlsxIO.write(xlsx,entries,false);
        try(ZipOutputStream zip=new ZipOutputStream(stream)){part(zip,"format.txt","CHAGOK-PLANNING-BACKUP-1".getBytes(StandardCharsets.UTF_8));part(zip,"내역.xlsx",xlsx.toByteArray());part(zip,"계획.dat",planning.encode().getBytes(StandardCharsets.US_ASCII));zip.finish();zip.flush();}
    }
    static void part(ZipOutputStream zip,String name,byte[] bytes)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    static Snapshot read(InputStream stream)throws Exception{
        Map<String,byte[]> parts=new HashMap<>();int total=0;
        try(ZipInputStream zip=new ZipInputStream(stream)){ZipEntry e;byte[] buffer=new byte[8192];while((e=zip.getNextEntry())!=null){if(!Arrays.asList("format.txt","내역.xlsx","계획.dat").contains(e.getName())||e.isDirectory()||parts.containsKey(e.getName()))throw new IOException("차곡 계획 포함 백업 파일인지 확인하세요.");ByteArrayOutputStream bytes=new ByteArrayOutputStream();int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>LIMIT)throw new IOException("백업 파일이 너무 커요.");bytes.write(buffer,0,n);}parts.put(e.getName(),bytes.toByteArray());}}
        if(parts.size()!=3||!"CHAGOK-PLANNING-BACKUP-1".equals(new String(parts.get("format.txt"),StandardCharsets.UTF_8)))throw new IOException("지원하지 않는 백업 형식이에요.");
        XlsxIO.ReadResult read=XlsxIO.read(new ByteArrayInputStream(parts.get("내역.xlsx")));if(read.invalidRows>0)throw new IOException("백업 내역에 오류가 있어요.");
        return new Snapshot(read.entries,PlanningData.decode(new String(parts.get("계획.dat"),StandardCharsets.US_ASCII)));
    }
}
