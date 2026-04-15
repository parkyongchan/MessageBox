package com.ah.acr.messagebox.data;

public class DeviceInfo {

    private String serialNum;
    private String budaeNum;
    private String imei;
    private String version;
    private int voltage;
    private int signalVal;
    private boolean pwChanged;
    private boolean sosStarted;
    private boolean trackingMode;

    public DeviceInfo() {
    }

    public String getSerialNum() {
        return serialNum;
    }

    public void setSerialNum(String serialNum) {
        this.serialNum = serialNum;
    }

    public String getBudaeNum() { return budaeNum;}
    public void setBudaeNum(String budaeNum) {this.budaeNum = budaeNum;}
    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getVoltage() {return voltage;}
    public void setVoltage(int voltage) {this.voltage = voltage; }
    public int getSignalVal() {return signalVal;}
    public void setSignalVal(int signalVal) { this.signalVal = signalVal;}
    public boolean isSosStarted() {return sosStarted;}
    public void setSosStarted(boolean sosStarted) {this.sosStarted = sosStarted;}

    public boolean isPwChanged() {return pwChanged;}
    public void setPwChanged(boolean isPw) {this.pwChanged = isPw;}

    public boolean isTrackingMode() {return trackingMode;}
    public void setTrackingMode(boolean trackingMode) {this.trackingMode = trackingMode;}




    @Override
    public String toString() {
        return "DeviceInfo{" +
                "serialNum='" + serialNum + '\'' +
                ", unit='" + budaeNum + '\'' +
                ", imei='" + imei + '\'' +
                ", version='" + version + '\'' +
                ", voltage='" + voltage + '\'' +
                ", siginalVal='" + signalVal + '\'' +
                ", pwChanged='" + pwChanged + '\'' +
                ", sosStarted='" + sosStarted + '\'' +
                ", trackingMode='" + trackingMode + '\'' +
                '}';
    }
}
