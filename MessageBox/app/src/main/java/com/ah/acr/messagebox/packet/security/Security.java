package com.ah.acr.messagebox.packet.security;

import javax.crypto.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import android.util.Base64;

/**
 * Security-related methods. For a secure implementation, all of this code
 * should be implemented on a server that communicates with the
 * application on the device. For the sake of simplicity and clarity of this
 * example, this code is included here and is executed on the device. If you
 * must verify the purchases on the phone, you should obfuscate this code to
 * make it harder for an attacker to replace the code with stubs that treat all
 * purchases as verified.
 */
public class Security {

    private static final int DEFAULT_KEY_SIZE = 2048;

    private static final String KEY_FACTORY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String CHARSET = "UTF-8";

    
    public static String readFile(String fileName) {
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
    	} catch ( Exception e ) {
    		e.printStackTrace();
    	}
    	
    	strKeyPem = Security.fileReplace(strKeyPem);
    	return strKeyPem;
    }
    
    public static String fileReplace(String key) {
    	String strKey = key;
    	strKey = strKey.replace("-----BEGIN PRIVATE KEY-----",  "");
    	strKey = strKey.replace("-----END PRIVATE KEY-----",  "");
    	strKey = strKey.replace("-----BEGIN PUBLIC KEY-----",  "");
    	strKey = strKey.replace("-----END PUBLIC KEY-----",  "");
    	return strKey.replaceAll("\\r|\\n",  "");
    }
    
   
    
    /**
     * Generate key pair that public and private key by RSA
     * @return key pair
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_FACTORY_ALGORITHM);
        generator.initialize(DEFAULT_KEY_SIZE, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();
        return pair;
    }
    
    private static PublicKey generatePublicKey(byte[] encodedPublicKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(encodedPublicKey));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static PrivateKey generatePrivateKey(byte[] encodedPrivateKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedPrivateKey));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException(e);
        }
    }    

    /**
     * Encrypt plain text by RSA algorithm
     * @param plainText
     * @param encodedPublicKey
     * @return cipher text
     * @throws NoSuchAlgorithmException
     */
    public static String encrypt(String plainText, byte[] encodedPublicKey) throws NoSuchAlgorithmException {
        PublicKey publicKey = Security.generatePublicKey(encodedPublicKey);
        try {
            Cipher cipher = Cipher.getInstance(KEY_FACTORY_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] bytes = cipher.doFinal(plainText.getBytes(CHARSET));
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (NoSuchPaddingException | InvalidKeyException | UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypt cipher text by RSA algorithm
     * @param cipherText
     * @param encodedPrivateKey
     * @return plain text
     * @throws NoSuchAlgorithmException
     */
    public static String decrypt(String cipherText, byte[] encodedPrivateKey) throws NoSuchAlgorithmException {
        PrivateKey privateKey = Security.generatePrivateKey(encodedPrivateKey);
        try {
            byte[] bytes = Base64.decode(cipherText, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(KEY_FACTORY_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(bytes), CHARSET);
        } catch (NoSuchPaddingException | InvalidKeyException | UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create signature by signing
     * @param plainText the signed JSON string (signed, not encrypted)
     * @param encodedPrivateKey the base64-encoded private key to use for signing.
     * @return signature text
     */
    public static String sign(String plainText, byte[] encodedPrivateKey) {
        try {
            Signature privateSignature = Signature.getInstance(SIGNATURE_ALGORITHM);
            privateSignature.initSign(Security.generatePrivateKey(encodedPrivateKey));
            privateSignature.update(plainText.getBytes(CHARSET));
            byte[] signature = privateSignature.sign();
            return Base64.encodeToString(signature, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies that the data was signed with the given signature, and returns
     * the verified purchase. The data is in JSON format and signed
     * and product ID of the purchase.
     * @param plainText the signed JSON string (signed, not encrypted)
     * @param signature the signature for the data, signed with the private key
     * @param encodedPublicKey the base64-encoded public key to use for verifying.
     * @return result for verification
     */
    public static boolean verify(String plainText, String signature, byte[] encodedPublicKey) {
        PublicKey publicKey = Security.generatePublicKey(encodedPublicKey);
        return Security.verifySignarue(plainText, signature, publicKey);
    }

    private static boolean verifySignarue(String plainText, String signature, PublicKey publicKey) {
        Signature sig;
        try {
            sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(plainText.getBytes());
            if (!sig.verify(Base64.decode(signature, Base64.NO_WRAP)))
                throw new InvalidSignatureException("It was awesome! Signature hasn't be invalid");
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}

@SuppressWarnings("serial")
class InvalidSignatureException extends RuntimeException {
    InvalidSignatureException(String message) {
        super(message);
    }
}
