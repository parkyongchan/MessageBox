package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.util.Base64;
import android.util.Log;
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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ah.acr.messagebox.adapter.ChatRoomAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.databinding.FragmentChatRoomBinding;
import com.ah.acr.messagebox.util.AvatarHelper;
import com.ah.acr.messagebox.util.AvatarPickerHelper;
import com.ah.acr.messagebox.util.ByteLengthFilter;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ChatRoomFragment extends Fragment {

    private static final String TAG = ChatRoomFragment.class.getSimpleName();
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final int HEADER_AVATAR_SIZE_DP = 38;

    private FragmentChatRoomBinding binding;
    private MsgViewModel msgViewModel;
    private AddressViewModel addressViewModel;
    private ChatRoomAdapter adapter;
    private String mCodeNum;
    private String mContactName;

    // Avatar edit state
    private String mAvatarPath = null;

    // ⭐ 현재 대화방의 대기 메시지 리스트 (FAB 전송용)
    private List<MsgEntity> mContactUnsentMsgs = new ArrayList<>();

    // Gallery launcher
    private ActivityResultLauncher<String> pickImageLauncher;

    // 전송 중 다이얼로그
    private ProgressDialog sendDialog;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        if (getArguments() != null) {
            mCodeNum     = getArguments().getString("code_num", "");
            mContactName = getArguments().getString("contact_name", mCodeNum);
        }

        setupHeader();
        setupRecyclerView();
        setupInputFilters();
        setupObserver();
        setupClickListeners();
        observeAddressForAvatar();
        observeUnsentMessages();  // ⭐ FAB 업데이트
    }


    // ═══════════════════════════════════════════════════════════════
    //   Header Setup
    // ═══════════════════════════════════════════════════════════════

    private void setupHeader() {
        binding.textChatRoomName.setText(mContactName);
        binding.textChatRoomNum.setText(mCodeNum);
        updateHeaderAvatar(mAvatarPath);
    }


    private void observeAddressForAvatar() {
        if (mCodeNum == null || mCodeNum.isEmpty()) return;

        addressViewModel.getAddressByNumbers(mCodeNum)
                .observe(getViewLifecycleOwner(), addressEntity -> {
                    if (addressEntity != null) {
                        mAvatarPath = addressEntity.getAvatarPath();
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


    private void updateHeaderAvatar(String avatarPath) {
        if (binding == null) return;

        try {
            Bitmap avatarBitmap = AvatarHelper.loadOrCreate(
                    getContext(),
                    mCodeNum,
                    mContactName,
                    avatarPath,
                    HEADER_AVATAR_SIZE_DP
            );
            binding.imgChatRoomAvatar.setImageBitmap(avatarBitmap);
            binding.imgChatRoomAvatar.setVisibility(View.VISIBLE);
            binding.textChatRoomAvatar.setVisibility(View.GONE);
        } catch (Exception e) {
            binding.imgChatRoomAvatar.setImageDrawable(null);
            binding.imgChatRoomAvatar.setVisibility(View.GONE);
            binding.textChatRoomAvatar.setVisibility(View.VISIBLE);
            binding.textChatRoomAvatar.setText(
                    AvatarHelper.getInitial(mCodeNum, mContactName)
            );
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   Avatar Edit (기존)
    // ═══════════════════════════════════════════════════════════════

    private void showAvatarMenu() {
        if (mCodeNum == null || mCodeNum.isEmpty()) {
            Toast.makeText(getContext(), "IMEI not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = {"📷 Choose from Gallery", "🔤 Use Initial Avatar"};

        new AlertDialog.Builder(requireContext())
                .setTitle("Avatar")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openGallery();
                    else resetAvatar();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void openGallery() {
        try {
            pickImageLauncher.launch("image/*");
        } catch (Exception e) {
            Toast.makeText(getContext(), "Cannot open gallery", Toast.LENGTH_SHORT).show();
        }
    }


    private void handleImagePicked(Uri uri) {
        if (uri == null) return;
        if (mCodeNum == null || mCodeNum.isEmpty()) return;

        final String imei = mCodeNum;

        new Thread(() -> {
            String savedPath = AvatarPickerHelper.saveAvatarFromUri(getContext(), uri, imei);

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (savedPath != null) {
                    ensureAddressExists(imei, savedPath);
                    Toast.makeText(getContext(), "✅ Avatar updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "❌ Failed to save avatar", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }


    private void ensureAddressExists(String imei, String avatarPath) {
        addressViewModel.getAddressByNumbers(imei).observe(getViewLifecycleOwner(),
                new androidx.lifecycle.Observer<AddressEntity>() {
                    @Override
                    public void onChanged(AddressEntity entity) {
                        addressViewModel.getAddressByNumbers(imei).removeObserver(this);

                        if (entity == null) {
                            AddressEntity newAddr = new AddressEntity(
                                    0, imei,
                                    mContactName != null ? mContactName : imei,
                                    new Date(), null, avatarPath
                            );
                            addressViewModel.insert(newAddr);
                        } else {
                            addressViewModel.updateAvatarPath(imei, avatarPath);
                        }
                    }
                });
    }


    private void resetAvatar() {
        if (mCodeNum == null || mCodeNum.isEmpty()) return;
        AvatarPickerHelper.deleteAvatar(getContext(), mCodeNum);
        addressViewModel.updateAvatarPath(mCodeNum, null);
        Toast.makeText(getContext(), "✅ Avatar reset to initial", Toast.LENGTH_SHORT).show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   Recycler & Input
    // ═══════════════════════════════════════════════════════════════

    private void setupRecyclerView() {
        adapter = new ChatRoomAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        binding.recyclerChatRoom.setLayoutManager(layoutManager);
        binding.recyclerChatRoom.setAdapter(adapter);
    }

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


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ 대기 메시지 관찰 → FAB 업데이트
    // ═══════════════════════════════════════════════════════════════

    private void observeUnsentMessages() {
        msgViewModel.getAllMsgs().observe(getViewLifecycleOwner(), allMsgs -> {
            mContactUnsentMsgs.clear();
            int count = 0;

            if (allMsgs != null && mCodeNum != null) {
                for (MsgEntity msg : allMsgs) {
                    // 현재 대화방 (code_num 일치) + 송신 대기 (is_send_msg=true, is_send=false)
                    if (mCodeNum.equals(msg.getCodeNum())
                            && msg.isSendMsg()
                            && !msg.isSend()) {
                        mContactUnsentMsgs.add(msg);
                        count++;
                    }
                }
            }

            updateFabVisibility(count);
        });
    }


    /** ⭐ FAB 가시성 + 배지 업데이트 */
    private void updateFabVisibility(int count) {
        if (binding == null) return;

        if (count > 0) {
            binding.fabSendPending.setVisibility(View.VISIBLE);
            binding.textFabBadge.setText(String.valueOf(count));
        } else {
            binding.fabSendPending.setVisibility(View.GONE);
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   Click Listeners
    // ═══════════════════════════════════════════════════════════════

    private void setupClickListeners() {

        // ⭐ 뒤로가기 버튼
        binding.btnChatBack.setOnClickListener(v -> {
            try {
                NavHostFragment.findNavController(ChatRoomFragment.this).popBackStack();
            } catch (Exception e) {
                Log.e(TAG, "popBackStack failed: " + e.getMessage(), e);
                requireActivity().onBackPressed();
            }
        });


        // ⭐ 새로고침 버튼 (Inbox 받기)
        binding.btnChatRefresh.setOnClickListener(v -> {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                Toast.makeText(getContext(),
                        "장비가 연결되어 있지 않습니다.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            Toast.makeText(getContext(),
                    "📥 메시지 수신 중...",
                    Toast.LENGTH_SHORT).show();
            Log.v(TAG, "Manual refresh: RECEIVED=?");
        });


        // ⭐ FAB 클릭: 현재 대화방의 대기 메시지 전송
        binding.fabSendPending.setOnClickListener(v -> sendPendingMessages());


        // 헤더 아바타 클릭 → 편집 메뉴
        binding.frameChatRoomAvatar.setOnClickListener(v -> showAvatarMenu());


        // 전송 버튼 (기존 → 저장만)
        binding.btnChatSend.setOnClickListener(v -> {
            String title = binding.editChatTitle.getText().toString().trim();
            String msg   = binding.editChatMsg.getText().toString().trim();

            if (msg.isEmpty()) {
                Toast.makeText(getContext(),
                        "Please enter a message.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            MsgEntity newMsg = new MsgEntity(
                    0, true, mCodeNum,
                    title.isEmpty() ? null : title,
                    msg,
                    new Date(),
                    null, null,
                    false, false, false
            );

            msgViewModel.insert(newMsg, success -> {
                if (success) {
                    requireActivity().runOnUiThread(() -> {
                        binding.editChatTitle.setText("");
                        binding.editChatMsg.setText("");
                        Toast.makeText(getContext(),
                                "💾 저장됨 (▶ 버튼으로 전송하세요)",
                                Toast.LENGTH_SHORT).show();
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
                        try {
                            NavHostFragment.findNavController(ChatRoomFragment.this).popBackStack();
                        } catch (Exception e) {
                            requireActivity().onBackPressed();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ FAB: 대기 메시지 전송 (MsgBoxFragment 로직 참고)
    // ═══════════════════════════════════════════════════════════════

    private void sendPendingMessages() {

        if (mContactUnsentMsgs.isEmpty()) {
            Toast.makeText(getContext(),
                    "전송 대기 메시지가 없습니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        BleDevice bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
        if (bleDevice == null || !BleManager.getInstance().isConnected(bleDevice)) {
            Toast.makeText(getContext(),
                    "장비가 연결되어 있지 않습니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 오래된 순서대로 정렬
        List<MsgEntity> unsentSorted = new ArrayList<>(mContactUnsentMsgs);
        unsentSorted.sort((a, b) -> {
            if (a.getCreateAt() == null) return 1;
            if (b.getCreateAt() == null) return -1;
            return a.getCreateAt().compareTo(b.getCreateAt());
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("메시지 전송")
                .setMessage(unsentSorted.size() + "개의 대기 메시지를 전송하시겠습니까?")
                .setPositiveButton("전송", (d, w) -> doSendPending(unsentSorted))
                .setNegativeButton("취소", null)
                .show();
    }


    private void doSendPending(List<MsgEntity> unsentSorted) {

        // 진행 다이얼로그
        sendDialog = new ProgressDialog(requireContext());
        sendDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        sendDialog.setCancelable(false);
        sendDialog.setMessage("전송 중: " + unsentSorted.size() + "개 메시지");

        // 바이너리 패킷 빌드 (MsgBoxFragment 와 동일 포맷)
        List<String> msgList = new ArrayList<>();
        for (MsgEntity msg : unsentSorted) {
            String codeNum = msg.getCodeNum() != null ? msg.getCodeNum() : "";
            String title   = msg.getTitle() != null ? msg.getTitle() : "";
            String message = msg.getMsg() != null ? msg.getMsg() : "";

            Log.v(TAG, "Prep: id=" + msg.getId() + " to=" + codeNum + " msg=" + message);

            ByteBuf buffer = Unpooled.buffer();
            buffer.writeByte(0x07);
            buffer.writeByte(codeNum.getBytes(StandardCharsets.US_ASCII).length);
            buffer.writeCharSequence(codeNum, StandardCharsets.US_ASCII);
            buffer.writeByte(title.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(title, StandardCharsets.UTF_8);
            buffer.writeByte(message.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(message, StandardCharsets.UTF_8);

            byte[] body = new byte[buffer.readableBytes()];
            buffer.readBytes(body);
            String sms = String.format("SENDING=%d,%s",
                    msg.getId(), Base64.encodeToString(body, Base64.NO_WRAP));
            msgList.add(sms);
        }

        if (msgList.isEmpty()) {
            Log.v(TAG, "No messages to send");
            return;
        }

        sendDialog.show();

        // 백그라운드 스레드에서 순차 전송
        new Thread(new SendRunnable(msgList)).start();
    }


    /** 순차 전송 Runnable (MsgBoxFragment 로직과 동일) */
    private class SendRunnable implements Runnable {
        private final List<String> msgList;
        private final Object lock = new Object();
        private final BleDevice bleDevice;
        private BluetoothGattCharacteristic characteristic;
        private int curCnt = 0;
        private int totalCnt;

        SendRunnable(List<String> msgList) {
            this.msgList = msgList;
            this.bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
            this.totalCnt = msgList.size();

            for (BluetoothGattCharacteristic c : BLE.INSTANCE.getSelDeviceGatt()) {
                int props = c.getProperties();
                if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    this.characteristic = c;
                    break;
                }
            }

            // OutboxMsgStatus 관찰 (전송 완료 콜백)
            BLE.INSTANCE.getOutboxMsgStatus().observe(getViewLifecycleOwner(), sReceive -> {
                if (sReceive.startsWith("SENDING=")) {
                    String msg = sReceive.substring(8);
                    String[] vals = msg.split(",");
                    if (vals.length >= 2 && vals[1].equals("OK")) {
                        int id = Integer.parseInt(vals[0]);
                        msgViewModel.updateSend(id);
                    }
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            });
        }

        @Override
        public void run() {
            Handler handler = new Handler(Looper.getMainLooper());

            for (String msg : msgList) {
                String sendMsg = String.format("%s\n",
                        Base64.encodeToString(msg.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));

                if (characteristic == null) {
                    Log.e(TAG, "characteristic is null, aborting");
                    break;
                }

                BleManager.getInstance().write(
                        bleDevice,
                        SERVICE_UUID.toString(),
                        characteristic.getUuid().toString(),
                        sendMsg.getBytes(),
                        new BleWriteCallback() {
                            @Override
                            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                                handler.post(() ->
                                        Log.v("WRITE", "success curr=" + current + " total=" + total));
                            }
                            @Override
                            public void onWriteFailure(BleException exception) {
                                handler.post(() -> Log.e("WRITE", "failure: " + exception));
                            }
                        });

                synchronized (lock) {
                    try {
                        lock.wait(10000);  // 타임아웃 10초
                        handler.post(() -> {
                            curCnt++;
                            if (curCnt == totalCnt && sendDialog != null && sendDialog.isShowing()) {
                                sendDialog.dismiss();
                                Toast.makeText(getContext(),
                                        "✅ " + totalCnt + "개 메시지 전송 완료",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Thread interrupted: " + e);
                        break;
                    }
                }
            }

            // 최종 안전처리
            handler.post(() -> {
                if (sendDialog != null && sendDialog.isShowing()) {
                    sendDialog.dismiss();
                }
            });
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sendDialog != null && sendDialog.isShowing()) {
            sendDialog.dismiss();
        }
        binding = null;
    }
}
