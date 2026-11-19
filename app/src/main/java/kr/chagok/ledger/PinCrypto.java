package kr.chagok.ledger;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Random salt and PBKDF2 verification; the PIN itself is never saved. */
final class PinCrypto {
    static final int ITERATIONS=120000;
    static String create(String pin) {
        if(pin==null || !pin.matches("[0-9]{4,12}"))throw new IllegalArgumentException("비밀번호는 숫자 4~12자리로 입력해 주세요.");
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        try{return ITERATIONS+":"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(pin,salt,ITERATIONS));}catch(GeneralSecurityException e){throw new IllegalStateException("비밀번호를 저장하지 못했어요.",e);}
    }
    static boolean verify(String pin,String encoded){
        if(pin==null || encoded==null)return false;
        try{String[] parts=encoded.split(":");if(parts.length!=3)return false;int iterations=Integer.parseInt(parts[0]);if(iterations<10000 || iterations>1000000)return false;
            byte[] salt=Base64.getDecoder().decode(parts[1]),expected=Base64.getDecoder().decode(parts[2]);if(salt.length!=16 || expected.length!=32)return false;
            return MessageDigest.isEqual(expected,derive(pin,salt,iterations));
        }catch(Exception e){return false;}
    }
    private static byte[] derive(String pin,byte[] salt,int iterations)throws GeneralSecurityException{
        PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,iterations,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private PinCrypto(){}
}
