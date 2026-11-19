package kr.chagok.ledger;

import java.util.Locale;

/** Export classification. The user's saved type always takes priority over keywords. */
public final class TransactionType {
    public static final String INCOME="수입", EXPENSE="지출", SAVING="저축", INVESTMENT="투자", TRANSFER="이체";
    private static final String[] OWNER_NAMES={};
    private static final String[] TRANSFER_KEYWORDS={"내 계좌","내계좌"};
    private static final String[] SAVING_KEYWORDS={"적금","예금","청약","CMA","저축"};
    private static final String[] INVESTMENT_KEYWORDS={"투자","주식","ETF","펀드","증권","채권","가상자산","암호화폐"};
    private static final String[] INCOME_KEYWORDS={"급여","월급","상여","이자","환급","캐시백"};

    public static String classify(String knownType,String text,boolean deposit) {
        if(TRANSFER.equals(knownType)) return TRANSFER;
        if(knownType!=null && !knownType.trim().isEmpty()) {
            try { return Ledger.normalizeType(knownType); } catch(IllegalArgumentException ignored) {}
        }
        String value=text==null?"":text.toLowerCase(Locale.ROOT);
        if(contains(value,INCOME_KEYWORDS)) return INCOME;
        if(contains(value,OWNER_NAMES) || contains(value,TRANSFER_KEYWORDS)) return TRANSFER;
        if(contains(value,INVESTMENT_KEYWORDS)) return INVESTMENT;
        if(contains(value,SAVING_KEYWORDS)) return SAVING;
        return deposit?INCOME:EXPENSE;
    }
    private static boolean contains(String text,String[] keywords) {
        for(String word:keywords) if(!word.isEmpty() && text.contains(word.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
    public static String of(Ledger.Entry entry) { return classify(entry.type,entry.memo+" "+entry.category,INCOME.equals(entry.type)); }
    public static long signedAmount(Ledger.Entry entry) { return INCOME.equals(of(entry))?entry.amount:-entry.amount; }
    private TransactionType() {}
}
