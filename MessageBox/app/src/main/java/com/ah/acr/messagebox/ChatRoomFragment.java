package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputFilter;
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
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.databinding.FragmentChatRoomBinding;
import com.ah.acr.messagebox.util.ByteLengthFilter;

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
        setupInputFilters();      // ⭐ 신규: 바이트 제한
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

    /** ⭐ UTF-8 바이트 제한 적용 (영어 1byte / 한글 3byte) */
    private void setupInputFilters() {
        // 제목: 20 byte
        binding.editChatTitle.setFilters(new InputFilter[]{
                new ByteLengthFilter(20, "UTF-8")
        });

        // 본문: 200 byte
        binding.editChatMsg.setFilters(new InputFilter[]{
                new ByteLengthFilter(200, "UTF-8")
        });
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
                Toast.makeText(getContext(), "Please enter a message.", Toast.LENGTH_SHORT).show();
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
                                "Message saved (send from Inbox)", Toast.LENGTH_SHORT).show();
                    });
                }
                return null;
            });
        });

        // 전체 삭제
        binding.btnChatDeleteAll.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete chat")
                    .setMessage("Delete all messages with " + mContactName + "?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        List<com.ah.acr.messagebox.database.MsgWithAddress> msgs =
                                adapter.getCurrentList();
                        for (com.ah.acr.messagebox.database.MsgWithAddress m : msgs) {
                            msgViewModel.delete(m.getMsg());
                        }
                        requireActivity().onBackPressed();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}