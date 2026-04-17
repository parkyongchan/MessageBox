package com.ah.acr.messagebox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.databinding.FragmentMainBinding;

public class MainFragment extends Fragment {
    private FragmentMainBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle b) {
        binding = FragmentMainBinding.inflate(inflater, container, false);

        // 수신함 배지 (메시지함 카드가 삭제되었지만, Step 2에서 하단 탭 배지로 재사용 예정)
        new ViewModelProvider(requireActivity())
                .get(BleViewModel.class)
                .getDeviceStatus()
                .observe(getViewLifecycleOwner(), status -> {
                    if (status == null || binding == null) return;
                    int inbox = status.getInBox();
                    if (inbox > 0) {
                        binding.badgeInbox.setVisibility(View.VISIBLE);
                        binding.badgeInbox.setText(String.valueOf(inbox));
                    } else {
                        binding.badgeInbox.setVisibility(View.GONE);
                    }
                });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle b) {
        super.onViewCreated(view, b);

        // 뒤로가기 처리
        OnBackPressedCallback cb = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(requireContext())
                        .setTitle("앱 종료")
                        .setMessage("앱을 종료하시겠습니까?")
                        .setPositiveButton("종료", (d, w) -> requireActivity().finishAffinity())
                        .setNegativeButton("취소", null)
                        .show();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), cb);
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