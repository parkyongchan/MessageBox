package com.ah.acr.messagebox;

import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.database.InboxViewModel;
import com.ah.acr.messagebox.database.InboxViewModelFactory;
import com.ah.acr.messagebox.database.MsgRoomDatabase;
import com.ah.acr.messagebox.database.OutboxViewModel;
import com.ah.acr.messagebox.database.OutboxViewModelFactory;
import com.ah.acr.messagebox.databinding.FragmentMainBinding;
import com.ah.acr.messagebox.packet.PacketProcUtil;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.clj.fastble.BleManager;
import com.google.android.material.snackbar.Snackbar;


public class MainFragment extends Fragment {
    private FragmentMainBinding binding;
    private KeyViewModel mKeyViewModel;
    //private OutboxViewModel mOutboxViewModel;
    //private InboxViewModel mInboxViewModel;

//    private Handler handler;
//    private Runnable runnable;

    private OnBackPressedCallback backPressedCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        //mBleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);
        binding = FragmentMainBinding.inflate(inflater, container, false);
        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);

        //mInboxViewModel = new InboxViewModelFactory(
        //        MsgRoomDatabase.Companion.getDatabase(getContext()).inboxDao()
        //).create(InboxViewModel.class);

       // mOutboxViewModel = new OutboxViewModelFactory(
        //        MsgRoomDatabase.Companion.getDatabase(getContext()).outboxDao()
        //).create(OutboxViewModel.class);

        

        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                binding.buttonBleSet.setBackgroundColor(Color.CYAN);
//                binding.buttonInbox.setBackgroundColor(Color.CYAN);
//                binding.buttonOutbox.setBackgroundColor(Color.CYAN);
//                binding.buttonSetting.setBackgroundColor(Color.CYAN);
//                binding.buttonStatus.setBackgroundColor(Color.CYAN);
            } else {
                binding.buttonBleSet.setBackgroundColor(Color.BLACK);
//                binding.buttonInbox.setBackgroundColor(Color.BLACK);
//                binding.buttonOutbox.setBackgroundColor(Color.BLACK);
//                binding.buttonSetting.setBackgroundColor(Color.BLACK);
//                binding.buttonStatus.setBackgroundColor(Color.BLACK);
            }
        });

//        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), info -> {
//            if (info.isSosStarted()) binding.buttonSos.setBackgroundColor(Color.RED);
//            binding.buttonSos.setBackgroundColor(Color.BLACK);
//        });

//        handler = new Handler(Looper.getMainLooper());
//        runnable = new Runnable() {
//            @Override
//            public void run() {
//
//                doPeriodicTask();
//                if (handler != null) {
//                    handler.postDelayed(this, 60000);
//                }
//            }
//        };
//
//        handler.post(runnable); // 작업 시작

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //clearBackStack();

        binding.buttonBleSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_ble_set_fragment);

//                NavController navController = NavHostFragment.findNavController(MainFragment.this);
//
//                NavOptions navOptions = new NavOptions.Builder()
//                        .setPopUpTo(R.id.main_fragment, false)  // main_fragment 포함하여 클리어
//                        .build();
//
//                navController.navigate(R.id.action_main_fragment_to_ble_set_fragment, null, navOptions);
            }
        });

        binding.buttonStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_status_fragment);
            }
        });

        binding.buttonSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_main_setting_parent_fragment);
                        //.navigate(R.id.action_main_fragment_to_main_setting_fragment);

                BLE.INSTANCE.getWriteQueue().offer("SET=?");
            }
        });

        binding.buttonMsgBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_main_msgbox_fragment);
            }
        });

        binding.buttonLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_main_location_fragment);
            }
        });

//        binding.buttonTestSend.setOnClickListener(new View.OnClickListener(){
//            @Override
//            public void onClick(View v) {
//                //BLE.INSTANCE.getWriteQueue().offer("TESTMSG=" + Base64.encodeToString(header, Base64.NO_WRAP));
//                ////((MainActivity) getActivity()).bleSendMessage("TESTMSG=1");
//            }
//        });

//        binding.buttonMsgAllDelete.setOnClickListener(c -> {
//            final Snackbar snackbar = Snackbar.make(getView(), getString(R.string.main_all_delete_message), Snackbar.LENGTH_LONG);
//            snackbar.setAction("OK", v2 ->  {
//                //mInboxViewModel.deleteAllInboxMsg();
//                //mOutboxViewModel.deleteAllOutboxMsg();
//
//                BLE.INSTANCE.getWriteQueue().offer("MSGDEL=?");
//
//                snackbar.dismiss();
//            });
//            snackbar.show();
//        });


        // 뒤로 가기 버튼 콜백 설정
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 여기에 뒤로 가기 버튼을 눌렀을 때 실행할 코드 작성

                // 예시 1: 확인 다이얼로그 표시 후 뒤로 가기
                showExitConfirmationDialog();

                // 예시 2: 바로 뒤로 가기
                // navigateBack();

                // 예시 3: 조건부로 뒤로 가기 허용
                // if (canGoBack()) {
                //     navigateBack();
                // } else {
                //     Toast.makeText(getContext(), "작업을 완료해주세요", Toast.LENGTH_SHORT).show();
                // }
            }
        };

        // 콜백을 Fragment의 lifecycle에 추가
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                callback
        );
    }

    private void showExitConfirmationDialog() {
            new AlertDialog.Builder(requireContext())
            .setTitle("Exit app")
            .setMessage("Do you want to exit the app?")
            .setPositiveButton("Yes", (dialog, which) -> {
                // 뒤로 가기 실행
                requireActivity().finishAffinity();
            })
            .setNegativeButton("No", null)
            .show();
    }

    private void clearBackStack() {
        NavController navController = NavHostFragment.findNavController(MainFragment.this);

        // 메인 프래그먼트까지의 모든 백스택 클리어 (메인은 남김)
        navController.popBackStack(R.id.main_fragment, false);
    }

    private void doPeriodicTask() {
        if (isAdded() && getView() != null) {
            Log.d("PeriodicTask", "작업 실행됨");

            //BLE.INSTANCE.getWriteQueue().offer("INFO=?");
            // 여기에 주기적으로 실행할 코드 작성
        }
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Toolbar의 뒤로 가기 버튼 클릭 시
            // OnBackPressedDispatcher를 호출하면 위의 콜백이 실행됩니다
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
/*        if (handler != null) {
            handler.removeCallbacks(runnable);
            handler = null;
        }*/
        binding = null;
    }


}