package com.ah.acr.messagebox;

import android.content.Context;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.navigation.NavController;
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
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.databinding.FragmentBleLoginBinding;
import com.ah.acr.messagebox.databinding.FragmentBleLoginChangeBinding;
import com.clj.fastble.BleManager;


public class BleLoginChangeFragment extends Fragment {

    private static final String TAG = BleLoginChangeFragment.class.getSimpleName();

    //private String serialNum;
    private FragmentBleLoginChangeBinding binding;
    private OnBackPressedCallback backPressedCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentBleLoginChangeBinding.inflate(inflater, container, false);

        BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_NONE);
        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
            @Override
            public void onChanged(DeviceInfo deviceInfo) {
                Log.v(TAG, deviceInfo.toString());
                // serialNum = deviceInfo.getSerialNum();
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

                } else if (s.equals(BLE.BLE_LOGIN_FAIL)) {

                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_TRY)) {

                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_OK)) {
                    Toast.makeText(getContext(), "Password change was successful.", Toast.LENGTH_LONG).show();
                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_FAIL)) {
                    Toast.makeText(getContext(), "Password change failed.", Toast.LENGTH_LONG).show();

                }
            }
        });


        binding.buttonLogin.setOnClickListener(v->{

            //String serialNum = binding.textId.getText().toString().trim();
            String password1 = binding.textPw.getText().toString().trim();
            String password2 = binding.textPwConfirm.getText().toString().trim();

            if (password1.equals(password2) && isPasswordValid(password1)) {
                BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
                    @Override
                    public void onChanged(DeviceInfo deviceInfo) {
                        Log.v(TAG, deviceInfo.toString());
                        String serialNum =deviceInfo.getSerialNum();
                        BLE.INSTANCE.getWriteQueue().offer(String.format("CHANGELOGIN=%s,%s", serialNum, password1));

                        navigateBack();
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

        Boolean isLogon = BLE.INSTANCE.isLogon().getValue();
        if (!isLogon) BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());

//        String state = BLE.INSTANCE.getBleLoginStatus().getValue();
//        if (!state.equals(BLE.BLE_LOGIN_OK) && !state.equals(BLE.BLE_LOGIN_CHANGE_OK)) {
//            BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());
//        }

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
//        Log.v(TAG, "뒤로가기...");
//        Boolean isLogon = BLE.INSTANCE.isLogon().getValue();
//        Log.v(TAG, "뒤로가기..." + isLogon.toString());
//        if (!Boolean.TRUE.equals(isLogon)) {
//            // 안전하게 true인 경우만 실행
//            BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());
//        }

        binding = null;
    }

}