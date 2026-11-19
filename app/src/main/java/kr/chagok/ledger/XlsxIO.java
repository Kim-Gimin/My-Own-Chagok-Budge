package kr.chagok.ledger;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Small OOXML reader/writer. No network, external entities, macros, or formula evaluation. */
public final class XlsxIO {
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final int MAX_XML = 32 * 1024 * 1024, MAX_TOTAL = 64 * 1024 * 1024;
    private static final String[] HEADERS = {"날짜", "구분", "금액", "분류", "메모", "고유ID"};
    public static final class ReadResult {
        public final List<Ledger.Entry> entries = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public int invalidRows;
    }
    public static void write(OutputStream output, List<Ledger.Entry> input, boolean template) throws IOException {
        List<Ledger.Entry> entries = new ArrayList<>(input);
        entries.sort(Comparator.comparing((Ledger.Entry e) -> e.date).thenComparing(e -> e.id));
        if (entries.size() > Ledger.MAX_IMPORT_ROWS) throw new IOException("한 파일에 최대 10,000건을 저장할 수 있어요. 기간을 나누어 백업해 주세요.");
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            put(zip,"[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            put(zip,"_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+REL+"/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip,"xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\""+NS+"\" xmlns:r=\""+REL+"\"><sheets><sheet name=\"내역\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"사용 안내\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
            put(zip,"xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+REL+"/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\""+REL+"/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\""+REL+"/styles\" Target=\"styles.xml\"/></Relationships>");
            put(zip,"xl/styles.xml", "<?xml version=\"1.0\"?><styleSheet xmlns=\""+NS+"\"><numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd\"/><numFmt numFmtId=\"165\" formatCode=\"#,##0\"/></numFmts><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"맑은 고딕\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"맑은 고딕\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF19765B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+NS+"\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"1\" width=\"16\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"12\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"20\" customWidth=\"1\"/><col min=\"4\" max=\"4\" width=\"22\" customWidth=\"1\"/><col min=\"5\" max=\"5\" width=\"52\" customWidth=\"1\"/><col min=\"6\" max=\"6\" width=\"40\" customWidth=\"1\"/></cols><sheetData><row r=\"1\" ht=\"28\" customHeight=\"1\">");
            for (int c=0;c<HEADERS.length;c++) sheet.append(textCell(col(c)+"1",HEADERS[c],1));
            sheet.append("</row>");
            int r=2;
            for (Ledger.Entry entry : entries) {
                sheet.append("<row r=\"").append(r).append("\">");
                sheet.append("<c r=\"A").append(r).append("\" s=\"2\"><v>").append(excelSerial(LocalDate.parse(entry.date))).append("</v></c>");
                sheet.append(textCell("B"+r,entry.type,0));
                sheet.append("<c r=\"C").append(r).append("\" s=\"3\"><v>").append(entry.amount).append("</v></c>");
                sheet.append(textCell("D"+r,entry.category,0)).append(textCell("E"+r,entry.memo,0)).append(textCell("F"+r,entry.id,0)).append("</row>");
                r++;
            }
            sheet.append("</sheetData><autoFilter ref=\"A1:F").append(Math.max(1,r-1)).append("\"/>");
            if (template) sheet.append("<dataValidations count=\"1\"><dataValidation type=\"list\" allowBlank=\"0\" showErrorMessage=\"1\" sqref=\"B2:B10001\"><formula1>&quot;수입,지출,저축&quot;</formula1></dataValidation></dataValidations>");
            sheet.append("</worksheet>");
            put(zip,"xl/worksheets/sheet1.xml",sheet.toString());
            String[] notes = {"차곡가계부 엑셀 사용 안내", "내역 시트의 첫 번째 행은 바꾸지 말고, 두 번째 행부터 입력하세요.", "날짜: YYYY-MM-DD 문자 또는 엑셀 날짜 셀 / 구분: 수입, 지출, 저축", "금액: 1~999,999,999,999원의 정수. 분류와 메모는 생략할 수 있어요.", "새 내역의 고유ID는 비워 두세요. 백업 파일의 ID는 복원 시 중복을 확인하는 데 사용해요.", "ID가 없으면 날짜·구분·금액·분류·메모가 모두 같은 내역을 중복으로 보아요.", "백업 내역의 내용을 수정한 뒤 같은 ID로 가져오면 충돌로 표시돼요. 기존 내역 수정은 앱에서 해 주세요.", "수식은 값으로 붙여 넣은 뒤 저장해 주세요. .xlsx만 지원해요. .xls는 .xlsx로 다시 저장하세요.", "가져오기는 최대 10,000건. 오류가 있으면 전체를 추가하지 않으며, 기존 내역은 지우지 않아요.", "사용 가능 금액 = 수입 − 지출 − 저축. 저축은 지출 통계에 포함하지 않아요.", "앱 삭제 시 휴대폰 내 기록도 삭제돼요. 백업 파일은 휴대폰 밖에도 보관해 주세요."};
            StringBuilder guide = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+NS+"\"><cols><col min=\"1\" max=\"1\" width=\"110\" customWidth=\"1\"/></cols><sheetData>");
            for(int i=0;i<notes.length;i++) guide.append("<row r=\"").append(i+1).append("\" ht=\"28\" customHeight=\"1\">").append(textCell("A"+(i+1),notes[i],i==0?1:0)).append("</row>");
            put(zip,"xl/worksheets/sheet2.xml",guide.append("</sheetData></worksheet>").toString());
        }
    }
    private static void put(ZipOutputStream zip,String path,String text) throws IOException {
        zip.putNextEntry(new ZipEntry(path)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static String textCell(String address,String text,int style) { return "<c r=\""+address+"\" t=\"inlineStr\" s=\""+style+"\"><is><t xml:space=\"preserve\">"+escape(text)+"</t></is></c>"; }
    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(c -> {
            if(c=='&') out.append("&amp;"); else if(c=='<') out.append("&lt;"); else if(c=='>') out.append("&gt;"); else if(c=='\"') out.append("&quot;");
            else if(c==9 || c==10 || c==13 || (c>=32 && c<=0xD7FF) || (c>=0xE000 && c<=0xFFFD) || (c>=0x10000 && c<=0x10FFFF)) out.appendCodePoint(c);
            else out.append('\uFFFD');
        });
        return out.toString();
    }
    private static String col(int i) { return String.valueOf((char)('A'+i)); }
    private static long excelSerial(LocalDate date) {
        long days = ChronoUnit.DAYS.between(LocalDate.of(1899,12,31),date);
        return date.isBefore(LocalDate.of(1900,3,1)) ? days : days+1;
    }
    public static ReadResult read(InputStream input) throws Exception {
        Map<String,byte[]> parts = new HashMap<>();
        int total=0, count=0;
        try(ZipInputStream zip = new ZipInputStream(input,StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                if(++count>2048) throw new IOException("파일 구성 항목이 너무 많아요.");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int n, size=0;
                while((n=zip.read(buffer))!=-1) {
                    total+=n; size+=n;
                    if(total>MAX_TOTAL || size>MAX_XML) throw new IOException("파일이 너무 커요. 최대 10,000건씩 나누어 가져와 주세요.");
                    bytes.write(buffer,0,n);
                }
                if(entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels")) {
                    if(parts.put(entry.getName(),bytes.toByteArray())!=null) throw new IOException("중복 구성 항목이 있는 파일이에요.");
                }
            }
        }
        if(!parts.containsKey("xl/workbook.xml")) throw new IOException("일반 .xlsx 파일을 선택해 주세요. 암호화 파일과 .xls는 지원하지 않아요.");
        List<String> strings = new ArrayList<>();
        if(parts.containsKey("xl/sharedStrings.xml")) parse(parts.get("xl/sharedStrings.xml"),new DefaultHandler() {
            StringBuilder value; boolean t;
            public void startElement(String uri,String local,String q,Attributes a) { if(local.equals("si")) value=new StringBuilder(); if(local.equals("t")) t=true; }
            public void characters(char[] c,int start,int len) { if(t && value!=null) value.append(c,start,len); }
            public void endElement(String uri,String local,String q) throws SAXException { if(local.equals("t")) t=false; if(local.equals("si")) { if(strings.size()>=100000) throw new SAXException("문자열이 너무 많아요."); strings.add(value.toString()); value=null; } }
        });
        Map<String,String> rels = new HashMap<>();
        parse(parts.get("xl/_rels/workbook.xml.rels"),new DefaultHandler() {
            public void startElement(String uri,String local,String q,Attributes a) throws SAXException {
                if(local.equals("Relationship") && !"External".equals(a.getValue("TargetMode"))) {
                    String target=a.getValue("Target");
                    if(target==null) return;
                    String path=target.startsWith("/")?target.substring(1):"xl/"+target;
                    path=java.net.URI.create(path).normalize().getPath();
                    if(path.startsWith("xl/") && !path.contains("..")) rels.put(a.getValue("Id"),path);
                }
            }
        });
        String[] selected={null}; boolean[] date1904={false};
        parse(parts.get("xl/workbook.xml"),new DefaultHandler() {
            public void startElement(String uri,String local,String q,Attributes a) {
                if(local.equals("workbookPr")) date1904[0]="1".equals(a.getValue("date1904")) || "true".equals(a.getValue("date1904"));
                if(local.equals("sheet") && (selected[0]==null || "내역".equals(a.getValue("name")))) {
                    String id=a.getValue(REL,"id"); if(id==null) id=a.getValue("r:id");
                    selected[0]=rels.get(id);
                }
            }
        });
        if(selected[0]==null || !parts.containsKey(selected[0])) throw new IOException("내역 시트를 찾지 못했어요.");
        ReadResult result = new ReadResult();
        parse(parts.get(selected[0]),new SheetHandler(strings,date1904[0],result));
        return result;
    }
    private static void parse(byte[] xml,DefaultHandler handler) throws Exception {
        if(xml==null) throw new IOException("필수 엑셀 구성 항목이 없어요.");
        String guard=new String(xml,StandardCharsets.UTF_8).replace("\u0000", "");
        if(guard.contains("<!DOCTYPE") || guard.contains("<!ENTITY")) throw new IOException("외부 엔터티가 포함된 XML은 지원하지 않아요.");
        SAXParserFactory factory=SAXParserFactory.newInstance(); factory.setNamespaceAware(true);
        try { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true); } catch(Exception ignored) {}
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); } catch(Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-general-entities",false); } catch(Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false); } catch(Exception ignored) {}
        XMLReader reader=factory.newSAXParser().getXMLReader();
        reader.setEntityResolver((publicId,systemId)->{ throw new SAXException("외부 참조를 읽을 수 없어요."); });
        reader.setContentHandler(handler);
        reader.parse(new InputSource(new ByteArrayInputStream(xml)));
    }
    private static final class Cell { String value="",type=""; boolean formula; }
    private static final class SheetHandler extends DefaultHandler {
        final List<String> strings; final boolean date1904; final ReadResult result;
        final Map<Integer,Cell> cells=new HashMap<>(); final Map<String,Integer> headers=new HashMap<>();
        int row, column, rows; boolean inValue,inText,hasHeader; Cell cell; StringBuilder value;
        SheetHandler(List<String> strings,boolean date1904,ReadResult result) { this.strings=strings; this.date1904=date1904; this.result=result; }
        public void startElement(String uri,String local,String q,Attributes a) throws SAXException {
            if(local.equals("row")) { cells.clear(); String r=a.getValue("r"); row=r==null?row+1:Integer.parseInt(r); column=-1; }
            if(local.equals("c")) {
                column++; String address=a.getValue("r");
                if(address!=null) { column=0; for(char c:address.toCharArray()) { if(c<'A' || c>'Z') break; column=column*26+(c-'A'+1); } column--; }
                cell=new Cell(); cell.type=a.getValue("t"); value=new StringBuilder();
            }
            if(local.equals("v")) inValue=true;
            if(local.equals("t")) inText=true;
            if(local.equals("f") && cell!=null) cell.formula=true;
        }
        public void characters(char[] c,int start,int length) { if(cell!=null && (inValue || inText)) value.append(c,start,length); }
        public void endElement(String uri,String local,String q) throws SAXException {
            if(local.equals("v")) inValue=false;
            if(local.equals("t")) inText=false;
            if(local.equals("c") && cell!=null) {
                cell.value=value.toString();
                if("s".equals(cell.type)) { try { cell.value=strings.get(Integer.parseInt(cell.value)); } catch(Exception e) { throw new SAXException("공유 문자열 참조가 올바르지 않아요."); } }
                if(column<100) cells.put(column,cell);
                cell=null;
            }
            if(local.equals("row")) finishRow();
        }
        private void finishRow() throws SAXException {
            boolean blank=true; for(Cell c:cells.values()) if(!c.value.trim().isEmpty() || c.formula) blank=false;
            if(blank) return;
            if(!hasHeader) {
                for(Map.Entry<Integer,Cell> item:cells.entrySet()) {
                    String label=header(item.getValue().value);
                    if(label!=null) { if(headers.put(label,item.getKey())!=null) throw new SAXException("중복된 열 제목이 있어요: "+label); }
                }
                if(!headers.containsKey("날짜") || !headers.containsKey("구분") || !headers.containsKey("금액")) throw new SAXException("첫 행에 날짜, 구분, 금액 열이 필요해요. 앱의 입력 양식을 사용해 주세요.");
                hasHeader=true; return;
            }
            if(++rows>Ledger.MAX_IMPORT_ROWS) throw new SAXException("최대 10,000건씩 나누어 가져와 주세요.");
            try {
                String date=get("날짜");
                if(date.matches("[+-]?\\d+(\\.\\d+)?" ) && !date.matches("\\d{8}")) {
                    long serial=new BigDecimal(date).setScale(0,java.math.RoundingMode.FLOOR).longValueExact();
                    if(serial<0 || (!date1904 && serial==60)) throw new IllegalArgumentException("올바르지 않은 엑셀 날짜예요.");
                    LocalDate converted=date1904?LocalDate.of(1904,1,1).plusDays(serial):LocalDate.of(1899,12,31).plusDays(serial>60?serial-1:serial);
                    date=converted.toString();
                }
                result.entries.add(new Ledger.Entry(get("고유ID"),date,get("구분"),Ledger.parseAmount(get("금액")),get("분류"),get("메모"),row));
            } catch(Exception e) {
                result.invalidRows++;
                if(result.errors.size()<50) result.errors.add(row+"행: "+(e.getMessage()==null?"셀 값을 확인하세요.":e.getMessage()));
            }
        }
        private String get(String name) {
            Integer c=headers.get(name); Cell found=c==null?null:cells.get(c);
            if(found==null) return "";
            if(found.formula) throw new IllegalArgumentException(name+"의 수식은 값으로 붙여 넣어 주세요.");
            if("e".equals(found.type)) throw new IllegalArgumentException(name+"에 엑셀 오류 값이 있어요.");
            return found.value;
        }
        private static String header(String text) {
            String s=text.trim().replace(" ","").replace("\uFEFF","").toLowerCase(Locale.ROOT);
            if(s.equals("날짜") || s.equals("date")) return "날짜";
            if(s.equals("구분") || s.equals("유형") || s.equals("type")) return "구분";
            if(s.equals("금액") || s.equals("amount")) return "금액";
            if(s.equals("분류") || s.equals("카테고리") || s.equals("category")) return "분류";
            if(s.equals("메모") || s.equals("내용") || s.equals("memo")) return "메모";
            if(s.equals("고유id") || s.equals("id")) return "고유ID";
            return null;
        }
    }
    private XlsxIO() {}
}
