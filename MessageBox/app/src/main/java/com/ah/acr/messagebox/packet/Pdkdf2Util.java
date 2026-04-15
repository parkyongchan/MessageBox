package com.ah.acr.messagebox.packet;

import android.util.Log;
import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


public class Pdkdf2Util {
	public static byte[] makeSalt() {
		byte[] salt = new byte[4];
		SecureRandom secureRandom = new SecureRandom();
		secureRandom.nextBytes(salt);
		return salt;
	}

	public static byte[] makeSaltedKey(byte[] salt, String password) {
		byte[] hash = null;
		try {
			KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 64);
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			hash = factory.generateSecret(spec).getEncoded();
		} catch (Exception e) {
			Log.e("ERROR", "Error msg : " + e.getMessage());
		}
		return hash;
	}

	public static String makeXor(byte[] saltedKey, byte[] headerSrc) {
		byte[] encode = new byte[headerSrc.length];
		int i = 0;
		for (byte b : saltedKey)
			encode[i] = (byte) (b ^ headerSrc[i++]);
		return byteArrayToHexString(encode);
	}

	public static byte[] makeXorArray(byte[] saltedKey, byte[] headerSrc) {
		byte[] encode = new byte[headerSrc.length];
		int i = 0;
		for (byte b : saltedKey)
			encode[i] = (byte) (b ^ headerSrc[i++]);
		return encode;
	}

	public static byte[] hexStringToByteArray(String str) {
    	int len = str.length();
    	byte[] data = new byte[len / 2];
    	for( int i = 0; i < len; i += 2) {
    		data[i / 2] = (byte) ((Character.digit(str.charAt(i),  16) << 4)
    							+ Character.digit(str.charAt(i+1), 16));
    	}
    	return data;
    }
    
    public static String byteArrayToHexString(byte[] bytes) {
    	StringBuilder sb = new StringBuilder();
    	for (byte b : bytes) {
    		sb.append(String.format("%02X",  b&0xff));
    	}
    	return sb.toString();
    }
}
