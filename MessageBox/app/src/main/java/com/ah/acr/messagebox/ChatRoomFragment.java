package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ah.acr.messagebox.adapter.ChatRoomAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.databinding.FragmentChatRoomBinding;

import java.util.Date;
import java.util.List;

public class ChatRoomFragment extends Fragment {

    private FragmentChatRoomBinding binding;
    private MsgViewModel msgViewModel;
    private AddressViewModel addressViewModel;
    private ChatRoomAdapter adapter;
    private String mCodeNum;
    private String mContactName;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatRoomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        msgViewModel     = new ViewModelProvider(this).get(MsgViewModel.class);
        addressViewModel = new ViewModelProvider(requireActivity()).get(AddressViewModel.class);

        // 전달받은 codeNum
        if (getArguments() != null) {
            mCodeNum     = getArguments().getString("code_num", "");
            mContactName = getArguments().getString("contact_name", mCodeNum);
        }

        setupHeader();
        setupRecyclerView();
        setupObserver();
        setupClickListeners();
    }

    private void setupHeader() {
        // 이름 표시
        binding.textChatRoomName.setText(mContactName);
        binding.textChatRoomNum.setText(mCodeNum);

        // 이니셜 아바타
        String initial = (mContactName != null && mContactName.length() > 0)
                ? mContactName.substring(0, 1).toUpperCase() : "?";
        binding.textChatRoomAvatar.setText(initial);
    }

    private void setupRecyclerView() {
        adapter = new ChatRoomAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true); // 최신 메시지가 하단에
        binding.recyclerChatRoom.setLayoutManager(layoutManager);
        binding.recyclerChatRoom.setAdapter(adapter);
    }

    private void setupObserver() {
        msgViewModel.getMsgsByContact(mCodeNum).observe(getViewLifecycleOwner(), msgs -> {
            adapter.submitList(msgs);
            // 최신 메시지로 스크롤
            if (msgs != null && msgs.size() > 0) {
                binding.recyclerChatRoom.scrollToPosition(msgs.size() - 1);
            }
            // 읽음 처리
            if (msgs != null) {
                for (com.ah.acr.messagebox.database.MsgWithAddress m : msgs) {
                    if (!m.getMsg().isRead() && !m.getMsg().isSendMsg()) {
                        msgViewModel.updateRead(m.getMsg().getId());
                    }
                }
            }
        });
    }

    private void setupClickListeners() {

        // 전송 버튼
        binding.btnChatSend.setOnClickListener(v -> {
            String title = binding.editChatTitle.getText().toString().trim();
            String msg   = binding.editChatMsg.getText().toString().trim();

            if (msg.isEmpty()) {
                Toast.makeText(getContext(), "메시지를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // DB 저장
            MsgEntity newMsg = new MsgEntity(
                    0,
                    true,        // isSendMsg
                    mCodeNum,    // codeNum (수신처)
                    title.isEmpty() ? null : title,
                    msg,
                    new Date(),  // createAt
                    null,        // receiveAt
                    null,        // sendDeviceAt
                    false,       // isRead
                    false,       // isSend (미전송 상태)
                    false        // isDeviceSend
            );

            msgViewModel.insert(newMsg, success -> {
                if (success) {
                    requireActivity().runOnUiThread(() -> {
                        binding.editChatTitle.setText("");
                        binding.editChatMsg.setText("");
                        Toast.makeText(getContext(),
                                "메시지 저장됨 (메시지함에서 전송하세요)", Toast.LENGTH_SHORT).show();
                    });
                }
                return null; // Kotlin Unit 반환
            });
        });

        // 전체 삭제
        binding.btnChatDeleteAll.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("대화 삭제")
                    .setMessage(mContactName + "와의 대화를 모두 삭제하시겠습니까?")
                    .setPositiveButton("삭제", (dialog, which) -> {
                        List<com.ah.acr.messagebox.database.MsgWithAddress> msgs =
                                adapter.getCurrentList();
                        for (com.ah.acr.messagebox.database.MsgWithAddress m : msgs) {
                            msgViewModel.delete(m.getMsg());
                        }
                        requireActivity().onBackPressed();
                    })
                    .setNegativeButton("취소", null)
                    .show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}