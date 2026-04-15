package com.ah.acr.messagebox.packet;

import android.util.Base64;
import android.util.Log;

import com.ah.acr.messagebox.packet.security.RsaUtil;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;

public class PacketProcUtil {
	//private static final String TAG = PacketProcUtil.class.getSimpleName();
	
	public final static int HEADER_LENGTH 	= 14;
	public final static int BODY_LENGTH 	= 256;
	public final static int OPCODE_LENGTH	= 4;
	
	public PacketProcUtil() {
	}
	
	/**
	 * make encrypted header
	 * @param strHpass		- header password
	 * @param iOpcode		- opcode 0 : test mode, 1 : not enc text, 2 : not enc bin, 3 : enc text, 4 : enc bin
	 * @param iSid			- sender id. device 1 ~ 127 
	 * @param iTid			- target id. server 129 ~ 255
	 * @param iSenderType	- sender type. 0 : send from device, 1 : send from server
	 * @param iTargetType	- target type. 0 : receive from device, 1 : receive from server 
	 * @param iEnc			- encrypted : 0, not encrypted : 1
	 * @param iText			- text : 0, binary : 1
	 * @return byte array
	 */
	public static byte[] makeHeader(String strHpass, int iOpcode, int iSid, int iTid, int iSenderType, int iTargetType, int iEnc, int iText) {
		
		byte[] data = new byte[HEADER_LENGTH];
		byte[] srcHeader = new byte[8];
		
		byte[] salt = Pdkdf2Util.makeSalt();
		byte[] saltedKey = Pdkdf2Util.makeSaltedKey(salt, strHpass);
		
		SecureRandom secureRandom = new SecureRandom();
		srcHeader[0] = (byte) iOpcode;
		srcHeader[1] = (byte) iSid;
		srcHeader[2] = (byte) iTid;
		srcHeader[3] = (byte) iSenderType;
		srcHeader[4] = (byte) iTargetType;
		srcHeader[5] = (byte) iEnc;
		srcHeader[6] = (byte) iText;
		srcHeader[7] = (byte) secureRandom.nextInt();

		//Log.d(TAG, "saltedKey length : " + saltedKey.length);
		//Log.d(TAG, "saltedKey string : " + Pdkdf2Util.byteArrayToHexString(saltedKey));
		//Log.d(TAG, "srcHeader : " + Pdkdf2Util.byteArrayToHexString(srcHeader));
        
        if( iOpcode == 0 ) {	// not encrypted header
			//Log.d(TAG, "===== Not encrypted Header =====");
        	// make encHeader - 14 bytes
	        System.arraycopy(salt, 0, data, 0, salt.length);						// 0 ~ 3 : salt
	        System.arraycopy(srcHeader, 0, data, salt.length, srcHeader.length);	// 4 ~ 11 : srcHeader
        } else {
			//Log.d(TAG, "===== Encrypted Header =====");
	        String strXorHeader = Pdkdf2Util.makeXor(saltedKey, srcHeader);
			//Log.d(TAG, "strXorHeader : " + strXorHeader);
	        
	        byte[] encHeader = Pdkdf2Util.hexStringToByteArray(strXorHeader);
	        
	        // make encHeader - 14 bytes
	        System.arraycopy(salt, 0, data, 0, salt.length);						// 0 ~ 3 : salt
	        System.arraycopy(encHeader, 0, data, salt.length, encHeader.length);	// 4 ~ 11 : enc_header
        }
        
        data[12] = (byte) secureRandom.nextInt();								// 12 : random
        data[13] = (byte) secureRandom.nextInt();								// 13 : random
        
		return data;
	}
	
	/**
	 * make encrypted body
	 * @param strMsg		- raw_data
	 * @param publicKey		- rsa public key
	 * @param iOpcode		- header opcode
	 * @return byte array
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	/**
	 * make encrypted body  
	 * opcode 0 : not encrypt
	 * opcode 1 : not encrypt text
	 * opcode 2 : not encrypt binary(hex)
	 * opcode 3 : rsa encrypt text
	 * opcode 4 : rsa encrypt binary(hex)
	 */
	public static byte[] makeBody(String strMsg, String publicKey, int iOpcode) throws IllegalArgumentException, Exception {

		byte[] data = null;//new byte[BODY_LENGTH];
		//Log.d(TAG, "Opcode : " + iOpcode);
		
		if( iOpcode == 0 ) {
			data = "Test Message !!".getBytes("MS949");
		} else if( iOpcode == 1 ) {		// Max length : 256 bytes. Text
			data = strMsg.getBytes("MS949");
		} else if( iOpcode == 2 ) {		// Max length : 512 bytes. Binary( Hex )
			data = strMsg.getBytes("MS949");
			//data = Pdkdf2Util.hexStringToByteArray(strMsg);
		} else if( iOpcode == 3 ) {		// Encrypted Text
			data = RsaUtil.encodeBinary(strMsg, publicKey);
		} else if( iOpcode == 4 ) {		// Encrypted Binary( Hex )
			data = RsaUtil.encodeBinary(strMsg, publicKey);
		} else {
			data = "MAKE MESSAGE ERROR".getBytes("MS949");
		}
		//Log.d(TAG, "opcode [" + iOpcode + "] , body data length : " + data.length);
		
		return data;
	}
	
	/**
	 * make packet data 
	 * return HexString 
	 */
	/**
	 * make packet data 
	 * @param header	- byte array header
	 * @param body		- byte array body
	 * @return String
	 */
	public static String makePacket(byte[] header, byte[] body) {
		
		int length = header.length + body.length;		
		byte[] data = new byte[length];
		//Log.d(TAG, "packet length : " + length);
		System.arraycopy(header, 0, data, 0, header.length);
		System.arraycopy(body, 0, data, header.length, body.length);
		String strData = RsaUtil.byteArrayToHexString(data);
		//Log.d(TAG, "Packet String : " + strData);
		
		return strData;
	}
	
	/**
	 * get Body Data
	 * @param rawData		- raw data
	 * @param iOpcode		- opcode
	 * @param privateKey	- rsa private key (*.pem)
	 * @return	BodyInfo class
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public static BodyInfo getBodyInfo(String rawData, int iOpcode, String privateKey) throws IllegalArgumentException, Exception {
		BodyInfo bodyInfo = new BodyInfo();
		String strBodyMsg = "";


		byte[] body = Base64.decode(rawData, Base64.NO_WRAP);
//		byte[] data = RsaUtil.hexStringToByteArray(rawData);
//		//byte[] header = new byte[14];
//		byte[] body = new byte[data.length - HEADER_LENGTH];
//		//System.arraycopy(data, 0, header, 0, header.length);
//		System.arraycopy(data, HEADER_LENGTH, body, 0, body.length);

		if( iOpcode == 0 ) {
			strBodyMsg = new String(body, "MS949");
		} else if( iOpcode == 1 ) {
			strBodyMsg = new String(body, "MS949");
		} else if( iOpcode == 2 ) {
			strBodyMsg = new String(body, "MS949");
		} else if( iOpcode == 3 ) {
			strBodyMsg = RsaUtil.decodeBinary(body, privateKey);
		} else if( iOpcode == 4 ) {
			strBodyMsg = RsaUtil.decodeBinary(body, privateKey);
		}
		
		bodyInfo.setText(strBodyMsg);
		bodyInfo.setBinary(rawData);

		//Log.d(TAG, "opcode : " + iOpcode);
		//Log.d(TAG, "rawdata Msg : " + strBodyMsg);

		return bodyInfo;
	}
	
	/**
	 * get Header infomation
	 * @param hPass		- header password
	 * @param rawData	- raw_data
	 * @return HeaderInfo class
	 */
	public static HeaderInfo getHeaderInfo(String hPass, String rawData) {
		String strHeader = "";
		HeaderInfo headerInfo = new HeaderInfo();
		
		byte[] data = Base64.decode(rawData, Base64.NO_WRAP);
		// byte[] data = RsaUtil.hexStringToByteArray(rawData);

		byte[] header = new byte[8];
		byte[] salt = new byte[4];

		System.arraycopy(data, 0, salt, 0, salt.length);
		System.arraycopy(data, salt.length, header, 0, header.length);
		
		String strSalt = RsaUtil.byteArrayToHexString(salt);
		//Log.d(TAG, "strSalt : " + strSalt);
		
		byte[] saltedKey = Pdkdf2Util.makeSaltedKey(salt, hPass);

		//Log.d(TAG, "saltedKey length : " + saltedKey.length);
		//Log.d(TAG, "saltedKey string : " + Pdkdf2Util.byteArrayToHexString(saltedKey));
        
        int iOpcode = (int) (header[0] & 0xFF);
		//Log.d(TAG, "iOpcode : " + iOpcode);
        
        if( iOpcode == 0 ) {
        	strHeader = Pdkdf2Util.byteArrayToHexString(header);
			//Log.d(TAG, "Header HexString : " + strHeader);
			//Log.d(TAG, "Header NewString : " + new String(header));
        } else {
        	strHeader = Pdkdf2Util.makeXor(saltedKey, header);
			//Log.d(TAG, "strHeader : " + strHeader);
        }

		//Log.d(TAG, "decXor header String : " + strHeader);
        
        byte[] decHeader = Pdkdf2Util.hexStringToByteArray(strHeader);
        
        iOpcode = (int) (decHeader[0] & 0xFF);
        int iSid = (int) (decHeader[1] & 0xFF);
        int iTid = (int) (decHeader[2] & 0xFF);
        int iSenderType = (int) (decHeader[3] & 0xFF);
        int iTargetType = (int) (decHeader[4] & 0xFF);
        int iEnc = (int) (decHeader[5] & 0xFF);
        int iText = (int) (decHeader[6] & 0xFF);
        int iRnd = (int) (decHeader[7] & 0xFF);
		//Log.d(TAG, "iOpcode : " + iOpcode);
		//Log.d(TAG, "iSid : " + iSid);
		//Log.d(TAG, "iTid : " + iTid);
		//Log.d(TAG, "iSenderType : " + iSenderType);
		//Log.d(TAG, "iTargetType : " + iTargetType);
		//Log.d(TAG, "iEnc : " + iEnc);
		//Log.d(TAG, "iText : " + iText);
		//Log.d(TAG, "iRnd : " + iRnd);
        
        headerInfo.setOpcode(iOpcode);
        headerInfo.setSenderid(iSid);
        headerInfo.setTargetid(iTid);
        headerInfo.setSendertype(iSenderType);
        headerInfo.setTargettype(iTargetType);
        headerInfo.setEncrypted(iEnc);
        headerInfo.setText(iText);
        
        // Opcode Check - 0, 1, 2, 3, 4
        if(iOpcode > OPCODE_LENGTH || iOpcode < 0) {
        	headerInfo.setIserror(true);
        }
        
        // Sender type check - 0, 1
        if( iSenderType > 1 ) {
        	headerInfo.setIserror(true);
        }
        
        // Target type check - 0, 1
        if( iTargetType > 1 ) {
        	headerInfo.setIserror(true);
        }
        
        // Encrypt check - 0, 1
        if( iEnc > 1 ) {
        	headerInfo.setIserror(true);
        }
        
        // Text check - 0, 1
        if( iText > 1 ) {
        	headerInfo.setIserror(true);
        }
        
        headerInfo.setBinary(strHeader);
		
		return headerInfo;
	}
	
	/**
	 * get Packet(Header + Body) Infomation
	 * @param hPass			- header password
	 * @param rawData		- raw data
	 * @param privateKey	- rsa private key
	 * @return HashMap
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public static HashMap<String, Object> getPacketInfo(String hPass, String rawData, String privateKey) throws IllegalArgumentException, Exception {
		String strHeader = "";
		String strBodyText = ""; 
		String strBodyBinary = "";
		
		HashMap<String, Object> packet = new HashMap<String, Object>();
		
		HeaderInfo headerInfo = null;
		BodyInfo bodyInfo = null;
		
		headerInfo = getHeaderInfo(hPass, rawData);
		
		int iOpcode = headerInfo.getOpcode();
		//Log.d(TAG, "headerInfo iOpcode : " + iOpcode);
		
		if( !headerInfo.isIserror() ) {
			bodyInfo = getBodyInfo(rawData, iOpcode, privateKey);
			strBodyText = bodyInfo.getText();
			strBodyBinary = bodyInfo.getBinary();
			strHeader = headerInfo.getBinary();
		} else {
			strHeader = "error";
		}
		
		packet.put("header", headerInfo);
		packet.put("body", bodyInfo);

		//Log.d(TAG, "strHeader =======> " + strHeader);
		//Log.d(TAG, "strBodyText =======> " + strBodyText);
		//Log.d(TAG, "strBodyBinary =======> " + strBodyBinary);
		
		return packet;
	}
}
