package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.databinding.FragmentMsgOutBoxSubNewBinding;
import com.ah.acr.messagebox.search.SearchDialogFragment;
import com.ah.acr.messagebox.util.ByteLengthFilter;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;


public class MsgOutBoxSubNewFragment extends Fragment {
    private static final String TAG = MsgOutBoxSubNewFragment.class.getSimpleName();
    private FragmentMsgOutBoxSubNewBinding binding;
    private KeyViewModel mKeyViewModel;
    private MsgViewModel msgViewModel;

    private AddressViewModel addressViewModel;

    private String mCodeNum;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentMsgOutBoxSubNewBinding.inflate(inflater, container, false);

        setupViewModel();
        setupClickListeners();

        binding.getRoot().setOnClickListener(v->{
            hideKeyboard();
        });

//        ArrayAdapter msgTypeAdapter = ArrayAdapter.createFromResource(getContext(),
//                R.array.msg_type, android.R.layout.simple_spinner_item);
//        msgTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        binding.spinnerMsgType.setAdapter(msgTypeAdapter);

        setupFragmentResultListener();

        return binding.getRoot();
    }


    private void setupViewModel() {
        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);
        msgViewModel = new ViewModelProvider(this).get(MsgViewModel.class);
        addressViewModel = new ViewModelProvider(requireActivity()).get(AddressViewModel.class);

        mCodeNum = null;
    }

    private void setupClickListeners() {


        binding.textMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //binding.textMsgSize.setText( String.valueOf(binding.textMessage.getText()).getBytes(StandardCharsets.UTF_8).length + "/240");
            }
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    binding.textMsgSize.setText( String.valueOf(binding.textMessage.getText()).getBytes(StandardCharsets.UTF_8).length + "/200");
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        });

        binding.textMessage.setFilters(new InputFilter[]{new ByteLengthFilter(200, StandardCharsets.UTF_8.name())});

        binding.textTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //Log.v("TAG", "beforeTextChanged");
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //Log.v("TAG", "onTextChanged");
            }
            @Override
            public void afterTextChanged(Editable s) {
                //Log.v("TAG", "afterTextChanged");
                try {
                    binding.textTitleSize.setText( String.valueOf(binding.textTitle.getText()).getBytes(StandardCharsets.UTF_8).length + "/20");
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        });

        binding.textTitle.setFilters(new InputFilter[]{new ByteLengthFilter(20, StandardCharsets.UTF_8.name())});


        binding.buttonMsgSave.setOnClickListener(v->{

//            String codeNum;
//            if (mCodeNum == null) codeNum = binding.textReceiver.getText().toString().trim();
//            else codeNum = mCodeNum;

            String nicName = binding.textReceiver.getText().toString().trim();
            addressViewModel.getAddressByNicName(nicName).observe(getViewLifecycleOwner(), addressEntity -> {
                String codeNum = nicName;
                if (addressEntity != null) {
                    codeNum = addressEntity.getNumbers();
                }

                if (!codeNum.matches("\\d+")) {
                    Toast.makeText(getContext(), "The recipient's number must contain only numbers.", Toast.LENGTH_LONG).show();
                    return;
//                    new AlertDialog.Builder(getContext())
//                            .setTitle("Message Cancel")
//                            .setMessage("The recipient's number must contain only numbers.")
//                            .setPositiveButton("OK", (dialog, which) -> {
//                                return;
//                            })
//                            .setNegativeButton("cancel", null)
//                            .show();
                }

                String title = binding.textTitle.getText().toString().trim();
                String bodyMsg = binding.textMessage.getText().toString().trim();

                MsgEntity msg = new MsgEntity(
                        0,
                        true,
                        codeNum,
                        title,
                        bodyMsg,
                        new Date(),
                        new Date(System.currentTimeMillis()),
                        new Date(System.currentTimeMillis()),
                        false,
                        false,
                        false
                );
                Log.v(TAG, msg.toString());

                msgViewModel.insert(msg, success -> {
                    if (success) {
                        // insert 완료 후 동작
                        navigateBack();
                        Log.d("Caller", "Insert success");
                    } else {
                        Log.d("Caller", "Insert failed");
                    }
                    return null;
                });
            });


        });

        binding.buttonMsgCancel.setOnClickListener(v->{
            new AlertDialog.Builder(getContext())
                    .setTitle("Message Cancel")
                    .setMessage(getString(R.string.outbox_new_cencel))
                    .setPositiveButton("Confirm", (dialog, which) -> {
                        navigateBack();
                    })
                    .setNegativeButton("cancel", null)
                    .show();
        });


        binding.buttonSearch.setOnClickListener(v-> showSearchDialog());
    }


    private void showSearchDialog() {
        SearchDialogFragment searchDialog = new SearchDialogFragment();
        // Fragment에서는 getParentFragmentManager() 또는 getChildFragmentManager() 사용
        searchDialog.show(getParentFragmentManager(), "SearchDialog");
    }

    private void setupFragmentResultListener() {
        // 검색 결과 받기 - Fragment에서는 getParentFragmentManager() 사용
        getParentFragmentManager().setFragmentResultListener("search_result", this,
                new FragmentResultListener() {
                    @Override
                    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                        int selectedId = bundle.getInt("selected_id");
                        String selectedTitle = bundle.getString("selected_nic");
                        String selectedDescription = bundle.getString("selected_code");

                        // 선택된 결과 처리
                        handleSearchResult(selectedId, selectedTitle, selectedDescription);
                    }
                });
    }


    private void handleSearchResult(int id, String title, String code) {
        // 검색 결과 처리 로직
        if (getContext() != null) {
            //Toast.makeText(getContext(), "선택됨: " + title, Toast.LENGTH_SHORT).show();
            binding.textReceiver.setText(title);
            mCodeNum = code;
            // save........
        }
    }


    private void navigateBack() {
        try {
            NavController navController = Navigation.findNavController(requireView());
            navController.navigateUp();
            // 또는
            // navController.popBackStack();
        } catch (Exception e) {
            // Navigation이 설정되지 않은 경우 기본 방법 사용
            closeFragment();
        }
    }

    private void closeFragment() {
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().popBackStack();
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