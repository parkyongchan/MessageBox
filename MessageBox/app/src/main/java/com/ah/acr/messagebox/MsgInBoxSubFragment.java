package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.databinding.FragmentMsgInBoxSubBinding;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;



public class MsgInBoxSubFragment extends Fragment {
    private static final String TAG = MsgInBoxSubFragment.class.getSimpleName();
    private FragmentMsgInBoxSubBinding binding;
    private KeyViewModel mKeyViewModel;
    private MsgViewModel msgViewModel;
    private AddressViewModel addressViewModel;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentMsgInBoxSubBinding.inflate(inflater, container, false);

        setupViewModel();
        setupClickListeners();

        if (requireArguments().containsKey("id")) {
            int msgId = requireArguments().getInt("id", 0);

            msgViewModel.getMsgById(msgId).observe(getViewLifecycleOwner(), msgEntity -> {
                if (msgEntity != null) {

                    addressViewModel.getAddressByNumbers(msgEntity.getCodeNum()).observe(getViewLifecycleOwner(), new Observer<AddressEntity>() {
                        @Override
                        public void onChanged(AddressEntity addressEntity) {
                            if (addressEntity != null) {
                                binding.textSender.setText(addressEntity.getNumbersNic());
                            } else {
                                binding.textSender.setText(msgEntity.getCodeNum());
                            }
                        }
                    });

                    binding.textTitle.setText(msgEntity.getTitle());
                    binding.textMessage.setText(msgEntity.getMsg());
                    msgEntity.setRead(true);

                    msgViewModel.update(msgEntity);
                } else {
                    //handleMessageError(msgId);
                    Log.v(TAG, "id: " + msgId);
                }
            });
        }

        return binding.getRoot();
    }

    private void setupViewModel() {
        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);
        msgViewModel = new ViewModelProvider(this).get(MsgViewModel.class);
        addressViewModel = new ViewModelProvider(requireActivity()).get(AddressViewModel.class);
    }

    private void setupClickListeners() {
        binding.buttonMsgSave.setOnClickListener(v->{
            if (requireArguments().containsKey("id")) {
                int msgId = requireArguments().getInt("id", 0);
            }

            navigateBack();
        });

        binding.buttonMsgDelete.setOnClickListener(v->{
            if (requireArguments().containsKey("id")) {
                int msgId = requireArguments().getInt("id", 0);

                msgViewModel.getMsgById(msgId).observe(getViewLifecycleOwner(), msgEntity -> {
                    if (msgEntity != null) {
                        new AlertDialog.Builder(getContext())
                                .setTitle("Message Delete")
                                .setMessage(getString(R.string.inbox_msg_del_alert))
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    msgViewModel.delete(msgEntity);

                                    navigateBack();
                                })
                                .setNegativeButton("cancel", null)
                                .show();
                    } else {
                        Log.v(TAG, "id: " + msgId               );
                    }
                });

            }

//            final Snackbar snackbar = Snackbar.make(getView(), getString(R.string.inbox_msg_del_alert), Snackbar.LENGTH_LONG);
//            snackbar.setAction("OK", v2 ->  {
//                if (mMsg == null) return;
//                //mInboxViewModel.deleteInboxMsg(mMsg);
//                snackbar.dismiss();
////                NavHostFragment.findNavController(MsgInBoxSubFragment.this)
////                        .navigate(R.id.action_main_inbox_sub_fragment_to_main_msgbox_fragment);
//            });
//            snackbar.show();
        });
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


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}