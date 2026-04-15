package com.ah.acr.messagebox.packet;

public class BodyInfo {

	private String text;
	private String binary;
	
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getBinary() {
		return binary;
	}
	public void setBinary(String binary) {
		this.binary = binary;
	}
	@Override
	public String toString() {
		return "BodyInfo [text=" + text + ", binary=" + binary + "]";
	}
	
}
