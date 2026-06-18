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
    // [gap-fill] 미완 대용량 배너 주기 갱신 (5초). 송신 없음 — 표시 전용.
    private final android.os.Handler mGapfillHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int mGapfillMsgId = -1;   // 현재 배너가 가리키는 미완 msgId (1-C 재요청용)
    private static final long GAPFILL_STALE_MS = 420000;   /* 7분: 위성 감도지연 흡수 */   // 마지막 조각 후 60초 지나면 "누락 의심"
    private final Runnable mGapfillTick = new Runnable() {
        @Override public void run() {
            updateGapfillBanner();
            updateMoresendBanner();
            mGapfillHandler.postDelayed(this, 5000);
        }
    };

    private int mMoresendMsgId = -1;
    // [fileMsg] 첨부된 파일/사진 바이트 + 이름 (3-C에서 채움, 송신 시 사용)
    private byte[] mAttachBytes = null;
    private String mAttachName = null;
    private final java.util.Map<Integer,Long> mBannerLockUntil = new java.util.HashMap<>();   // msgId -> 잠금 해제시각
    private static final long BANNER_LOCK_MS = 60000;   // [Step5] 재송신/재요청 클릭 후 60초 배너 잠금

    /** [Step5] MO 재송신 대기 배너 갱신. 잠금 중이면 숨김. */
    private void updateMoresendBanner() {
        if (binding == null || !(getActivity() instanceof MainActivity)) return;
        int[] info = null;
        try { info = ((MainActivity) getActivity()).getPendingMoResend(mCodeNum); } catch (Exception e) { /* ignore */ }
        if (info == null) {
            mMoresendMsgId = -1;
            binding.bannerMoresend.setVisibility(View.GONE);
            return;
        }
        int msgId = info[0], missing = info[1], total = info[2];
        // 잠금 체크
        Long lock = mBannerLockUntil.get(msgId);
        if (lock != null && lock > System.currentTimeMillis()) {
            binding.bannerMoresend.setVisibility(View.GONE);   // 잠금 중 — 숨김
            return;
        }
        mMoresendMsgId = msgId;
        MainActivity _act = (MainActivity) getActivity();
        if (_act.isAutoResend() && _act.getAutoMoCount(msgId) > 0) {
            // [Step2-auto] 자동 진행 중 — 진행 표시, 버튼 숨김
            binding.textMoresendInfo.setText("\uD83D\uDCE4 SEND  Auto-resending " + _act.getAutoMoCount(msgId) + "/" + MainActivity.AUTO_RESEND_MAX + " ...");
            binding.btnMoresendResend.setVisibility(View.GONE);
            binding.btnMoresendDiscard.setVisibility(View.GONE);
        } else {
            binding.textMoresendInfo.setText("\uD83D\uDCE4 SEND  Send incomplete: " + (total - missing) + "/" + total + " (" + missing + " need resend) \u00B7 " + (info[3]/60000) + " min ago");
            binding.btnMoresendResend.setVisibility(View.VISIBLE);
            binding.btnMoresendDiscard.setVisibility(View.VISIBLE);
        }
        binding.bannerMoresend.setVisibility(View.VISIBLE);
    }

    private void updateGapfillBanner() {
        if (binding == null || mCodeNum == null || mCodeNum.isEmpty()) return;
        // [STALE] 내 대용량 송신 중이면 배너 억제 (위치정보는 무관, 자동 제외)
        if (getActivity() instanceof MainActivity && ((MainActivity) getActivity()).isLargeSending()) {
            binding.bannerGapfill.setVisibility(View.GONE); return;
        }
        long[] info = null;
        try {
            if (getActivity() instanceof MainActivity) {
                info = ((MainActivity) getActivity()).getIncompleteLargeMsg(mCodeNum);
            }
        } catch (Exception e) { /* ignore */ }
        if (info == null) {
            mGapfillMsgId = -1;
            binding.bannerGapfill.setVisibility(View.GONE);
            return;
        }
        int msgId = (int) info[0]; int received = (int) info[1];
        int total = (int) info[2]; long elapsed = info[3];
        mGapfillMsgId = msgId;
        Long _mtLock = mBannerLockUntil.get(msgId);
        if (_mtLock != null && _mtLock > System.currentTimeMillis()) { binding.bannerGapfill.setVisibility(View.GONE); return; }   // [Step5] MT 잠금 중 숨김
        if (elapsed < GAPFILL_STALE_MS) {
            // 아직 받는 중 — 진행 표시만
            binding.textGapfillInfo.setText("\uD83D\uDCE5 RECV  Receiving: " + received + "/" + total + " ...");
            binding.btnGapfillResend.setVisibility(View.GONE);
            binding.bannerGapfill.setVisibility(View.VISIBLE);
        } else {
            int missing = total - received;
            MainActivity _act = (MainActivity) getActivity();
            if (_act.isAutoResend() && !_act.isAutoMtGaveUp(msgId)) {
                // [Step2-auto] 자동 모드 — 스케줄 등록(중복 방지됨) + 진행 표시, 버튼 숨김
                _act.scheduleAutoMtGapfill(msgId);
                int ac = _act.getAutoMtCount(msgId);
                binding.textGapfillInfo.setText("\uD83D\uDCE5 RECV  Auto-resending " + ac + "/" + MainActivity.AUTO_RESEND_MAX + " ...");
                binding.btnGapfillResend.setVisibility(View.GONE);
                binding.btnGapfillDiscard.setVisibility(View.GONE);
            } else {
                // [무인포기숨김] 자동모드인데 여기(else) 도달 = 3회 포기(gaveUp). 무인장비는 수동 재수신/버림 버튼 무의미 → 배너 통째 숨김
                if (_act.isAutoResend()) { binding.bannerGapfill.setVisibility(View.GONE); return; }
                binding.textGapfillInfo.setText("\uD83D\uDCE5 RECV  Incomplete: " + received + "/" + total + " (" + missing + " missing) \u00B7 " + (elapsed/60000) + " min ago");
                binding.btnGapfillResend.setVisibility(View.VISIBLE);
                binding.btnGapfillDiscard.setVisibility(View.VISIBLE);
            }
            binding.bannerGapfill.setVisibility(View.VISIBLE);
        }
    }

    private String mContactName;

    // Avatar edit state
    private String mAvatarPath = null;

    // Pending messages list for current contact (for FAB send)
    private List<MsgEntity> mContactUnsentMsgs = new ArrayList<>();

    // Gallery launcher
    private ActivityResultLauncher<String> pickImageLauncher;
    // [fileMsg 3-C] 파일/사진 첨부용 launcher (아바타용 pickImageLauncher와 별개)
    private ActivityResultLauncher<String> mAttachLauncher;

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
        // [fileMsg 3-C] 파일/사진 첨부 선택 결과 처리
        mAttachLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> handleAttachPicked(uri)
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
        updateTitleVisibility();   // 초기 제목칸 상태 (ACK ON이면 숨김)
    }

    @Override
    public void onResume() {
        super.onResume();
        // [gap-fill] 배너 갱신 시작 + 버튼(1-C 전까지 Toast만)
        mGapfillHandler.removeCallbacks(mGapfillTick);
        mGapfillHandler.post(mGapfillTick);
        if (binding != null) {
            // ─── MT gap-fill: Resend (60초 잠금) ───
            binding.btnGapfillResend.setOnClickListener(v -> {
                if (mGapfillMsgId < 0 || !(getActivity() instanceof MainActivity)) return;
                int queued = ((MainActivity) getActivity()).enqueueGapFillRequests(mGapfillMsgId);
                if (queued > 0) {
                    mBannerLockUntil.put(mGapfillMsgId, System.currentTimeMillis() + BANNER_LOCK_MS);
                    binding.bannerGapfill.setVisibility(View.GONE);
                    android.widget.Toast.makeText(getContext(), "Re-requesting " + queued + " chunk(s)", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(getContext(), "Nothing to re-request", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            // ─── MT gap-fill: Discard (포기) ───
            binding.btnGapfillDiscard.setOnClickListener(v -> {
                if (mGapfillMsgId >= 0 && getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).discardGapfill(mGapfillMsgId);
                binding.bannerGapfill.setVisibility(View.GONE);
            });
            // ─── MO resend: Resend (60초 잠금) ───
            binding.btnMoresendResend.setOnClickListener(v -> {
                if (mMoresendMsgId < 0 || !(getActivity() instanceof MainActivity)) return;
                int n = ((MainActivity) getActivity()).triggerMoResend(mMoresendMsgId);
                if (n > 0) {
                    mBannerLockUntil.put(mMoresendMsgId, System.currentTimeMillis() + BANNER_LOCK_MS);
                    binding.bannerMoresend.setVisibility(View.GONE);
                    android.widget.Toast.makeText(getContext(), "Resending " + n + " chunk(s)", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(getContext(), "Nothing to resend", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            // ─── MO resend: Discard (포기) ───
            binding.btnMoresendDiscard.setOnClickListener(v -> {
                if (mMoresendMsgId >= 0 && getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).discardMoResend(mMoresendMsgId);
                binding.bannerMoresend.setVisibility(View.GONE);
            });
        }
        // ACK 설정이 다른 화면에서 바뀌었을 수 있으므로 제목칸 상태 재반영
        updateTitleVisibility();
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

    // ============================================================
    //  [fileMsg 3-C] 파일/사진 첨부 선택 결과 처리.
    //  Uri → byte[] 읽기 → 10KB 검증(파일=차단, 사진=압축) → mAttachBytes 저장 + UI.
    // ============================================================
    private static final int ATTACH_MAX_BYTES = 10 * 1024;   // 10KB 원본 한도

    private void handleAttachPicked(Uri uri) {
        if (uri == null) return;
        final boolean isPhoto = binding.typePhoto.isChecked();
        final String fname = getFileNameFromUri(uri);
        new Thread(() -> {
            try {
                byte[] data = readBytesFromUri(uri);
                if (data == null || data.length == 0) { postToast("Failed to read file"); return; }
                if (isPhoto) {
                    if (data.length > ATTACH_MAX_BYTES) {
                        byte[] comp = compressImageToMax(uri, ATTACH_MAX_BYTES);
                        if (comp == null) { postToast("Failed to compress photo"); return; }
                        data = comp;
                    }
                    if (data.length > ATTACH_MAX_BYTES) { postToast("Photo too large even after compression"); return; }
                } else {
                    if (data.length > ATTACH_MAX_BYTES) {
                        final int kb = data.length / 1024;
                        if (getActivity() != null) getActivity().runOnUiThread(() ->
                            new android.app.AlertDialog.Builder(requireContext())
                                .setTitle("File too large")
                                .setMessage("Max 10KB. Selected file is " + kb + "KB.")
                                .setPositiveButton("OK", null).show());
                        return;
                    }
                }
                final byte[] fdata = data;
                mAttachBytes = fdata;
                mAttachName = (fname != null && !fname.isEmpty()) ? fname : (isPhoto ? "photo.jpg" : "file.bin");
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    binding.uploadLabel.setText(mAttachName + " (" + fdata.length + " B)");
                    if (isPhoto) {
                        try {
                            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(fdata, 0, fdata.length);
                            if (bmp != null) { binding.uploadThumb.setImageBitmap(bmp); binding.uploadThumb.setVisibility(View.VISIBLE); }
                        } catch (Exception ignore) {}
                    } else { binding.uploadThumb.setVisibility(View.GONE); }
                });
            } catch (Exception e) { postToast("Attach error: " + e.getMessage()); }
        }).start();
    }

    private void postToast(String msg) {
        if (getActivity() != null) getActivity().runOnUiThread(() ->
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private byte[] readBytesFromUri(Uri uri) throws Exception {
        java.io.InputStream is = requireContext().getContentResolver().openInputStream(uri);
        if (is == null) return null;
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally { is.close(); }
    }

    private String getFileNameFromUri(Uri uri) {
        String name = null;
        try (android.database.Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignore) {}
        return name;
    }

    private byte[] compressImageToMax(Uri uri, int maxBytes) {
        try {
            java.io.InputStream is = requireContext().getContentResolver().openInputStream(uri);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            if (bmp == null) return null;
            for (int q = 90; q >= 30; q -= 10) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, bos);
                if (bos.size() <= maxBytes) return bos.toByteArray();
            }
            android.graphics.Bitmap cur = bmp;
            for (int step = 0; step < 5; step++) {
                int w = Math.max(1, cur.getWidth() / 2), h = Math.max(1, cur.getHeight() / 2);
                cur = android.graphics.Bitmap.createScaledBitmap(cur, w, h, true);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                cur.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, bos);
                if (bos.size() <= maxBytes) return bos.toByteArray();
                if (w <= 2 || h <= 2) break;
            }
            return null;
        } catch (Exception e) { Log.e(TAG, "compressImageToMax fail: " + e.getMessage()); return null; }
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

        // Large radio: when Large/Photo/File, remove 200B filter; Short restores it
        binding.typeLarge.setOnCheckedChangeListener((b, checked) -> {
            updateTitleVisibility();
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

    /** 제목칸 표시 제어: 대용량 체크 OR 단문 ACK ON이면 숨김 (식별자 전용이라 입력 불필요). */
    private void updateTitleVisibility() {
        if (binding == null) return;
        boolean large = binding.typeLarge.isChecked() || binding.typePhoto.isChecked() || binding.typeFile.isChecked();
        boolean ackShortOn = android.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean("pref_ack_short", false);
        boolean hide = large || ackShortOn;
        binding.editChatTitle.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    private void updateByteCount() {
        if (binding == null) return;
        int bytes = binding.editChatMsg.getText().toString().getBytes(StandardCharsets.UTF_8).length;
        boolean large = binding.typeLarge.isChecked();
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
        // [fileMsg] Type selector → switch input UI
        binding.groupMsgType.setOnCheckedChangeListener((g, checkedId) -> {
            boolean isShort = (checkedId == R.id.type_short);
            boolean isLarge = (checkedId == R.id.type_large);
            boolean isPhoto = (checkedId == R.id.type_photo);
            boolean isFile  = (checkedId == R.id.type_file);
            boolean isAttach = isPhoto || isFile;

            // Title: only for Short (and Large hides it per spec)
            binding.editChatTitle.setVisibility(isShort ? View.VISIBLE : View.GONE);
            // Message box vs Upload area
            binding.editChatMsg.setVisibility(isAttach ? View.GONE : View.VISIBLE);
            binding.uploadArea.setVisibility(isAttach ? View.VISIBLE : View.GONE);
            // Count label
            if (isShort)      binding.textByteCount.setText("0/200 B");
            else if (isLarge) binding.textByteCount.setText("\u2248 0 chunks");
            else              binding.textByteCount.setText("");

            // reset attach when leaving attach mode
            if (!isAttach) {
                mAttachBytes = null; mAttachName = null;
                binding.uploadThumb.setVisibility(View.GONE);
                binding.uploadLabel.setText("Tap to attach");
            }
        });

        // [fileMsg 3-C] Tap upload area → open file/photo picker
        binding.uploadArea.setOnClickListener(v -> {
            boolean isPhoto = binding.typePhoto.isChecked();
            mAttachLauncher.launch(isPhoto ? "image/*" : "*/*");
        });

        binding.btnChatSend.setOnClickListener(v -> {
            String title = binding.editChatTitle.getText().toString().trim();
            String msg   = binding.editChatMsg.getText().toString().trim();

            // 대용량 모드: 저장 없이 즉시 분할 전송 (상대 IMEI로)
            // Photo/File: attach via upload area (handled in 3-C). Here just guard.
            if (binding.typePhoto.isChecked() || binding.typeFile.isChecked()) {
                if (mAttachBytes == null || mAttachBytes.length == 0) {
                    Toast.makeText(getContext(), "Please attach a file first", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                    Toast.makeText(getContext(), getString(R.string.chat_ble_not_connected), Toast.LENGTH_SHORT).show();
                    return;
                }
                String fileTo = "SERVER".equals(mCodeNum) ? "" : mCodeNum;
                char ft = binding.typePhoto.isChecked() ? 'I' : 'F';
                // [3-D-1] 송신 말풍선: 사진/파일 보낼 때 채팅에 즉시 [IMG]/[FILE] 표시 (전송됨=isSend true → FAB펜딩 제외)
                    String _bubbleTitle = (ft == 'I') ? "[IMG]" : "[FILE]";
                    String _fname = (mAttachName != null) ? mAttachName : (ft == 'I' ? "photo.jpg" : "file.bin");
                    String _bubbleBody = _fname;   // 기본: 파일명
                    // [3-D-2 송신썸네일] 이미지면 보낸 사진을 sent_files에 저장하고 경로를 msg에 → 어댑터가 썸네일 표시
                    if (ft == 'I' && mAttachBytes != null) {
                        try {
                            java.io.File _sdir = new java.io.File(requireContext().getExternalFilesDir(null), "sent_files");
                            if (!_sdir.exists()) _sdir.mkdirs();
                            java.io.File _sf = new java.io.File(_sdir, System.currentTimeMillis() + "_" + _fname);
                            try (java.io.FileOutputStream _fos = new java.io.FileOutputStream(_sf)) { _fos.write(mAttachBytes); }
                            _bubbleBody = _sf.getAbsolutePath();   // 경로로 교체 → 썸네일
                        } catch (Exception _se) { /* 저장 실패 시 파일명 유지(텍스트 폴백) */ }
                    }
                    MsgEntity _photoBubble = new MsgEntity(
                            0, true, mCodeNum,
                            _bubbleTitle, _bubbleBody,
                            new java.util.Date(),
                            null, null,
                            false, true, false
                    );
                    msgViewModel.insert(_photoBubble, _s -> null);
                ((MainActivity) requireActivity()).sendLargeFile(fileTo, mAttachBytes, mAttachName, ft, _bubbleBody);
                mAttachBytes = null; mAttachName = null;
                binding.uploadThumb.setVisibility(View.GONE);
                binding.uploadLabel.setText("Tap to attach");
                Toast.makeText(getContext(), "Large transfer started", Toast.LENGTH_SHORT).show();
                return;
            }

            // Large text mode: split & send immediately (no DB save)
            if (binding.typeLarge.isChecked()) {
                if (msg.isEmpty()) {
                    Toast.makeText(getContext(), getString(R.string.chat_input_empty), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                    Toast.makeText(getContext(), getString(R.string.chat_ble_not_connected), Toast.LENGTH_SHORT).show();
                    return;
                }
                String largeTo = "SERVER".equals(mCodeNum) ? "" : mCodeNum;
                ((MainActivity) requireActivity()).sendLargeMsg(largeTo, msg);
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

            // ⭐ 단문 ACK 설정 확인: ON이면 제목=식별자(~M:msgId), 사용자 제목 무시 (대용량처럼)
            boolean ackShortOn = android.preference.PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
                    .getBoolean("pref_ack_short", false);
            String titleToSave;
            if (ackShortOn) {
                int ackMsgId = ((MainActivity) requireActivity()).nextAckMsgId();
                titleToSave = "~M:" + ackMsgId;
            } else {
                titleToSave = title.isEmpty() ? null : title;
            }
            MsgEntity newMsg = new MsgEntity(
                    0, true, mCodeNum,
                    titleToSave,
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
            String codeNum = MainActivity.addrForSend(msg.getCodeNum());
            String title   = msg.getTitle() != null ? msg.getTitle() : "";
            String message = msg.getMsg() != null ? msg.getMsg() : "";

            Log.v(TAG, "Prep: id=" + msg.getId() + " to=" + codeNum + " msg=" + message);

            // [방어] 0x07 프레임의 title/memo 길이 필드는 1바이트(최대 255).
            //   초과하면 길이 필드가 깨져 단말 버퍼를 망가뜨림 → 전송 건너뜀.
            byte[] titleBytesChk = title.getBytes(StandardCharsets.UTF_8);
            byte[] msgBytesChk   = message.getBytes(StandardCharsets.UTF_8);
            if (titleBytesChk.length > 20 || msgBytesChk.length > 200) {
                android.util.Log.e("SEND", "길이 초과 - 전송 건너뜀 id=" + msg.getId()
                        + " titleBytes=" + titleBytesChk.length
                        + " msgBytes=" + msgBytesChk.length + " (제목≤20, 본문≤200)");
                final int badLen = msgBytesChk.length;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(),
                            "200바이트 초과 메시지는 대용량으로 보내세요 (" + badLen + "B)",
                            Toast.LENGTH_LONG).show());
                }
                continue;   // 이 메시지는 깨진 프레임 방지 위해 스킵
            }

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

                // [bleFix-serialQueue] 직접 GATT write 제거 → Service 직렬 큐로 통일 (SENDING/RECV 동시쓰기 충돌 방지)
                //   완료 동기화는 기존 SENDING=id,OK 에코(lock.notifyAll)로 유지. Service가 Base64 인코딩하므로 원문(msg) 전달.
                // [pendingFix] offer는 LiveData setValue(@MainThread) → 백그라운드(SendRunnable)에서 직접 호출 시 크래시. 메인 스레드로.
                final String _qmsg = msg;
                handler.post(() -> BLE.INSTANCE.getWriteQueue().offer(_qmsg));

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
        mGapfillHandler.removeCallbacks(mGapfillTick);
        binding = null;
    }
}
