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
import androidx.navigation.fragment.NavHostFragment;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.MsgRoomDatabase;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.OutboxMsg;
import com.ah.acr.messagebox.database.OutboxViewModel;
import com.ah.acr.messagebox.database.OutboxViewModelFactory;
import com.ah.acr.messagebox.databinding.FragmentMsgOutBoxSubBinding;
import com.ah.acr.messagebox.packet.PacketProcUtil;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.search.SearchDialogFragment;
import com.ah.acr.messagebox.util.ByteLengthFilter;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;


public class MsgOutBoxSubFragment extends Fragment {
    private static final String TAG = MsgOutBoxSubFragment.class.getSimpleName();
    private FragmentMsgOutBoxSubBinding binding;
    private KeyViewModel mKeyViewModel;
    private MsgViewModel msgViewModel;

    private AddressViewModel addressViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentMsgOutBoxSubBinding.inflate(inflater, container, false);

        setupViewModel();
        setupClickListeners();

        binding.getRoot().setOnClickListener(v->{
            hideKeyboard();
        });


        if (requireArguments().containsKey("id")) {
            int msgId = requireArguments().getInt("id", 0);
            msgViewModel.getMsgById(msgId).observe(getViewLifecycleOwner(), msgEntity -> {
                if (msgEntity != null) {

                    //receiver
                    addressViewModel.getAddressByNumbers(msgEntity.getCodeNum()).observe(getViewLifecycleOwner(), new Observer<AddressEntity>() {
                        @Override
                        public void onChanged(AddressEntity addressEntity) {
                            if (addressEntity != null) {
                                binding.textReceiver.setText(addressEntity.getNumbersNic());
                            } else {
                                binding.textReceiver.setText(msgEntity.getCodeNum());
                            }
                        }
                    });

                    binding.textTitle.setText(msgEntity.getTitle());
                    binding.textMessage.setText(msgEntity.getMsg());

//                    try {
//                        binding.textMsgSize.setText(message.getBytes(StandardCharsets.UTF_8).length + "/200");
//                    } catch (Exception e) {
//                        Log.e(TAG, e.toString());
//                    }
                } else {
                    Log.v(TAG, "id: " + msgId               );
                }
            });

        }

        setupFragmentResultListener();

         return binding.getRoot();
    }

    private void setupViewModel() {
        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);
        msgViewModel = new ViewModelProvider(this).get(MsgViewModel.class);
        addressViewModel = new ViewModelProvider(requireActivity()).get(AddressViewModel.class);
    }

    private void setupClickListeners() {
        binding.textMessage.addTextChangedListener(new TextWatcher() {
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

        binding.buttonMsgEdit.setOnClickListener(v->{
            // save msg....

            if (requireArguments().containsKey("id")) {
                int msgId = requireArguments().getInt("id", 0);
                msgViewModel.getMsgById(msgId).observe(getViewLifecycleOwner(), msgEntity -> {
                    String msg = binding.textMessage.getText().toString().trim();
                    String title = binding.textTitle.getText().toString().trim();
                    if (msgEntity.getMsg().equals(msg) && msgEntity.getTitle().equals(title)) navigateBack();

                    msgEntity.setTitle(title);
                    msgEntity.setMsg(msg);
                    msgEntity.setSend(false);

                    msgViewModel.update(msgEntity, success -> {
                        if (success) {
                            navigateBack();
                            Log.d("Caller", "Insert success");
                        } else {
                            Log.d("Caller", "Insert failed");
                        }
                        return null;
                    });
                });
            }



//            NavHostFragment.findNavController(MsgOutBoxSubFragment.this)
//                    .navigate(R.id.action_main_outbox_sub_fragment_to_main_outbox_fragment);


        });

        // 변경 물까능
        //binding.buttonSearch.setOnClickListener(v->showSearchDialog());

        binding.buttonMsgDelete.setOnClickListener(v->{

            navigateBack();
//            if (requireArguments().containsKey("id")) {
//                int msgId = requireArguments().getInt("id", 0);
//
//                msgViewModel.getMsgById(msgId).observe(getViewLifecycleOwner(), msgEntity -> {
//                    if (msgEntity != null) {
//                        new AlertDialog.Builder(getContext())
//                                .setTitle("Message Delete")
//                                .setMessage(getString(R.string.outbox_msg_del_alert))
//                                .setPositiveButton("Delete", (dialog, which) -> {
//                                    msgViewModel.delete(msgEntity);
//
//                                    navigateBack();
//                                })
//                                .setNegativeButton("cancel", null)
//                                .show();
//                    } else {
//                        Log.v(TAG, "id: " + msgId               );
//                    }
//                });
//
//            }
        });
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
                        String selectedNic = bundle.getString("selected_nic");
                        String selectedCode = bundle.getString("selected_code");

                        // 선택된 결과 처리
                        handleSearchResult(selectedId, selectedNic, selectedCode);
                    }
                });
    }


    private void handleSearchResult(int id, String nicName, String codeNum) {
        // 검색 결과 처리 로직
        if (getContext() != null) {
            Toast.makeText(getContext(), "선택됨: " + nicName, Toast.LENGTH_SHORT).show();
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