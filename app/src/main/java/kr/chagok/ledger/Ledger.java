package kr.chagok.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Android-independent validation, totals and import rules. All amounts are whole KRW. */
public final class Ledger {
    public static final long MAX_AMOUNT = 999_999_999_999L;
    public static final int MAX_IMPORT_ROWS = 10_000;
    public static final String[] TYPES = {"수입", "지출", "저축", "투자"};
    public static final class Entry {
        public final String id, date, type, category, memo;
        public final long amount;
        public final int sourceRow;
        public Entry(String id, String date, String type, long amount, String category, String memo, int row) {
            this.id = id == null ? "" : id.trim();
            this.date = normalizeDate(date);
            this.type = normalizeType(type);
            if (amount < 1 || amount > MAX_AMOUNT) throw new IllegalArgumentException("금액은 1~999,999,999,999원의 정수로 입력하세요.");
            this.amount = amount;
            this.category = category == null || category.trim().isEmpty() ? "기타" : category.trim();
            this.memo = memo == null ? "" : memo.trim();
            if (this.category.length() > 40) throw new IllegalArgumentException("분류는 40자까지 입력할 수 있어요.");
            if (this.memo.length() > 500) throw new IllegalArgumentException("메모는 500자까지 입력할 수 있어요.");
            if (this.id.length() > 128 || this.id.matches(".*[\\p{Cntrl}].*")) throw new IllegalArgumentException("고유ID 형식을 확인하세요.");
            this.sourceRow = row;
        }
        public Entry withId(String newId) { return new Entry(newId, date, type, amount, category, memo, sourceRow); }
        public String fingerprint() { return date + ":" + type + ":" + amount + ":" + category.length() + ":" + category + memo.length() + ":" + memo; }
        public boolean sameContent(Entry other) { return fingerprint().equals(other.fingerprint()); }
    }
    public static String normalizeDate(String value) {
        String text = value == null ? "" : value.trim();
        LocalDate date;
        try {
            if (text.indexOf('T') > 0) date = LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(text));
            else if (text.matches("\\d{8}")) date = LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
            else date = LocalDate.parse(text.replace('/', '-').replace('.', '-'), DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT));
        } catch (Exception ex) { throw new IllegalArgumentException("날짜는 YYYY-MM-DD 형식의 실제 날짜로 입력하세요."); }
        if (date.getYear() < 1900 || date.getYear() > 9999) throw new IllegalArgumentException("날짜는 1900~9999년 범위로 입력하세요.");
        return date.toString();
    }
    public static String normalizeType(String value) {
        String s = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (s.equals("수입") || s.equals("income")) return "수입";
        if (s.equals("지출") || s.equals("expense")) return "지출";
        if (s.equals("저축") || s.equals("saving") || s.equals("savings")) return "저축";
        if (s.equals("투자") || s.equals("investment") || s.equals("investing")) return "투자";
        throw new IllegalArgumentException("돈의 성격은 수입, 지출, 저축, 투자 중 하나로 입력하세요.");
    }
    public static long parseAmount(String value) {
        try {
            String raw = value == null ? "" : value.trim().replace(",", "");
            if (raw.endsWith("원")) raw = raw.substring(0, raw.length()-1).trim();
            long amount = new BigDecimal(raw).longValueExact();
            if (amount < 1 || amount > MAX_AMOUNT) throw new ArithmeticException();
            return amount;
        } catch (Exception ex) { throw new IllegalArgumentException("금액은 1~999,999,999,999원의 정수로 입력하세요."); }
    }
    public static long[] totals(List<Entry> entries) {
        long[] result = new long[TYPES.length];
        for (Entry e : entries) result[index(e.type)] = Math.addExact(result[index(e.type)], e.amount);
        return result;
    }
    public static int index(String type) {
        for (int i=0;i<TYPES.length;i++) if (TYPES[i].equals(type)) return i;
        throw new IllegalArgumentException("알 수 없는 돈의 성격이에요: "+type);
    }
    public static long available(long[] totals) {
        long balance=totals[0];
        for(int i=1;i<totals.length;i++) balance=Math.subtractExact(balance,totals[i]);
        return balance;
    }
    public static List<Entry> month(List<Entry> entries, YearMonth month) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.date.startsWith(month.toString())) out.add(e);
        return out;
    }
    public static final class ImportPlan {
        public final List<Entry> additions = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public int duplicates;
    }
    public static ImportPlan planImport(List<Entry> input, List<Entry> existing) {
        ImportPlan plan = new ImportPlan();
        Map<String, Entry> ids = new HashMap<>();
        Set<String> fingerprints = new HashSet<>();
        for (Entry e : existing) { ids.put(e.id, e); fingerprints.add(e.fingerprint()); }
        for (Entry e : input) {
            if (!e.id.isEmpty() && ids.containsKey(e.id)) {
                if (ids.get(e.id).sameContent(e)) plan.duplicates++;
                else plan.errors.add(e.sourceRow + "행: 같은 고유ID의 내용이 기존 내역과 달라요. 새 내역이라면 ID를 비워 주세요.");
            } else if (e.id.isEmpty() && fingerprints.contains(e.fingerprint())) plan.duplicates++;
            else {
                Entry add = e.id.isEmpty() ? e.withId(UUID.randomUUID().toString()) : e;
                plan.additions.add(add); ids.put(add.id, add); fingerprints.add(add.fingerprint());
            }
        }
        return plan;
    }
    private Ledger() {}
}
