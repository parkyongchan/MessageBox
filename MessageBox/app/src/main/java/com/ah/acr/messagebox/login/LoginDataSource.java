package com.ah.acr.messagebox.login;


import com.ah.acr.messagebox.MsgBoxApplication;

import java.io.IOException;

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
public class LoginDataSource {

    public Result<LoggedInUser> login(String username, String password) {

        try {

            String pw = MsgBoxApplication.sharedUtil.getString(username+"_password");
            if (password.equals(pw)){
                MsgBoxApplication.sharedUtil.putAny("loginId", username);
                LoggedInUser user =
                        new LoggedInUser(username, "MessageBox");
                return new Result.Success<>(user);
            }
            return new Result.Error(new IOException("Password error"));

        } catch (Exception e) {
            return new Result.Error(new IOException("Error logging in", e));
        }
    }

    public void logout() {
        // TODO: revoke authentication
    }
}