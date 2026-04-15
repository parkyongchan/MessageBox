package com.ah.acr.messagebox;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.ah.acr.messagebox.databinding.FragmentLogInPwEditBinding;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;

import java.util.Locale;

public class LogInPwEditFragment extends Fragment {
    private FragmentLogInPwEditBinding binding;
    private KeyViewModel mKeyViewModel;
    private String name;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLogInPwEditBinding.inflate(inflater, container, false);
        mKeyViewModel = new ViewModelProvider(this).get(KeyViewModel.class);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();

        if (requireArguments().containsKey("name")) {
            name = requireArguments().getString("name", "");
            String pw = shared.getString(name+"_password");
            if (pw.isEmpty()) {
                binding.textOldPassword.setText("0000");
                shared.putAny(name+"_password", "0000");
            } else {
                Toast.makeText(getContext().getApplicationContext(), "Please enter your old password.", Toast.LENGTH_LONG).show();
                binding.textOldPassword.requestFocus();
            }
        }


        binding.buttonChange.setOnClickListener(v->{

            String oldpw = shared.getString(name+"_password");
            String old = binding.textOldPassword.getText().toString().trim();
            String new1 = binding.textNewPassword1.getText().toString().trim();
            String new2 = binding.textNewPassword2.getText().toString().trim();
            if (!isPasswordValid(new1)){
                Toast.makeText(getContext().getApplicationContext(), "password failed. 5 length", Toast.LENGTH_LONG).show();
                return;
            }
            if (!isPasswordValid(new2)){
                Toast.makeText(getContext().getApplicationContext(), "password failed. 5 length", Toast.LENGTH_LONG).show();
                return;
            }
            if (!old.equals(oldpw)){
                Toast.makeText(getContext().getApplicationContext(), "Existing password does not match.", Toast.LENGTH_LONG).show();
                return;
            }
            if (!new1.equals(new2)){
                Toast.makeText(getContext().getApplicationContext(), "does not match.", Toast.LENGTH_LONG).show();
                return;
            }

            shared.putAny(name+"_password", new1);
            Toast.makeText(getContext().getApplicationContext(), "password success", Toast.LENGTH_LONG).show();

            NavHostFragment.findNavController(LogInPwEditFragment.this)
                    .navigate(R.id.action_login_pw_fragment_to_login_fragment);

        });

    }

    private boolean isPasswordValid(String password) {
        return password != null && password.trim().length() > 4;
    }
}