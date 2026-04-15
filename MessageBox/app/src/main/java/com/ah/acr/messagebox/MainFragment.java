package com.ah.acr.messagebox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.databinding.FragmentMainBinding;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;

public class MainFragment extends Fragment {
    private FragmentMainBinding binding;
    private KeyViewModel mKeyViewModel;
    private BleViewModel mBleViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMainBinding.inflate(inflater, container, false);
        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);
        mBleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);

        // BLE 연결 상태 표시
        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                binding.textBleStatusMain.setText("● 연결됨");
                binding.textBleStatusMain.setTextColor(0xFF00E5D1);
            } else {
                binding.textBleStatusMain.setText("● 미연결");
                binding.textBleStatusMain.setTextColor(0xFFFF5252);
                // 연결 끊기면 상태 초기화
                binding.textMainBattery.setText("- %");
                binding.textMainSignal.setText("-");
                binding.textMainInbox.setText("0");
                binding.textMainOutbox.setText("0");
            }
        });

        // 디바이스 상태 (배터리, 신호, 메시지 수)
        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                // 배터리 mV → % 변환 (3300mV=0%, 4200mV=100%)
                int mv = status.getBattery();
                int percent = (int) ((mv - 3300) / 9.0);
                percent = Math.max(0, Math.min(100, percent));
                binding.textMainBattery.setText(percent + "%");

                // 신호 세기
                int signal = status.getSignal();
                String signalStr = signal + "/5";
                binding.textMainSignal.setText(signalStr);

                // 수신 메시지
                int inbox = status.getInBox();
                binding.textMainInbox.setText(String.valueOf(inbox));

                // 송신 대기
                int outbox = status.getOutBox();
                binding.textMainOutbox.setText(String.valueOf(outbox));

                // 수신 배지 표시
                if (inbox > 0) {
                    binding.badgeInbox.setVisibility(View.VISIBLE);
                    binding.badgeInbox.setText(String.valueOf(inbox));
                } else {
                    binding.badgeInbox.setVisibility(View.GONE);
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 블루투스 연결
        binding.buttonBleSet.setOnClickListener(v ->
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_ble_set_fragment)
        );

        // 메시지함
        binding.buttonMsgBox.setOnClickListener(v ->
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_main_msgbox_fragment)
        );

        // 위치
        binding.buttonLocation.setOnClickListener(v ->
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_main_location_fragment)
        );

        // 설정
        binding.buttonSetting.setOnClickListener(v -> {
            NavHostFragment.findNavController(MainFragment.this)
                    .navigate(R.id.action_main_fragment_to_main_setting_parent_fragment);
            BLE.INSTANCE.getWriteQueue().offer("SET=?");
        });

        // 디바이스 상태
        binding.buttonStatus.setOnClickListener(v ->
                NavHostFragment.findNavController(MainFragment.this)
                        .navigate(R.id.action_main_fragment_to_status_fragment)
        );

        // SOS 긴급 버튼
        binding.buttonSosMain.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("🆘 SOS 긴급 전송")
                    .setMessage("SOS 신호를 전송하시겠습니까?\n3분 간격으로 계속 전송됩니다.")
                    .setPositiveButton("전송", (dialog, which) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=4");
                    })
                    .setNegativeButton("취소", null)
                    .show();
        });

        // 뒤로 가기 처리
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), callback
        );
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("앱 종료")
                .setMessage("앱을 종료하시겠습니까?")
                .setPositiveButton("종료", (dialog, which) ->
                        requireActivity().finishAffinity()
                )
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}