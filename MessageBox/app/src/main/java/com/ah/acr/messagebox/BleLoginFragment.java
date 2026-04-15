package com.ah.acr.messagebox;

import android.content.Context;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.databinding.FragmentBleLoginBinding;
import com.ah.acr.messagebox.databinding.FragmentMsgInBoxSubBinding;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.clj.fastble.BleManager;


public class BleLoginFragment extends Fragment {
    private static final String TAG = BleLoginFragment.class.getSimpleName();

    //private String serialNum;
    private FragmentBleLoginBinding binding;
    private KeyViewModel mKeyViewModel;

    private OnBackPressedCallback backPressedCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentBleLoginBinding.inflate(inflater, container, false);
        //mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);

        //DeviceInfo info =  mBleViewModel.getDeviceInfo().getValue();
        //serialNum = info.getSerialNum();

        BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_NONE);

        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
            @Override
            public void onChanged(DeviceInfo deviceInfo) {
                Log.v(TAG, deviceInfo.toString());
                //binding.textId.setText(deviceInfo.getBudaeNum());
                binding.textId.setText(deviceInfo.getImei());
            }
        });

        BLE.INSTANCE.getBleLoginStatus().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                Log.v(TAG, s);
                if (s.equals(BLE.BLE_LOGIN_TRY)) {

                } else if (s.equals(BLE.BLE_LOGIN_OK)) {
                    //NavHostFragment.findNavController(BleLoginFragment.this)
                    //        .navigate(R.id.action_main_ble_login_fragment_to_main_fragment);

                    NavController navController = NavHostFragment.findNavController(BleLoginFragment.this);

                    NavOptions navOptions = new NavOptions.Builder()
                            .setPopUpTo(R.id.main_fragment, false)  // main_fragment 포함하여 클리어
                            .build();
                    navController.navigate(R.id.action_main_ble_login_fragment_to_main_fragment, null, navOptions);

                } else if (s.equals(BLE.BLE_LOGIN_FAIL)) {
                    BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());
                    //NavHostFragment.findNavController(BleLoginFragment.this)
                    //        .navigate(R.id.action_main_ble_login_fragment_to_main_ble_set_fragment);

                    NavController navController = NavHostFragment.findNavController(BleLoginFragment.this);

                    NavOptions navOptions = new NavOptions.Builder()
                            .setPopUpTo(R.id.main_fragment, false)  // main_fragment 포함하여 클리어
                            .build();
                    navController.navigate(R.id.action_main_ble_login_fragment_to_main_ble_set_fragment, null, navOptions);

                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_TRY)) {
                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_OK)) {
                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_FAIL)) {
                }
            }
        });

        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);
        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
            @Override
            public void onChanged(DeviceInfo deviceInfo) {
                String serialNum =deviceInfo.getSerialNum();

                SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                boolean remember = shared.getBoolean("lonin_remember");
                if (remember){
                    String password = shared.getString(serialNum+"_login_pw");
                    binding.textPw.setText(password);
                    binding.checkboxRemember.setChecked(remember);
                }
            }
        });

        binding.checkboxRemember.setOnClickListener(v->{
            SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
            shared.putAny("lonin_remember", binding.checkboxRemember.isChecked());
        });



        binding.buttonLogin.setOnClickListener(v->{
            String password = binding.textPw.getText().toString().trim();
            String codeNum = binding.textId.getText().toString().trim();

            if (isPasswordValid(password)) {

                BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
                    @Override
                    public void onChanged(DeviceInfo deviceInfo) {
                        Log.v(TAG, deviceInfo.toString());
                        BLE.INSTANCE.getWriteQueue().offer(String.format("LOGIN=%s,%s", deviceInfo.getSerialNum(), password));

                        SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                        shared.putAny(deviceInfo.getSerialNum() +"_login_pw", password);
                    }
                });



            } else {
                Toast.makeText(getContext(), "password not valid.", Toast.LENGTH_LONG).show();
            }

        });


        binding.getRoot().setOnClickListener(v->{
            hideKeyboard();
        });

        return binding.getRoot();
    }



    private boolean isPasswordValid(String password) {
        return password != null && password.trim().length() > 8;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        String state = BLE.INSTANCE.getBleLoginStatus().getValue();

        if (!state.equals(BLE.BLE_LOGIN_OK) && !state.equals(BLE.BLE_LOGIN_CHANGE_OK)) {
            BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());
        }

        navigateBack();



//        new AlertDialog.Builder(requireContext())
//                .setTitle("종료 확인")
//                .setMessage("정말 나가시겠습니까?")
//                .setPositiveButton("예", (dialog, which) -> {
//                    // 뒤로 가기 실행
//                    navigateBack();
//                })
//                .setNegativeButton("아니오", null)
//                .show();
    }


    // Toolbar의 뒤로 가기 버튼 처리
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

    // 동적으로 뒤로 가기 콜백을 활성화/비활성화하는 경우
    private void setupBackPressWithDynamicControl() {
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // 뒤로 가기 처리
                Toast.makeText(getContext(), "뒤로 가기 버튼 클릭됨", Toast.LENGTH_SHORT).show();
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                backPressedCallback
        );
    }

    // 필요할 때 활성화/비활성화
    private void enableBackPress() {
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(true);
        }
    }

    private void disableBackPress() {
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(false);
        }
    }

    // 조건 체크 예시
    private boolean canGoBack() {
        // 여기에 뒤로 가기 가능 여부를 판단하는 로직 작성
        // 예: 입력된 데이터가 있는지, 저장되지 않은 변경사항이 있는지 등
        return true;
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

        //Log.d("DEBUG", "콜백 onDestroyView");
        if (!BLE.INSTANCE.getBleLoginStatus().getValue().equals(BLE.BLE_LOGIN_OK))
            BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}