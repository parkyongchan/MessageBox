package com.ah.acr.messagebox.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ah.acr.messagebox.MsgBoxApplication;
import com.ah.acr.messagebox.packet.security.SharedUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class KeyViewModel  extends ViewModel {
    private MutableLiveData<SharedUtil> sharedUtil;
    private MutableLiveData<Map<String, Object>> publicKey;

    public MutableLiveData<SharedUtil> getSharedUtil() {
        if (sharedUtil == null) {
            sharedUtil = new MutableLiveData<SharedUtil>();
            sharedUtil.setValue(MsgBoxApplication.sharedUtil);
        }
        return sharedUtil;
    }

}
