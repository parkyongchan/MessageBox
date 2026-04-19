package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ah.acr.messagebox.adapter.ChatRoomAdapter;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.databinding.FragmentChatRoomBinding;
import com.ah.acr.messagebox.util.AvatarHelper;
import com.ah.acr.messagebox.util.AvatarPickerHelper;
import com.ah.acr.messagebox.util.ByteLengthFilter;

import java.util.Date;
import java.util.List;

public class ChatRoomFragment extends Fragment {

    private static final int HEADER_AVATAR_SIZE_DP = 38;

    private FragmentChatRoomBinding binding;
    private MsgViewModel msgViewModel;
    private AddressViewModel addressViewModel;
    private ChatRoomAdapter adapter;
    private String mCodeNum;
    private String mContactName;

    // ⭐ Avatar edit state
    private String mAvatarPath = null;  // 현재 상대방 아바타 경로

    // ⭐ Gallery launcher
    private ActivityResultLauncher<String> pickImageLauncher;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ⭐ Register gallery launcher
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> handleImagePicked(uri)
        );
    }


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
        setupInputFilters();
        setupObserver();
        setupClickListeners();
        observeAddressForAvatar();  // ⭐ NEW
    }


    private void setupHeader() {
        // 이름 표시
        binding.textChatRoomName.setText(mContactName);
        binding.textChatRoomNum.setText(mCodeNum);

        // ⭐ 초기 아바타 (AddressEntity 로드 전 기본값)
        updateHeaderAvatar(mAvatarPath);
    }


    /**
     * ⭐ AddressEntity 관찰 → avatarPath 가져오기 → 아바타 갱신
     */
    private void observeAddressForAvatar() {
        if (mCodeNum == null || mCodeNum.isEmpty()) return;

        addressViewModel.getAddressByNumbers(mCodeNum)
                .observe(getViewLifecycleOwner(), addressEntity -> {
                    if (addressEntity != null) {
                        mAvatarPath = addressEntity.getAvatarPath();
                        // 닉네임이 DB에 있으면 업데이트
                        String nickname = addressEntity.getNumbersNic();
                        if (nickname != null && !nickname.trim().isEmpty()) {
                            mContactName = nickname;
                            binding.textChatRoomName.setText(nickname);
                        }
                    } else {
                        mAvatarPath = null;
                    }
                    updateHeaderAvatar(mAvatarPath);
                });
    }


    /**
     * ⭐ 헤더 아바타 업데이트 (AvatarHelper 사용)
     */
    private void updateHeaderAvatar(String avatarPath) {
        if (binding == null) return;

        try {
            Bitmap avatarBitmap = AvatarHelper.loadOrCreate(
                    getContext(),
                    mCodeNum,         // IMEI (뒤 4자리 fallback)
                    mContactName,     // Nickname (첫 글자 우선)
                    avatarPath,       // 커스텀 이미지
                    HEADER_AVATAR_SIZE_DP
            );
            binding.imgChatRoomAvatar.setImageBitmap(avatarBitmap);
            binding.imgChatRoomAvatar.setVisibility(View.VISIBLE);
            binding.textChatRoomAvatar.setVisibility(View.GONE);
        } catch (Exception e) {
            // Fallback
            binding.imgChatRoomAvatar.setImageDrawable(null);
            binding.imgChatRoomAvatar.setVisibility(View.GONE);
            binding.textChatRoomAvatar.setVisibility(View.VISIBLE);
            binding.textChatRoomAvatar.setText(
                    AvatarHelper.getInitial(mCodeNum, mContactName)
            );
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ AVATAR EDIT
    // ═══════════════════════════════════════════════════════════════

    /**
     * 아바타 편집 메뉴 (갤러리 / 초기화 / 취소)
     */
    private void showAvatarMenu() {
        if (mCodeNum == null || mCodeNum.isEmpty()) {
            Toast.makeText(getContext(),
                    "IMEI not available",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = {
                "📷 Choose from Gallery",
                "🔤 Use Initial Avatar"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Avatar")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openGallery();
                    } else {
                        resetAvatar();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void openGallery() {
        try {
            pickImageLauncher.launch("image/*");
        } catch (Exception e) {
            Toast.makeText(getContext(),
                    "Cannot open gallery",
                    Toast.LENGTH_SHORT).show();
        }
    }


    private void handleImagePicked(Uri uri) {
        if (uri == null) return;
        if (mCodeNum == null || mCodeNum.isEmpty()) return;

        final String imei = mCodeNum;

        new Thread(() -> {
            String savedPath = AvatarPickerHelper.saveAvatarFromUri(
                    getContext(), uri, imei);

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (savedPath != null) {
                    // DB 업데이트 (AddressEntity가 있어야 함)
                    // AddressEntity 없으면 먼저 생성
                    ensureAddressExists(imei, savedPath);

                    Toast.makeText(getContext(),
                            "✅ Avatar updated",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(),
                            "❌ Failed to save avatar",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }


    /**
     * ⭐ AddressEntity가 없으면 생성 후 avatarPath 업데이트
     */
    private void ensureAddressExists(String imei, String avatarPath) {
        addressViewModel.getAddressByNumbers(imei).observe(getViewLifecycleOwner(),
                new androidx.lifecycle.Observer<AddressEntity>() {
                    @Override
                    public void onChanged(AddressEntity entity) {
                        // 한 번만 처리 후 observer 제거
                        addressViewModel.getAddressByNumbers(imei).removeObserver(this);

                        if (entity == null) {
                            // 연락처 없음 → 생성
                            AddressEntity newAddr = new AddressEntity(
                                    0,
                                    imei,
                                    mContactName != null ? mContactName : imei,
                                    new Date(),
                                    null,
                                    avatarPath
                            );
                            addressViewModel.insert(newAddr);
                        } else {
                            // 이미 있음 → avatarPath만 업데이트
                            addressViewModel.updateAvatarPath(imei, avatarPath);
                        }
                    }
                });
    }


    private void resetAvatar() {
        if (mCodeNum == null || mCodeNum.isEmpty()) return;

        // 파일 삭제
        AvatarPickerHelper.deleteAvatar(getContext(), mCodeNum);

        // DB 업데이트
        addressViewModel.updateAvatarPath(mCodeNum, null);

        Toast.makeText(getContext(),
                "✅ Avatar reset to initial",
                Toast.LENGTH_SHORT).show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   EXISTING METHODS
    // ═══════════════════════════════════════════════════════════════

    private void setupRecyclerView() {
        adapter = new ChatRoomAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        binding.recyclerChatRoom.setLayoutManager(layoutManager);
        binding.recyclerChatRoom.setAdapter(adapter);
    }

    /** UTF-8 바이트 제한 적용 (영어 1byte / 한글 3byte) */
    private void setupInputFilters() {
        binding.editChatTitle.setFilters(new InputFilter[]{
                new ByteLengthFilter(20, "UTF-8")
        });
        binding.editChatMsg.setFilters(new InputFilter[]{
                new ByteLengthFilter(200, "UTF-8")
        });
    }

    private void setupObserver() {
        msgViewModel.getMsgsByContact(mCodeNum).observe(getViewLifecycleOwner(), msgs -> {
            adapter.submitList(msgs);
            if (msgs != null && msgs.size() > 0) {
                binding.recyclerChatRoom.scrollToPosition(msgs.size() - 1);
            }
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

        // ⭐ 헤더 아바타 클릭 → 편집 메뉴
        binding.frameChatRoomAvatar.setOnClickListener(v -> showAvatarMenu());

        // 전송 버튼
        binding.btnChatSend.setOnClickListener(v -> {
            String title = binding.editChatTitle.getText().toString().trim();
            String msg   = binding.editChatMsg.getText().toString().trim();

            if (msg.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a message.", Toast.LENGTH_SHORT).show();
                return;
            }

            MsgEntity newMsg = new MsgEntity(
                    0,
                    true,
                    mCodeNum,
                    title.isEmpty() ? null : title,
                    msg,
                    new Date(),
                    null,
                    null,
                    false,
                    false,
                    false
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
