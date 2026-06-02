package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.os.Build;
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

    // Pending messages list for current contact (for FAB send)
    private List<MsgEntity> mContactUnsentMsgs = new ArrayList<>();

    // Gallery launcher
    private ActivityResultLauncher<String> pickImageLauncher;

    // Sending progress dialog
    private ProgressDialog sendDialog;

    // ECHO 수신 시 채팅 화면 자동 새로고침 (지도와 동일 방식)
    private BroadcastReceiver mEchoReceiver;


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
        observeUnsentMessages();  // FAB update
        registerEchoReceiver();   // ECHO 수신 → 자동 새로고침
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
    //   Avatar Edit
    // ═══════════════════════════════════════════════════════════════

    private void showAvatarMenu() {
        if (mCodeNum == null || mCodeNum.isEmpty()) {
            // Localized
            Toast.makeText(getContext(),
                    getString(R.string.chat_avatar_no_imei),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Localized options
        String[] options = {
                getString(R.string.chat_avatar_option_gallery),
                getString(R.string.chat_avatar_option_initial)
        };

        // Localized
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.chat_avatar_dialog_title))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openGallery();
                    else resetAvatar();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void openGallery() {
        try {
            pickImageLauncher.launch("image/*");
        } catch (Exception e) {
            // Localized
            Toast.makeText(getContext(),
                    getString(R.string.chat_avatar_no_gallery),
                    Toast.LENGTH_SHORT).show();
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
                    // Localized
                    Toast.makeText(getContext(),
                            getString(R.string.chat_avatar_updated),
                            Toast.LENGTH_SHORT).show();
                } else {
                    // Localized
                    Toast.makeText(getContext(),
                            getString(R.string.chat_avatar_save_fail),
                            Toast.LENGTH_SHORT).show();
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
        // Localized
        Toast.makeText(getContext(),
                getString(R.string.chat_avatar_reset),
                Toast.LENGTH_SHORT).show();
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

        // 대용량 체크박스: 켜면 본문 200B 필터 해제, 끄면 복원
        binding.checkLargeMsg.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                binding.editChatMsg.setFilters(new InputFilter[]{});
            } else {
                binding.editChatMsg.setFilters(new InputFilter[]{ new ByteLengthFilter(200, "UTF-8") });
            }
            updateByteCount();
        });
        // 글자수 실시간 표시
        binding.editChatMsg.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable e) { updateByteCount(); }
        });
        updateByteCount();
    }

    private void updateByteCount() {
        if (binding == null) return;
        int bytes = binding.editChatMsg.getText().toString().getBytes(StandardCharsets.UTF_8).length;
        boolean large = binding.checkLargeMsg.isChecked();
        if (large) {
            int parts = Math.max(1, (int) Math.ceil(bytes / 200.0));
            binding.textByteCount.setText(bytes + " B / SBD " + parts);
            binding.textByteCount.setTextColor(0xFF00E5D1);
        } else {
            binding.textByteCount.setText(bytes + "/200 B");
            binding.textByteCount.setTextColor(bytes > 180 ? 0xFFFF5252 : 0xFF4A5F78);
        }
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
    //   ECHO 수신 → 채팅 화면 자동 새로고침 (LocationFragment와 동일 방식)
    //   배경: getMsgsByContact는 address 조인(@Relation) 쿼리라
    //         백그라운드 insert 시 LiveData invalidation이 누락될 수 있음.
    //         메시지 저장 시 Service가 쏘는 ECHO 브로드캐스트를 받아
    //         observe를 다시 붙여 강제 갱신한다.
    // ═══════════════════════════════════════════════════════════════
    private void registerEchoReceiver() {
        mEchoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (com.ah.acr.messagebox.service.TytoConnectService
                        .BROADCAST_ECHO_RECEIVED.equals(action)) {
                    Log.v(TAG, "📨 ECHO 수신 → 채팅 자동 새로고침");
                    if (binding != null && mCodeNum != null) {
                        setupObserver();  // observe 재등록 → 즉시 갱신
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(
                com.ah.acr.messagebox.service.TytoConnectService.BROADCAST_ECHO_RECEIVED);

        Context ctx = requireContext();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(mEchoReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ctx.registerReceiver(mEchoReceiver, filter);
            }
            Log.v(TAG, "📡 ECHO 수신기 등록");
        } catch (Exception e) {
            Log.e(TAG, "ECHO 수신기 등록 실패: " + e.getMessage());
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   Observe pending messages -> update FAB
    // ═══════════════════════════════════════════════════════════════

    private void observeUnsentMessages() {
        msgViewModel.getAllMsgs().observe(getViewLifecycleOwner(), allMsgs -> {
            mContactUnsentMsgs.clear();
            int count = 0;

            if (allMsgs != null && mCodeNum != null) {
                for (MsgEntity msg : allMsgs) {
                    // Current chat (matching code_num) + send pending (is_send_msg=true, is_send=false)
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


    /** Update FAB visibility + badge */
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

        // Back button
        binding.btnChatBack.setOnClickListener(v -> {
            try {
                NavHostFragment.findNavController(ChatRoomFragment.this).popBackStack();
            } catch (Exception e) {
                Log.e(TAG, "popBackStack failed: " + e.getMessage(), e);
                requireActivity().onBackPressed();
            }
        });


        // Refresh button (receive Inbox)
        binding.btnChatRefresh.setOnClickListener(v -> {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                // Localized
                Toast.makeText(getContext(),
                        getString(R.string.chat_ble_not_connected),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            // Localized
            Toast.makeText(getContext(),
                    getString(R.string.chat_refreshing),
                    Toast.LENGTH_SHORT).show();
            Log.v(TAG, "Manual refresh: RECEIVED=?");
        });


        // FAB click: send pending messages for current contact
        binding.fabSendPending.setOnClickListener(v -> sendPendingMessages());


        // Header avatar click -> edit menu
        binding.frameChatRoomAvatar.setOnClickListener(v -> showAvatarMenu());


        // Send button (now save only)
        binding.btnChatSend.setOnClickListener(v -> {
            String title = binding.editChatTitle.getText().toString().trim();
            String msg   = binding.editChatMsg.getText().toString().trim();

            // 대용량 모드: 저장 없이 즉시 분할 전송 (상대 IMEI로)
            if (binding.checkLargeMsg.isChecked()) {
                if (msg.isEmpty()) {
                    Toast.makeText(getContext(), getString(R.string.chat_input_empty), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                    Toast.makeText(getContext(), getString(R.string.chat_ble_not_connected), Toast.LENGTH_SHORT).show();
                    return;
                }
                ((MainActivity) requireActivity()).sendLargeMsg(mCodeNum, msg);
                binding.editChatTitle.setText("");
                binding.editChatMsg.setText("");
                Toast.makeText(getContext(), "대용량 전송 시작", Toast.LENGTH_SHORT).show();
                return;
            }

            if (msg.isEmpty()) {
                // Localized
                Toast.makeText(getContext(),
                        getString(R.string.chat_input_empty),
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
                        // Localized
                        Toast.makeText(getContext(),
                                getString(R.string.chat_msg_saved),
                                Toast.LENGTH_SHORT).show();
                    });
                }
                return null;
            });
        });


        // Delete all
        binding.btnChatDeleteAll.setOnClickListener(v -> {
            // Localized
            new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.chat_delete_chat_title))
                    .setMessage(getString(R.string.chat_delete_chat_msg, mContactName))
                    .setPositiveButton(getString(R.string.addr_btn_delete), (dialog, which) -> {
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
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
        });
    }


    // ═══════════════════════════════════════════════════════════════
    //   FAB: Send pending messages (refer to MsgBoxFragment logic)
    // ═══════════════════════════════════════════════════════════════

    private void sendPendingMessages() {

        if (mContactUnsentMsgs.isEmpty()) {
            // Localized
            Toast.makeText(getContext(),
                    getString(R.string.chat_send_no_pending),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        BleDevice bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
        if (bleDevice == null || !BleManager.getInstance().isConnected(bleDevice)) {
            // Localized
            Toast.makeText(getContext(),
                    getString(R.string.chat_ble_not_connected),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Sort by oldest first
        List<MsgEntity> unsentSorted = new ArrayList<>(mContactUnsentMsgs);
        unsentSorted.sort((a, b) -> {
            if (a.getCreateAt() == null) return 1;
            if (b.getCreateAt() == null) return -1;
            return a.getCreateAt().compareTo(b.getCreateAt());
        });

        // Localized
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.chat_send_dialog_title))
                .setMessage(getString(R.string.chat_send_dialog_msg, unsentSorted.size()))
                .setPositiveButton(getString(R.string.chat_btn_send), (d, w) -> doSendPending(unsentSorted))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void doSendPending(List<MsgEntity> unsentSorted) {

        // Progress dialog - Localized
        sendDialog = new ProgressDialog(requireContext());
        sendDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        sendDialog.setCancelable(false);
        sendDialog.setMessage(getString(R.string.chat_send_progress, unsentSorted.size()));

        // Build binary packets (same format as MsgBoxFragment)
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
            android.util.Log.d("ACK-PROBE", "송신 id=" + msg.getId() + " sms=" + sms);
        }

        if (msgList.isEmpty()) {
            Log.v(TAG, "No messages to send");
            return;
        }

        sendDialog.show();

        // Sequential send on background thread
        new Thread(new SendRunnable(msgList)).start();
    }


    /** Sequential send Runnable (same logic as MsgBoxFragment) */
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

            // Observe OutboxMsgStatus (send-complete callback)
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
                        lock.wait(10000);  // 10 second timeout
                        handler.post(() -> {
                            curCnt++;
                            if (curCnt == totalCnt && sendDialog != null && sendDialog.isShowing()) {
                                sendDialog.dismiss();
                                // Localized
                                Toast.makeText(getContext(),
                                        getString(R.string.chat_send_complete, totalCnt),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Thread interrupted: " + e);
                        break;
                    }
                }
            }

            // Final safety cleanup
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
        if (mEchoReceiver != null) {
            try {
                requireContext().unregisterReceiver(mEchoReceiver);
            } catch (Exception e) {
                // ignore
            }
            mEchoReceiver = null;
        }
        binding = null;
    }
}
