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
import com.ah.acr.messagebox.data.DeviceInfo;

public class MainFragment extends Fragment {
    private FragmentMainBinding binding;
    private KeyViewModel mKeyViewModel;
    private BleViewModel mBleViewModel;
    private int mTestTapCount = 0;
    private boolean mIsTestMode = false;
    private com.ah.acr.messagebox.database.MsgViewModel mMsgViewModel;

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
        mMsgViewModel = new ViewModelProvider(requireActivity()).get(
                com.ah.acr.messagebox.database.MsgViewModel.class);

        // 미전송 메시지 수 실시간 감지
        mMsgViewModel.getAllMsgs().observe(getViewLifecycleOwner(), allMsgs -> {
            if (allMsgs == null || binding == null) return;
            int unsentCount = 0;
            for (com.ah.acr.messagebox.database.MsgEntity msg : allMsgs) {
                if (msg.isSendMsg() && !msg.isSend()) unsentCount++;
            }
            if (unsentCount > 0) {
                binding.textMainUnsent.setVisibility(View.VISIBLE);
                binding.textMainUnsent.setText("(" + unsentCount + ")");
            } else {
                binding.textMainUnsent.setVisibility(View.GONE);
            }
        });

        // BLE 연결 상태 표시
        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                mIsTestMode = false; // 실제 연결 시 테스트모드 해제
                binding.textBleStatusMain.setText("● BLE 연결");
                binding.textBleStatusMain.setTextColor(0xFF00E5D1);
                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
            } else {
                if (mIsTestMode) return; // 테스트 모드 중이면 초기화 건너뜀
                binding.textBleStatusMain.setText("● BLE 미연결");
                binding.textBleStatusMain.setTextColor(0xFFFF5252);
                binding.textMainBattery.setText("- %");
                binding.textMainInbox.setText("0");
                binding.textMainOutbox.setText("0");
                binding.textMainImei.setText("-");
                updateSignalBar(0);
                updateLocationStatus(0);
            }
        });

        // 디바이스 상태 (배터리, 신호, 메시지 수, IMEI)
        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                // 배터리 (StatusFragment와 동일하게 그대로 % 표시)
                binding.textMainBattery.setText(String.format("%d%%", status.getBattery()));

                // 신호 감도 막대
                updateSignalBar(status.getSignal());

                // 수신 메시지
                int inbox = status.getInBox();
                binding.textMainInbox.setText(String.valueOf(inbox));

                // 송신 대기
                int outbox = status.getOutBox();
                binding.textMainOutbox.setText(String.valueOf(outbox));

                // 수신 배지
                if (inbox > 0) {
                    binding.badgeInbox.setVisibility(View.VISIBLE);
                    binding.badgeInbox.setText(String.valueOf(inbox));
                } else {
                    binding.badgeInbox.setVisibility(View.GONE);
                }

                // IMEI
                DeviceInfo deviceInfo = BLE.INSTANCE.getDeviceInfo().getValue();
                if (deviceInfo != null && deviceInfo.getImei() != null) {
                    binding.textMainImei.setText(deviceInfo.getImei().toString());
                }

                // 위치 상태 (SOS > Tracking > OFF 우선순위)
                if (status.isSosMode()) {
                    updateLocationStatus(2);
                } else if (status.isTrackingMode()) {
                    updateLocationStatus(1);
                } else {
                    updateLocationStatus(0);
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
        // ─── 테스트 모드 (헤더 타이틀 5번 탭) ───
        binding.getRoot().findViewById(android.R.id.content); // 안전 참조
        // TYTO MessageBox 텍스트뷰 탭 5번 → 테스트 데이터 주입
        View headerLayout = binding.headerLayout;
        headerLayout.setOnClickListener(v -> {
            mTestTapCount++;
            if (mTestTapCount >= 5) {
                mTestTapCount = 0;
                if (mIsTestMode) {
                    // 테스트 모드 해제
                    mIsTestMode = false;
                    binding.textBleStatusMain.setText("● 미연결");
                    binding.textBleStatusMain.setTextColor(0xFFFF5252);
                    binding.textMainBattery.setText("- %");
                    binding.textMainInbox.setText("0");
                    binding.textMainOutbox.setText("0");
                    binding.textMainImei.setText("-");
                    updateSignalBar(0);
                    updateLocationStatus(0);
                    android.widget.Toast.makeText(requireContext(),
                            "🧪 테스트 모드 해제", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    // 테스트 모드 진입
                    injectTestData();
                }
            }
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

    /** 신호 감도 막대 업데이트 (0~5단계) */
    private void updateSignalBar(int signal) {
        if (binding == null) return;
        View[] bars = {
                binding.sigBar1, binding.sigBar2, binding.sigBar3,
                binding.sigBar4, binding.sigBar5
        };
        int activeColor  = 0xFF00E5D1;  // 청록색
        int inactiveColor = 0xFF1A2F50; // 어두운 배경색

        binding.signalBarContainer.setVisibility(View.VISIBLE);
        if (signal <= 0) {
            // 감도 0: 빨간 X 표시 + 막대 흐리게
            binding.sigNoSignal.setVisibility(View.VISIBLE);
            for (View bar : bars) {
                bar.setBackgroundColor(0xFF2A1A1A); // 어두운 빨간빛
                bar.setAlpha(0.4f);
            }
        } else {
            // 감도 있음: X 숨기고 막대 표시
            binding.sigNoSignal.setVisibility(View.GONE);
            for (int i = 0; i < bars.length; i++) {
                bars[i].setAlpha(1.0f);
                bars[i].setBackgroundColor(i < signal ? activeColor : inactiveColor);
            }
        }
    }

    //** 테스트 데이터 주입 (BLE 없이 UI 확인용) */
    private void injectTestData() {
        android.widget.Toast.makeText(requireContext(),
                "🧪 테스트 데이터 주입", android.widget.Toast.LENGTH_SHORT).show();

        // ① DeviceStatus 가짜 데이터
        com.ah.acr.messagebox.data.DeviceStatus testStatus =
                new com.ah.acr.messagebox.data.DeviceStatus();
        testStatus.setBattery(4000);          // mV 입력 → 내부 변환 ≈ 74%
        testStatus.setSignal(3);              // 신호 3/5
        testStatus.setInBox(2);              // 수신 2건
        testStatus.setOutBox(1);             // 대기 1건
        testStatus.setGpsTime("2026-04-16 10:50:00");
        testStatus.setGpsLat("37.5665");
        testStatus.setGpsLng("126.9780");
        testStatus.setTrackingMode(true);    // TRACKING 중
        testStatus.setSosMode(false);
        mBleViewModel.getDeviceStatus().postValue(testStatus);

        // ② IMEI 직접 표시 (테스트용)
        binding.textMainImei.setText("300434061000001");

        // ③ 테스트 모드 플래그 활성화 + BLE 상태 표시
        mIsTestMode = true;
        binding.textBleStatusMain.setText("● 테스트모드");
        binding.textBleStatusMain.setTextColor(0xFFFFB300);

        // ④ 신호 막대 즉시 표시
        updateSignalBar(3);

        // ⑤ 위치 상태 즉시 표시
        updateLocationStatus(1); // TRACKING 중
    }




    /**
     * 위치 상태 카드 업데이트
     * @param state 0=위치보고OFF, 1=TRACKING중, 2=SOS작동중
     */
    private void updateLocationStatus(int state) {
        if (binding == null) return;
        switch (state) {
            case 1: // TRACKING 중
                binding.cardLocationStatus.setCardBackgroundColor(0xFF0D2D40);
                binding.textLocationStatusIcon.setText("📡");
                binding.textLocationStatusLabel.setText("TRACKING 중");
                binding.textLocationStatusLabel.setTextColor(0xFF00E5D1);
                binding.dotLocationStatus.setBackgroundResource(R.drawable.circle_cyan);
                break;
            case 2: // SOS 작동중
                binding.cardLocationStatus.setCardBackgroundColor(0xFF3D0000);
                binding.textLocationStatusIcon.setText("🆘");
                binding.textLocationStatusLabel.setText("SOS 작동중");
                binding.textLocationStatusLabel.setTextColor(0xFFFF5252);
                binding.dotLocationStatus.setBackgroundResource(R.drawable.circle_red);
                break;
            default: // 위치보고 OFF
                binding.cardLocationStatus.setCardBackgroundColor(0xFF0D1F3C);
                binding.textLocationStatusIcon.setText("📍");
                binding.textLocationStatusLabel.setText("위치보고 OFF");
                binding.textLocationStatusLabel.setTextColor(0xFF8899AA);
                binding.dotLocationStatus.setBackgroundResource(R.drawable.circle_gray);
                break;
        }
    }




    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}