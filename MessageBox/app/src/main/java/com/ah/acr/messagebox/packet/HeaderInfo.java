package com.ah.acr.messagebox.packet;

public class HeaderInfo {

	private int opcode;
	private int senderid;
	private int targetid;
	private int sendertype;
	private int targettype;
	private int encrypted;
	private int text;
	private boolean iserror;
	private String binary;
	
	public int getOpcode() {
		return opcode;
	}
	public void setOpcode(int opcode) {
		this.opcode = opcode;
	}
	public int getSenderid() {
		return senderid;
	}
	public void setSenderid(int senderid) {
		this.senderid = senderid;
	}
	public int getTargetid() {
		return targetid;
	}
	public void setTargetid(int targetid) {
		this.targetid = targetid;
	}
	public int getSendertype() {
		return sendertype;
	}
	public void setSendertype(int sendertype) {
		this.sendertype = sendertype;
	}
	public int getTargettype() {
		return targettype;
	}
	public void setTargettype(int targettype) {
		this.targettype = targettype;
	}
	public int getEncrypted() {
		return encrypted;
	}
	public void setEncrypted(int encrypted) {
		this.encrypted = encrypted;
	}
	public int getText() {
		return text;
	}
	public void setText(int text) {
		this.text = text;
	}
	public boolean isIserror() {
		return iserror;
	}
	public void setIserror(boolean iserror) {
		this.iserror = iserror;
	}
	public String getBinary() {
		return binary;
	}
	public void setBinary(String binary) {
		this.binary = binary;
	}
	@Override
	public String toString() {
		return "HeaderInfo [opcode=" + opcode + ", senderid=" + senderid + ", targetid=" + targetid + ", sendertype="
				+ sendertype + ", targettype=" + targettype + ", encrypted=" + encrypted + ", text=" + text
				+ ", iserror=" + iserror + ", binary=" + binary + "]";
	}
	
	
}
