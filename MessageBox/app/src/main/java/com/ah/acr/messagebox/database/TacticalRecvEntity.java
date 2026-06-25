package com.ah.acr.messagebox.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 수신/송신 전술 데이터 1건 (원문 보관).
 * payload(직렬화 원문)를 그대로 저장하고, 표시할 때 TacticalParser로 파싱한다.
 */
@Entity(tableName = "tactical_recv")
public class TacticalRecvEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "code_num")
    private String codeNum;          // 발신/수신 경로 (연락처 코드)

    @ColumnInfo(name = "from_imei")
    private String fromImei;         // 발신자 IMEI (빈값=관제센터)

    @ColumnInfo(name = "payload")
    private String payload;          // 직렬화 원문 (FROM:..;M:..;N:..)

    @ColumnInfo(name = "is_send")
    private boolean isSend;          // true=내가 보낸 것, false=수신

    @ColumnInfo(name = "recv_at")
    private long recvAt;             // 수신/저장 시각(ms)

    public TacticalRecvEntity() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCodeNum() { return codeNum; }
    public void setCodeNum(String codeNum) { this.codeNum = codeNum; }
    public String getFromImei() { return fromImei; }
    public void setFromImei(String fromImei) { this.fromImei = fromImei; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public boolean isSend() { return isSend; }
    public void setSend(boolean send) { isSend = send; }
    public long getRecvAt() { return recvAt; }
    public void setRecvAt(long recvAt) { this.recvAt = recvAt; }
}