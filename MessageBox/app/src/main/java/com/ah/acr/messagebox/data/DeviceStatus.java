package com.ah.acr.messagebox.data;

public class DeviceStatus {
    private int battery;
    private int inBox;
    private int outBox;
    private int signal;

    private String gpsTime;
    private String gpsLat;
    private String gpsLng;

    private boolean trackingMode;
    private boolean sosMode;

    public DeviceStatus() {
    }

    public int getBattery() {
        return battery;
    }

    int map(int x, int in_min, int in_max, int out_min, int out_max){
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }


    public void setBattery(int bat) {
        int min = 3720;
        int max = 4100;
        //value = ((max - min) * percentage) + min
        int val =  map(bat, min, max, 1, 100);
        if (val > 100) val = 100;
        if (val <= 0 ) val = 0;
        this.battery = val;
        //this.battery =  (int)( ((battery - min) / (max - min)) * 100 );
    }

    public int getInBox() {
        return inBox;
    }

    public void setInBox(int inBox) {
        this.inBox = inBox;
    }

    public int getOutBox() {
        return outBox;
    }

    public void setOutBox(int outBox) {
        this.outBox = outBox;
    }

    public int getSignal() {
        return signal;
    }

    public void setSignal(int signal) {
        this.signal = signal;
    }

    public String getGpsTime() {return gpsTime;};
    public void setGpsTime(String gpsTime) { this.gpsTime = gpsTime;};
    public String getGpsLat() {return gpsLat;};
    public void setGpsLat(String gpsLat) {this.gpsLat = gpsLat;};
    public String getGpsLng() {return gpsLng;};
    public void setGpsLng(String gpsLng) {this.gpsLng = gpsLng;};

    public void setTrackingMode(boolean mode) {this.trackingMode = mode;};
    public void setSosMode(boolean mode) {this.sosMode = mode;};
    public boolean isTrackingMode(){return trackingMode;};
    public boolean isSosMode(){return sosMode;};

    @Override
    public String toString() {
        return "DeviceStatus{" +
                "battery='" + battery + '\'' +
                ", inBox=" + inBox +
                ", outBox=" + outBox +
                ", signal=" + signal +
                ", gpsTime=" + gpsTime +
                ", gpsLat=" + gpsLat +
                ", gpsLng=" + gpsLng +
                ", trackingMode=" + trackingMode +
                ", sosMode=" + sosMode +
                '}';
    }
}
