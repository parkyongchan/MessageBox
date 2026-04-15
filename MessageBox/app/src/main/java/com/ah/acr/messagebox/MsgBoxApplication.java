package com.ah.acr.messagebox;

import android.app.Application;
import com.ah.acr.messagebox.packet.security.SharedUtil;

public class MsgBoxApplication extends Application {

    public static SharedUtil sharedUtil;

    @Override
    public void onCreate(){
        super.onCreate();
        this.sharedUtil = new SharedUtil(getApplicationContext());
    }


}
