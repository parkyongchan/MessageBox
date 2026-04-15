package com.ah.acr.messagebox.packet.security;

import android.util.Log;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.util.HashMap;

import javax.crypto.Cipher;

public class RsaUtil {

	//private static final String TAG = RsaUtil.class.getSimpleName();
    static final int KEY_SIZE = 2048;
	
	public RsaUtil() {
	}
	
	/**
	 * create Keypair
	 * @return HashMap
	 */
	public static HashMap<String, String> createKeypairAsString() {
		HashMap<String, String> stringKeypair = new HashMap<>();
		try {
			  SecureRandom secureRandom = new SecureRandom();
			  KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			  keyPairGenerator.initialize(KEY_SIZE, secureRandom);
			  KeyPair keyPair = keyPairGenerator.genKeyPair();
			  
			  PublicKey publicKey = keyPair.getPublic();
			  PrivateKey privateKey = keyPair.getPrivate();
			  
			  String strPublicKey = Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
			  String strPrivateKey = Base64.encodeToString(privateKey.getEncoded(), Base64.NO_WRAP);

			  stringKeypair.put("publicKey", strPublicKey);
			  stringKeypair.put("privateKey", strPrivateKey);
			  
		} catch (Exception e) {
			e.printStackTrace();
			//Log.d(TAG, "Error msg : " + e.getMessage());
		}
		
		return stringKeypair;
	}
	
	/**
	 * encode rsa
	 * @param plainData			- src data
	 * @param stringPublicKey	- rsa public key
	 * @return String
	 */
    public static String encode(String plainData, String stringPublicKey) {
    	
        String encryptedData = null;
        
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] bytePublicKey = Base64.decode(stringPublicKey.getBytes(), Base64.NO_WRAP);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytePublicKey);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] byteEncryptedData = cipher.doFinal(plainData.getBytes());
            encryptedData = Base64.encodeToString(byteEncryptedData, Base64.NO_WRAP);

        } catch (Exception e) {
            e.printStackTrace();
			//Log.d(TAG, "Error msg : " + e.getMessage());
        }
        
        return encryptedData;
    }
    
    /**
     * decode rsa
     * @param encryptedData		- enc src data
     * @param stringPrivateKey	- rsa private key
     * @return String
     */
    public static String decode(String encryptedData, String stringPrivateKey) {
        String decryptedData = null;
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] bytePrivateKey = Base64.decode(stringPrivateKey.getBytes(), Base64.NO_WRAP);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(bytePrivateKey);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] byteEncryptedData = Base64.decode(encryptedData.getBytes(), Base64.NO_WRAP);
            byte[] byteDecryptedData = cipher.doFinal(byteEncryptedData);
            decryptedData = new String(byteDecryptedData);

        } catch (Exception e) {
            e.printStackTrace();
			//Log.d(TAG, "Error msg : " + e.getMessage());
        }

        return decryptedData;
    }
    
    /**
	 * encode rsa binary
	 * @param plainData			- src data
	 * @param stringPublicKey	- rsa public key
	 * @return byte array
	 */
    public static byte[] encodeBinary(String plainData, String stringPublicKey) throws IllegalArgumentException, Exception {
    	
    	byte[] byteEncryptedData = null;
    	
        //try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            //byte[] bytePublicKey = Base64.getDecoder().decode(stringPublicKey.getBytes());
            byte[] bytePublicKey = Base64.decode(stringPublicKey.getBytes(), Base64.NO_WRAP);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytePublicKey);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byteEncryptedData = cipher.doFinal(plainData.getBytes("MS949"));

        return byteEncryptedData;
    }
    
    /**
     * decode rsa binary
     * @param byteEncryptedData		- enc byte[] data
     * @param stringPrivateKey		- rsa private key
     * @return String
     */
    public static String decodeBinary(byte[] byteEncryptedData, String stringPrivateKey) throws IllegalArgumentException, Exception {
    	
        String decryptedData = null;

		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		byte[] bytePrivateKey = Base64.decode(stringPrivateKey.getBytes(), Base64.NO_WRAP);
		PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(bytePrivateKey);
		PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

		Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);

		byte[] byteDecryptedData = cipher.doFinal(byteEncryptedData);
		decryptedData = new String(byteDecryptedData, "MS949");

        return decryptedData;
    }
    
    /**
     * get Key data
     * @param fileName
     * @return String
     */
    public static String getKey(String fileName) {
    	String strKeyPem = "";
    	
    	ClassLoader classloader = Thread.currentThread().getContextClassLoader();
    	InputStream is = classloader.getResourceAsStream(fileName);
    	InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
    	
    	try {
    		//BufferedReader br = new BufferedReader(new FileReader(fileName));
    		BufferedReader br = new BufferedReader(streamReader);
    		String line;
    	
    		while ((line = br.readLine()) != null) {
    			strKeyPem += line + "\n";
    		}
    		br.close();
    	} catch( IOException ie ) {
    		ie.printStackTrace();
			//Log.d(TAG, "Error io msg : " + ie.getMessage());
    	} catch ( Exception e ) {
    		e.printStackTrace();
			//Log.d(TAG, "Error msg : " + e.getMessage());
    	}

		//Log.d(TAG, "strKeyPem : " + strKeyPem);
    	
    	return strKeyPem;
    }
    
    /**
     * get Private key file
     * @param key	- privatekey file path
     * @return String
     */
    public static String getPrivateKey(String key) {
    	String strPrivateKey = key;
    	strPrivateKey = strPrivateKey.replace("-----BEGIN PRIVATE KEY-----\n",  "");
    	strPrivateKey = strPrivateKey.replace("-----END PRIVATE KEY-----",  "");
		//Log.d(TAG, "strPrivateKey : " + strPrivateKey);
    	return strPrivateKey.replaceAll("\\r|\\n",  "");
    }
    
    /**
     * get Public key file
     * @param key	- publickey file path
     * @return String
     */
    public static String getPublicKey(String key) {
    	String strPubliceKey = key;
    	strPubliceKey = strPubliceKey.replace("-----BEGIN PUBLIC KEY-----\n",  "");
    	strPubliceKey = strPubliceKey.replace("-----END PUBLIC KEY-----",  "");
		//Log.d(TAG, "strPubliceKey : " + strPubliceKey);
    	return strPubliceKey.replaceAll("\\r|\\n",  "");
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
