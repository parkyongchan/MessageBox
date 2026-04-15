package com.ah.acr.messagebox;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.ah.acr.messagebox.databinding.FragmentLogInBinding;
import com.ah.acr.messagebox.login.LoggedInUserView;
import com.ah.acr.messagebox.login.LoginResult;
import com.ah.acr.messagebox.login.LoginViewModel;
import com.ah.acr.messagebox.login.LoginViewModelFactory;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;

import java.util.Locale;


public class LogInFragment extends Fragment {
    private FragmentLogInBinding binding;
    private LoginViewModel loginViewModel;
    private KeyViewModel mKeyViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mKeyViewModel = new ViewModelProvider(this).get(KeyViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLogInBinding.inflate(inflater, container, false);

        binding.getRoot().setOnClickListener(v->{
            hideKeyboard();
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loginViewModel = new ViewModelProvider(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);


        loginViewModel.getLoginResult().observe(getViewLifecycleOwner(), new Observer<LoginResult>() {
            @Override
            public void onChanged(@Nullable LoginResult loginResult) {
                if (loginResult == null) {
                    return;
                }
                if (loginResult.getError() != null) {
                    showLoginFailed(loginResult.getError());
                }
                if (loginResult.getSuccess() != null) {
                    updateUiWithUser(loginResult.getSuccess());

                    Intent intent = new Intent(LogInFragment.this.getActivity(), MainActivity.class);
                    startActivity(intent);
                }
            }
        });

        TextWatcher afterTextChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // ignore
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // ignore
            }
            @Override
            public void afterTextChanged(Editable s) {
                loginViewModel.loginDataChanged(binding.textId.getText().toString(),
                        binding.textPw.getText().toString());
            }
        };


        binding.buttonPwEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = binding.textId.getText().toString().trim();
                if (!name.isEmpty()){
                    Bundle bundle = new Bundle();
                    bundle.putString("name", name);
                    NavHostFragment.findNavController(LogInFragment.this)
                            .navigate(R.id.action_login_fragment_to_login_pw_fragment, bundle);
                    return;
                }
            }
        });


        binding.buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                String name = binding.textId.getText().toString().trim();
                if (name.isEmpty()) return;
                String pw = shared.getString(name+"_password");
                
                if (binding.textPw.getText().toString().equals("0000") && !name.isEmpty()){
                    Bundle bundle = new Bundle();
                    bundle.putString("name", name);
                    NavHostFragment.findNavController(LogInFragment.this)
                            .navigate(R.id.action_login_fragment_to_login_pw_fragment, bundle);
                    return;
                }

                loginViewModel.login(binding.textId.getText().toString(),
                        binding.textPw.getText().toString());
            }
        });

    }

    private void updateUiWithUser(LoggedInUserView model) {
         String welcome = "Welcome!"; //getString(R.string.welcome) + model.getDisplayName();
        // TODO : initiate successful logged in experience
        if (getContext() != null && getContext().getApplicationContext() != null) {
            Toast.makeText(getContext().getApplicationContext(), welcome, Toast.LENGTH_LONG).show();
        }
    }

    private void showLoginFailed(@StringRes Integer errorString) {
        if (getContext() != null && getContext().getApplicationContext() != null) {
            Toast.makeText(
                    getContext().getApplicationContext(),
                    errorString,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void hideKeyboard(){
        if (getActivity() != null && requireActivity().getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager)requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(requireActivity().getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS );
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}