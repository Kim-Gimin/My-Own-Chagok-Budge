package kr.chagok.ledger;

/** Draft amount; applying it never saves a transaction by itself. */
final class AmountKeypad {
    private String digits;
    AmountKeypad(String value){digits=value!=null&&value.matches("[0-9]{1,12}")?normalize(value):"0";}
    String digits(){return digits;}
    long value(){return Long.parseLong(digits);}
    void clear(){digits="0";}
    boolean press(String key){
        if("⌫".equals(key)){digits=digits.length()>1?digits.substring(0,digits.length()-1):"0";return true;}
        if(key==null||!key.matches("[0-9]|00"))return false;
        String next=normalize(("0".equals(digits)?"":digits)+key);if(next.length()>12)return false;digits=next;return true;
    }
    private static String normalize(String value){return value.replaceFirst("^0+(?!$)","");}
}
