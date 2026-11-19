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
    private static final String[] BACKUP_HEADERS = {"날짜", "내역", "상세 분류", "금액", "돈의 성격", "고유ID"};
    private static final String STYLES = "<?xml version=\"1.0\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><numFmts count=\"4\"><numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd\"/><numFmt numFmtId=\"165\" formatCode=\"#,##0\"/><numFmt numFmtId=\"166\" formatCode=\"+#,##0;-#,##0;0\"/><numFmt numFmtId=\"167\" formatCode=\"0.0%\"/></numFmts><fonts count=\"3\"><font><sz val=\"11\"/><name val=\"맑은 고딕\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"맑은 고딕\"/></font><font><b/><sz val=\"11\"/><name val=\"맑은 고딕\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF19765B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"7\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"167\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
    public static final class ReadResult {
        public final List<Ledger.Entry> entries = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public int invalidRows;
    }
    public static void write(OutputStream output, List<Ledger.Entry> input, boolean template) throws IOException {
        List<Ledger.Entry> entries = new ArrayList<>(input);
        entries.sort(Comparator.comparing((Ledger.Entry e) -> e.date).thenComparing(e -> e.id));
        if(entries.size()>Ledger.MAX_IMPORT_ROWS) throw new IOException("한 파일에 최대 10,000건을 저장할 수 있어요. 기간을 나누어 백업해 주세요.");
        String[] names=template?new String[]{"내역","사용 안내","입력예시"}:new String[]{"요약","내역","사용 안내"};
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            StringBuilder types=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            StringBuilder book=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\""+NS+"\" xmlns:r=\""+REL+"\">"+(template?"<bookViews><workbookView activeTab=\"2\"/></bookViews>":"")+"<sheets>");
            StringBuilder rels=new StringBuilder("<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for(int i=0;i<names.length;i++) {
                int n=i+1;
                types.append("<Override PartName=\"/xl/worksheets/sheet").append(n).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                book.append("<sheet name=\"").append(names[i]).append("\" sheetId=\"").append(n).append("\" r:id=\"rId").append(n).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(n).append("\" Type=\"").append(REL).append("/worksheet\" Target=\"worksheets/sheet").append(n).append(".xml\"/>");
            }
            rels.append("<Relationship Id=\"rId").append(names.length+1).append("\" Type=\"").append(REL).append("/styles\" Target=\"styles.xml\"/>");
            put(zip,"[Content_Types].xml",types.append("</Types>").toString());
            put(zip,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+REL+"/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip,"xl/workbook.xml",book.append("</sheets><calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/></workbook>").toString());
            put(zip,"xl/_rels/workbook.xml.rels",rels.append("</Relationships>").toString());
            put(zip,"xl/styles.xml",STYLES);
            if(!template) put(zip,"xl/worksheets/sheet1.xml",summarySheet(entries));
            put(zip,"xl/worksheets/sheet"+(template?1:2)+".xml",detailSheet(entries,template));
            if(template) put(zip,"xl/worksheets/sheet3.xml",exampleSheet());
            put(zip,"xl/worksheets/sheet"+(template?2:3)+".xml",guideSheet(template));
        }
    }
    private static String detailSheet(List<Ledger.Entry> entries,boolean template) {
        String[] headers=BACKUP_HEADERS;
        int[] widths={16,52,24,22,18,40};
        StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+NS+"\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for(int c=0;c<widths.length;c++) sheet.append("<col min=\"").append(c+1).append("\" max=\"").append(c+1).append("\" width=\"").append(widths[c]).append("\" customWidth=\"1\"/>");
        sheet.append("</cols><sheetData><row r=\"1\" ht=\"28\" customHeight=\"1\">");
        for(int c=0;c<headers.length;c++) sheet.append(textCell(col(c)+"1",headers[c],1));
        sheet.append("</row>");int r=2;
        for(Ledger.Entry entry:entries) {
            sheet.append("<row r=\"").append(r).append("\">").append(numberCell("A"+r,Long.toString(excelSerial(LocalDate.parse(entry.date))),2));
            sheet.append(textCell("B"+r,entry.memo,0)).append(textCell("C"+r,entry.category,0)).append(numberCell("D"+r,Long.toString(template?entry.amount:TransactionType.signedAmount(entry)),template?3:4));
            sheet.append(textCell("E"+r,TransactionType.of(entry),0)).append(textCell("F"+r,entry.id,0));
            sheet.append("</row>");r++;
        }
        sheet.append("</sheetData><autoFilter ref=\"A1:").append(col(headers.length-1)).append(Math.max(1,r-1)).append("\"/>");
        sheet.append("<dataValidations count=\"1\"><dataValidation type=\"list\" allowBlank=\"0\" showDropDown=\"0\" showInputMessage=\"1\" promptTitle=\"돈의 성격 예시\" prompt=\"수입: 월급·이자 / 지출: 식비·교통 / 저축: 예·적금 / 투자: 주식·ETF. 저축회수: 기존 저축 원금 반환. 금액은 모두 양수로 입력하세요.\" errorStyle=\"stop\" showErrorMessage=\"1\" errorTitle=\"돈의 성격 선택\" error=\"수입, 지출, 저축, 투자, 저축회수 중 하나를 선택하세요.\" sqref=\"E2:E10001\"><formula1>&quot;수입,지출,저축,투자,저축회수&quot;</formula1></dataValidation></dataValidations>");
        return sheet.append("</worksheet>").toString();
    }
    private static String summarySheet(List<Ledger.Entry> entries) {
        long[] totals=Ledger.totals(entries);int end=Math.max(2,entries.size()+1);
        String period=entries.isEmpty()?"기록 없음":entries.get(0).date+" ~ "+entries.get(entries.size()-1).date;
        StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+NS+"\"><cols><col min=\"1\" max=\"1\" width=\"28\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"62\" customWidth=\"1\"/></cols><sheetData>");
        sheet.append("<row r=\"1\">").append(textCell("A1","차곡가계부 요약",6)).append("</row><row r=\"2\">").append(textCell("A2","기간",6)).append(textCell("B2",period,0)).append("</row>");
        for(int i=0;i<Ledger.TYPES.length;i++) {
            int r=3+i;
            String formula=(i==0?"":"-")+"SUMIFS('내역'!$D$2:$D$"+end+",'내역'!$E$2:$E$"+end+",\""+Ledger.TYPES[i]+"\")"+(i==2?"-SUMIFS('내역'!$D$2:$D$"+end+",'내역'!$E$2:$E$"+end+",\"저축회수\")":"");
            sheet.append("<row r=\"").append(r).append("\">").append(textCell("A"+r,"총"+Ledger.TYPES[i],6)).append(formulaCell("B"+r,formula,Long.toString(totals[i]),3)).append("</row>");
        }
        String rate=totals[0]==0?"0":BigDecimal.valueOf(totals[2]).divide(BigDecimal.valueOf(totals[0]),java.math.MathContext.DECIMAL64).toPlainString();
        sheet.append("<row r=\"7\">").append(textCell("A7","남은 돈",6)).append(formulaCell("B7","B3-B4-B5-B6",Long.toString(Ledger.available(totals)),3)).append("</row>");
        sheet.append("<row r=\"8\">").append(textCell("A8","저축률",6)).append(formulaCell("B8","IF(B3>0,B5/B3,0)",rate,5)).append("</row>");
        long saved=Math.addExact(totals[2],totals[3]);
        sheet.append("<row r=\"9\">").append(textCell("A9","투자률",6)).append(formulaCell("B9","IF(B3>0,B6/B3,0)",ratio(totals[3],totals[0]),5)).append("</row>");
        sheet.append("<row r=\"10\">").append(textCell("A10","모은 돈 (저축+투자)",6)).append(formulaCell("B10","B5+B6",Long.toString(saved),3)).append("</row>");
        sheet.append("<row r=\"11\">").append(textCell("A11","모은 비율 (저축+투자)",6)).append(formulaCell("B11","IF(B3>0,B10/B3,0)",ratio(saved,totals[0]),5)).append("</row>");
        sheet.append("<row r=\"12\">").append(textCell("A12","지출률",6)).append(formulaCell("B12","IF(B3>0,B4/B3,0)",ratio(totals[1],totals[0]),5)).append("</row>");
        sheet.append("<row r=\"13\">").append(textCell("A13","남은 돈 비율",6)).append(formulaCell("B13","IF(B3>0,B7/B3,0)",ratio(Ledger.available(totals),totals[0]),5)).append("</row>");
        String[] notes={"돈의 성격: 수입·지출·저축·투자 / 상세 분류: 월급·식비·적금·주식 등", "저축과 투자, 이체는 지출 합계에 포함하지 않아요. 앱에는 이체 기록이 없어요.", "남은 돈 = 수입 − 지출 − 저축 − 투자. 모은 비율 = (저축 + 투자) ÷ 수입이에요.", "지출률 + 저축률 + 투자률 + 남은 돈 비율 = 100%예요. 수입이 없으면 비율은 0%로 표시해요.", "기존에 저장한 돈의 성격을 그대로 내보내며, 상세 분류로 자동 변경하지 않아요."};
        for(int i=0;i<notes.length;i++) sheet.append("<row r=\"").append(i+15).append("\">").append(textCell("A"+(i+15),notes[i],0)).append("</row>");
        return sheet.append("</sheetData></worksheet>").toString();
    }
    private static String ratio(long part,long income){return income<=0?"0":BigDecimal.valueOf(part).divide(BigDecimal.valueOf(income),java.math.MathContext.DECIMAL64).toPlainString();}
    private static String numberCell(String address,String value,int style) { return "<c r=\""+address+"\" s=\""+style+"\"><v>"+value+"</v></c>"; }
    private static String formulaCell(String address,String formula,String value,int style) { return "<c r=\""+address+"\" s=\""+style+"\"><f>"+escape(formula)+"</f><v>"+value+"</v></c>"; }
    private static String exampleSheet() {
        StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+NS+"\"><sheetViews><sheetView workbookViewId=\"0\" tabSelected=\"1\" showGridLines=\"0\"/></sheetViews><cols>");
        int[] widths={16,44,26,22,18,24};
        for(int c=0;c<widths.length;c++)sheet.append("<col min=\"").append(c+1).append("\" max=\"").append(c+1).append("\" width=\"").append(widths[c]).append("\" customWidth=\"1\"/>");
        sheet.append("</cols><sheetData><row r=\"1\" ht=\"30\" customHeight=\"1\">").append(textCell("A1","돈의 성격과 입력 예시",6)).append("</row><row r=\"2\" ht=\"28\" customHeight=\"1\">").append(textCell("A2","금액은 모두 양수로 입력하세요. 앱이 돈의 성격에 따라 수입·저축회수는 더하고 지출·저축·투자는 차감해요.",0)).append("</row><row r=\"3\" ht=\"28\" customHeight=\"1\">").append(textCell("A3","참고용 예시는 앱에 추가하지 않아요. 실제 기록은 ‘내역’ 시트의 2행부터 입력하세요.",0)).append("</row>");
        String[] headings={"돈의 성격","뜻","상세 분류 예","앱 반영"};
        sheet.append("<row r=\"4\" ht=\"28\" customHeight=\"1\">");for(int c=0;c<headings.length;c++)sheet.append(textCell(col(c)+"4",headings[c],1));sheet.append("</row>");
        String[][] meanings={{"수입","월급·상여금·이자처럼 받은 돈","월급, 상여금, 이자","더함 (+)"},{"지출","식사·교통·월세처럼 생활에 쓴 돈","식비, 교통, 월세","차감 (-)"},{"저축","예·적금·청약 등에 따로 모은 돈","적금, 예금, 청약","차감 (-)"},{"투자","주식·ETF·펀드 매수·납입에 쓴 돈","주식, ETF, 펀드","차감 (-)"},{"저축회수","기록한 저축 원금을 돌려받은 돈","기존 저축과 같은 분류","순저축 차감, 사용 가능 더함"}};
        for(int i=0;i<meanings.length;i++){int r=i+5;sheet.append("<row r=\"").append(r).append("\" ht=\"26\" customHeight=\"1\">");for(int c=0;c<4;c++)sheet.append(textCell(col(c)+r,meanings[i][c],0));sheet.append("</row>");}
        sheet.append("<row r=\"10\" ht=\"28\" customHeight=\"1\">").append(textCell("A10","입력 예시 9건 (참고용)",6)).append("</row><row r=\"11\" ht=\"28\" customHeight=\"1\">");for(int c=0;c<BACKUP_HEADERS.length;c++)sheet.append(textCell(col(c)+"11",BACKUP_HEADERS[c],1));sheet.append("</row>");
        String[][] samples={{"2026-11-01","11월 급여","월급","수입","3000000"},{"2026-11-02","예금 이자","이자","수입","5000"},{"2026-11-03","점심 식사","식비","지출","12500"},{"2026-11-04","버스 요금","교통","지출","1500"},{"2026-11-05","정기 적금 납입","적금","저축","700000"},{"2026-11-06","주택 청약 납입","청약","저축","100000"},{"2026-11-07","ETF 정기 매수","ETF","투자","400000"},{"2026-11-08","주식 매수","주식","투자","100000"},{"2026-11-09","기록한 적금 원금 회수","적금","저축회수","700000"}};
        for(int i=0;i<samples.length;i++){String[] sample=samples[i];int r=i+12;Ledger.Entry entry=new Ledger.Entry("",sample[0],sample[3],Long.parseLong(sample[4]),sample[2],sample[1],0);sheet.append("<row r=\"").append(r).append("\" ht=\"25\" customHeight=\"1\">").append(numberCell("A"+r,Long.toString(excelSerial(LocalDate.parse(entry.date))),2)).append(textCell("B"+r,entry.memo,0)).append(textCell("C"+r,entry.category,0)).append(numberCell("D"+r,Long.toString(entry.amount),3)).append(textCell("E"+r,entry.type,0)).append(textCell("F"+r,"",0)).append("</row>");}
        return sheet.append("<row r=\"21\" ht=\"28\" customHeight=\"1\">").append(textCell("A21","새 내역의 고유ID는 비워 두세요. 저축·투자는 지출 합계와 별도로 집계해요.",0)).append("</row></sheetData></worksheet>").toString();
    }
    private static String guideSheet(boolean template) {
        String[] notes={"차곡가계부 엑셀 사용 안내", "내역 시트의 첫 번째 행은 바꾸지 말고, 두 번째 행부터 입력하세요.",
            "돈의 성격과 상세 분류는 별도 열이에요. 예: 수입 / 월급, 투자 / 주식.",
            "E열 돈의 성격 셀을 선택한 뒤 드롭다운에서 수입·지출·저축·투자·저축회수 중 하나를 고르세요.",
            "수입: 월급·상여금·예금 이자처럼 받은 돈이에요. 사용 가능 금액에 더해요.",
            "지출: 식사·교통·월세처럼 생활에 쓴 돈이에요. 사용 가능 금액에서 차감해요.",
            "저축: 예·적금·청약 등에 따로 모은 돈이에요. 사용 가능 금액에서 차감해요.",
            "투자: 주식·ETF·펀드 매수·납입에 쓴 돈이에요. 사용 가능 금액에서 차감해요.",
            "저축회수: 이미 기록한 저축 원금을 돌려받을 때 선택하세요. 기존 저축과 같은 상세 분류로 입력하고 이자·지원금은 수입으로 따로 적으세요. 순저축에서 빼고 사용 가능 금액에 더해요.",
            template?"입력예시 시트에 뜻과 예시 9건이 있어요. 예시는 앱으로 가져오지 않아요.":"이 파일의 내역 시트에는 실제 백업 기록만 들어 있어요.",
            "날짜: YYYY-MM-DD 문자 또는 엑셀 날짜 셀. 분류와 메모는 생략할 수 있어요.",
            "금액은 모두 양수로 입력하세요. 앱이 돈의 성격에 따라 수입·저축회수는 더하고 지출·저축·투자는 차감해요.",
            "예전 지출·저축·투자의 음수 금액도 읽어요. 백업 파일은 유형별 부호를 자동 표시해요.",
            "금액 절댓값은 1~999,999,999,999원의 정수예요.",
            "새 내역의 고유ID는 비워 두세요. 백업 ID는 복원 시 중복·충돌을 확인하는 데 사용해요.",
            "ID가 없으면 날짜·돈의 성격·금액·상세 분류·메모가 모두 같은 내역을 중복으로 보아요.",
            "같은 ID로 내용을 바꾸면 충돌로 표시돼요. 기존 내역 수정은 앱에서 해 주세요.",
            "수식은 값으로 붙여 넣은 뒤 저장해 주세요. .xlsx만 지원해요.",
            "가져오기는 최대 10,000건. 오류가 있으면 전체를 추가하지 않으며 기존 내역은 지우지 않아요.",
            "사용 가능 금액 = 수입 − 지출 − 저축 − 투자. 저축과 투자는 지출 합계와 구분돼요.",
            "계좌 열이 있는 예전 파일도 읽지만 계좌 값은 가져오지 않아요. 이체 행은 가져올 수 없어요.",
            "구분·분류·메모 열의 예전 양식도 가져올 수 있어요. 예전 양식의 금액은 모두 양수예요.",
            "투자 금액은 매수·납입 등 나간 금액을 기록해요.",
            "기존 기록의 돈의 성격은 그대로 유지해요. 저축으로 저장한 주식도 자동으로 투자로 바꾸지 않아요.",
            "앱 삭제 시 휴대폰 내 기록도 삭제돼요. 백업 파일은 휴대폰 밖에도 보관해 주세요."};
        StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+NS+"\"><cols><col min=\"1\" max=\"1\" width=\"120\" customWidth=\"1\"/></cols><sheetData>");
        for(int i=0;i<notes.length;i++) sheet.append("<row r=\"").append(i+1).append("\" ht=\"28\" customHeight=\"1\">").append(textCell("A"+(i+1),notes[i],i==0?1:0)).append("</row>");
        return sheet.append("</sheetData></worksheet>").toString();
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
        int row, column, rows; boolean inValue,inText,hasHeader,signedAmounts; Cell cell; StringBuilder value;
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
                    String raw=cleanHeader(item.getValue().value);
                    if(raw.equals("거래유형") || raw.equals("돈의성격")) signedAmounts=true;
                    String label=header(raw);
                    if(label!=null) { if(headers.put(label,item.getKey())!=null) throw new SAXException("중복된 열 제목이 있어요: "+label); }
                }
                if(!headers.containsKey("날짜") || !headers.containsKey("구분") || !headers.containsKey("금액")) throw new SAXException("첫 행에 날짜, 돈의 성격(또는 구분), 금액 열이 필요해요. 앱의 입력 양식을 사용해 주세요.");
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
                String type=get("구분");
                if("이체".equals(type.trim())) throw new IllegalArgumentException("이체는 앱에 저장할 수 없어요. 해당 행을 제외해 주세요.");
                type=Ledger.normalizeType(type);
                long amount=signedAmounts?parseTypedAmount(get("금액"),type):Ledger.parseAmount(get("금액"));
                result.entries.add(new Ledger.Entry(get("고유ID"),date,type,amount,get("분류"),get("메모"),row));
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
        private static long parseTypedAmount(String value,String type) {
            String raw=value.trim().replace(",","");
            if(raw.endsWith("원")) raw=raw.substring(0,raw.length()-1).trim();
            BigDecimal signed;
            try { signed=new BigDecimal(raw); } catch(NumberFormatException ex) { throw new IllegalArgumentException("금액은 정수로 입력하세요."); }
            boolean income=TransactionType.INCOME.equals(type);
            if(income && signed.signum()<0)
                throw new IllegalArgumentException("수입 금액은 양수로 입력하세요.");
            return Ledger.parseAmount(signed.abs().toString());
        }
        private static String cleanHeader(String text) { return text.trim().replace(" ","").replace("\uFEFF","").toLowerCase(Locale.ROOT); }
        private static String header(String text) {
            String s=cleanHeader(text);
            if(s.equals("날짜") || s.equals("date")) return "날짜";
            if(s.equals("구분") || s.equals("유형") || s.equals("type") || s.equals("거래유형") || s.equals("돈의성격")) return "구분";
            if(s.equals("금액") || s.equals("amount")) return "금액";
            if(s.equals("분류") || s.equals("카테고리") || s.equals("category") || s.equals("상세분류")) return "분류";
            if(s.equals("메모") || s.equals("내용") || s.equals("memo") || s.equals("내역")) return "메모";
            if(s.equals("고유id") || s.equals("id")) return "고유ID";
            return null;
        }
    }
    private XlsxIO() {}
}
