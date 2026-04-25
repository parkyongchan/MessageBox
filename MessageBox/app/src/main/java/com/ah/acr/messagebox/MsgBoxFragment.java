package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.recyclerview.widget.ItemTouchHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.adapter.MsgBoxAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.MsgWithAddress;
import com.ah.acr.messagebox.databinding.FragmentMsgBoxBinding;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;
import com.google.android.material.snackbar.Snackbar;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

public class MsgBoxFragment extends Fragment {
    private static final String TAG = MsgBoxFragment.class.getSimpleName();
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final int PAGE_SIZE = 10;

    private FragmentMsgBoxBinding binding;
    private BleViewModel mBleViewModel;
    private MsgViewModel msgViewModel;
    private AddressViewModel addressViewModel;
    private MsgBoxAdapter mAdapter;
    private ProgressDialog dialog;

    // For display (latest 1 per contact)
    private List<MsgWithAddress> mAllMsgs = new ArrayList<>();

    // For sending: full list of unsent messages
    private List<MsgEntity> mAllUnsentMsgs = new ArrayList<>();

    private int mCurrentPage = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMsgBoxBinding.inflate(inflater, container, false);

        binding.progressSendBar.setIndeterminate(false);
        binding.progressSendBar.setProgress(80);
        binding.progressSendBar.setVisibility(View.GONE);

        setupRecyclerView();
        setupViewModel();
        setupClickListeners();

        return binding.getRoot();
    }

    private void setupViewModel() {
        mBleViewModel    = new ViewModelProvider(this).get(BleViewModel.class);
        msgViewModel     = new ViewModelProvider(this).get(MsgViewModel.class);
        addressViewModel = new ViewModelProvider(this).get(AddressViewModel.class);
        observeUsers();
    }

    private void setupRecyclerView() {
        mAdapter = new MsgBoxAdapter(new MsgBoxAdapter.OnMsgClickListener() {
            @Override
            public void onMessageClick(MsgEntity msg) {
                if (mAdapter.isCheckMode()) return;
                handleMsgClick(msg);
            }
            @Override
            public void onMsgDeleteClick(MsgEntity msg) {
                handleDeleteClick(msg);
            }
            @Override
            public void onLongClick() {}
        });

        RecyclerView recyclerView = binding.listMsgbox;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        DividerItemDecoration dividerDecoration = new DividerItemDecoration(
                recyclerView.getContext(),
                new LinearLayoutManager(getContext()).getOrientation());
        recyclerView.addItemDecoration(dividerDecoration);
        recyclerView.setAdapter(mAdapter);

        // ── Swipe to delete ──
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView rv,
                                          @NonNull RecyclerView.ViewHolder vh,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int position = viewHolder.getAdapterPosition();
                        MsgWithAddress swipedItem = mAdapter.getCurrentList().get(position);
                        String codeNum = swipedItem.getMsg().getCodeNum();

                        // Localized
                        new AlertDialog.Builder(getContext())
                                .setTitle(getString(R.string.chat_dialog_delete_all_contact_title))
                                .setMessage(getString(R.string.chat_dialog_delete_all_contact_message))
                                .setPositiveButton(getString(R.string.chat_btn_delete_all), (dlg, which) -> {
                                    for (MsgWithAddress item : mAllMsgs) {
                                        if (codeNum != null &&
                                                codeNum.equals(item.getMsg().getCodeNum())) {
                                            msgViewModel.delete(item.getMsg());
                                        }
                                    }
                                })
                                .setNegativeButton(getString(R.string.btn_cancel), (dlg, which) ->
                                        mAdapter.notifyItemChanged(position))
                                .setCancelable(false)
                                .show();
                    }

                    @Override
                    public void onChildDraw(@NonNull android.graphics.Canvas c,
                                            @NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
                        View itemView = viewHolder.itemView;
                        android.graphics.Paint paint = new android.graphics.Paint();
                        paint.setColor(0xFFB71C1C);
                        c.drawRect(itemView.getRight() + dX, itemView.getTop(),
                                itemView.getRight(), itemView.getBottom(), paint);

                        paint.setColor(0xFFFFFFFF);
                        paint.setTextSize(32f);
                        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                        paint.setFakeBoldText(true);
                        float textX = itemView.getRight() - 80f;
                        float textY = itemView.getTop() + (itemView.getBottom() - itemView.getTop()) / 2f + 12f;
                        // Localized swipe label
                        c.drawText(getString(R.string.chat_swipe_delete), textX, textY, paint);

                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }

                    @Override
                    public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder) {
                        if (mAdapter.isCheckMode()) return 0;
                        return super.getSwipeDirs(recyclerView, viewHolder);
                    }
                });
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void handleMsgClick(MsgEntity msg) {
        Bundle bundle = new Bundle();
        bundle.putString("code_num", msg.getCodeNum());

        String contactName = msg.getCodeNum();
        for (MsgWithAddress item : mAdapter.getCurrentList()) {
            if (item.getMsg().getCodeNum() != null &&
                    item.getMsg().getCodeNum().equals(msg.getCodeNum())) {
                if (item.getAddress() != null && item.getAddress().getNumbersNic() != null) {
                    contactName = item.getAddress().getNumbersNic();
                }
                break;
            }
        }
        bundle.putString("contact_name", contactName);

        NavHostFragment.findNavController(MsgBoxFragment.this)
                .navigate(R.id.action_msgbox_to_chat_room, bundle);
    }

    private void handleDeleteClick(MsgEntity msg) {
        // Localized
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.chat_dialog_delete_msg_title))
                .setMessage(getString(R.string.chat_dialog_delete_msg_message))
                .setPositiveButton(getString(R.string.addr_btn_delete), (dialog, which) ->
                        msgViewModel.deleteById(msg.getId()))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void setupClickListeners() {

        // Request inbox messages on BLE connection
        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            }
        });

        // Refresh
        binding.buttonReflesh.setOnClickListener(v ->
                BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?"));

        // New message
        binding.buttonMsgNew.setOnClickListener(v ->
                NavHostFragment.findNavController(MsgBoxFragment.this)
                        .navigate(R.id.main_msgbox_fragment_to_main_outbox_new_fragment));

        // ── Select button (toggle check mode) ──
        binding.buttonSelectMode.setOnClickListener(v -> {
            if (mAdapter.isCheckMode()) {
                exitCheckMode();
            } else {
                enterCheckMode();
            }
        });

        // ── Delete all button ──
        binding.buttonCheckDelete.setOnClickListener(v -> {
            if (mAllMsgs.isEmpty()) {
                // Localized
                Toast.makeText(getContext(),
                        getString(R.string.chat_toast_no_msg_to_delete),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            // Localized
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.chat_dialog_delete_all_title))
                    .setMessage(getString(R.string.chat_dialog_delete_all_message))
                    .setPositiveButton(getString(R.string.chat_btn_delete_all), (dlg, which) -> {
                        for (MsgWithAddress item : mAllMsgs) {
                            msgViewModel.delete(item.getMsg());
                        }
                        exitCheckMode();
                    })
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
        });

        // ── Pager buttons ──
        binding.btnPageFirst.setOnClickListener(v -> { mCurrentPage = 0; updatePage(); });
        binding.btnPagePrev.setOnClickListener(v  -> { mCurrentPage--; updatePage(); });
        binding.btnPageNext.setOnClickListener(v  -> { mCurrentPage++; updatePage(); });
        binding.btnPageLast.setOnClickListener(v  -> {
            mCurrentPage = Math.max(0,
                    (int) Math.ceil((double) mAllMsgs.size() / PAGE_SIZE) - 1);
            updatePage();
        });

        // ── Long click -> enter check mode ──
        binding.listMsgbox.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private final GestureDetector gestureDetector =
                    new GestureDetector(getContext(),
                            new GestureDetector.SimpleOnGestureListener() {
                                @Override
                                public void onLongPress(MotionEvent e) {
                                    View child = binding.listMsgbox.findChildViewUnder(e.getX(), e.getY());
                                    if (child != null && !mAdapter.isCheckMode()) {
                                        enterCheckMode();
                                    }
                                }
                            });

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                gestureDetector.onTouchEvent(e);
                return false;
            }
            @Override public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}
            @Override public void onRequestDisallowInterceptTouchEvent(boolean b) {}
        });

        // ⭐ Send all unsent messages
        binding.buttonMsgSend.setOnClickListener(view -> {

            // Use mAllUnsentMsgs (all unsent messages), not mAllMsgs (1 per contact)
            if (mAllUnsentMsgs.isEmpty()) {
                Log.v(TAG, "No messages to send.");
                // Localized
                Toast.makeText(getContext(),
                        getString(R.string.chat_toast_no_pending),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Sort by oldest first (createAt ascending)
            List<MsgEntity> unsentMsgsSorted = new ArrayList<>(mAllUnsentMsgs);
            unsentMsgsSorted.sort((a, b) -> {
                if (a.getCreateAt() == null) return 1;
                if (b.getCreateAt() == null) return -1;
                return a.getCreateAt().compareTo(b.getCreateAt());
            });

            dialog = new ProgressDialog(MsgBoxFragment.this.getContext());
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setCancelable(false);
            // Localized: "Sending %1$d messages..."
            dialog.setMessage(getString(R.string.chat_sending_progress, unsentMsgsSorted.size()));

            List<String> msgList = new ArrayList<>();
            for (MsgEntity msg : unsentMsgsSorted) {
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
                Log.v(TAG, "No messages to encode.");
                return;
            }

            Log.v(TAG, "Total messages to send: " + msgList.size());

            BleDevice bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
            if (bleDevice != null && BleManager.getInstance().isConnected(bleDevice)) {
                dialog.show();
                binding.progressSendBar.setVisibility(View.VISIBLE);
                binding.progressSendBar.setMax(msgList.size());
                binding.progressSendBar.setProgress(0);
                new Thread(new BackgroundRunable(msgList)).start();
            } else {
                final Snackbar snackbar = Snackbar.make(getView(),
                        getString(R.string.ble_test_nul_device), Snackbar.LENGTH_LONG);
                snackbar.setAction("OK", v -> snackbar.dismiss());
                snackbar.show();
            }
        });
    }

    // ── Enter check mode ──
    private void enterCheckMode() {
        mAdapter.setCheckMode(true);
        binding.buttonCheckDelete.setVisibility(View.VISIBLE);
        binding.buttonMsgNew.setVisibility(View.GONE);
        // Localized
        binding.buttonSelectMode.setText(getString(R.string.btn_cancel));
        binding.buttonSelectMode.setTextColor(0xFFFF5252);
    }

    private void exitCheckMode() {
        mAdapter.setCheckMode(false);
        binding.buttonCheckDelete.setVisibility(View.GONE);
        binding.buttonMsgNew.setVisibility(View.VISIBLE);
        // Localized
        binding.buttonSelectMode.setText(getString(R.string.chat_btn_select));
        binding.buttonSelectMode.setTextColor(0xFFB8CEE8);
    }

    // ── Update page ──
    private void updatePage() {
        int total      = mAllMsgs.size();
        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;
        if (mCurrentPage >= totalPages) mCurrentPage = totalPages - 1;
        if (mCurrentPage < 0) mCurrentPage = 0;

        int from     = mCurrentPage * PAGE_SIZE;
        int to       = Math.min(from + PAGE_SIZE, total);
        List<MsgWithAddress> pageData = mAllMsgs.subList(from, to);
        mAdapter.submitList(new ArrayList<>(pageData));

        if (total > PAGE_SIZE) {
            binding.layoutPager.setVisibility(View.VISIBLE);
            binding.textPageInfo.setText((mCurrentPage + 1) + " / " + totalPages);
            binding.btnPagePrev.setEnabled(mCurrentPage > 0);
            binding.btnPageNext.setEnabled(mCurrentPage < totalPages - 1);
            binding.btnPageFirst.setEnabled(mCurrentPage > 0);
            binding.btnPageLast.setEnabled(mCurrentPage < totalPages - 1);
        } else {
            binding.layoutPager.setVisibility(View.GONE);
        }
    }

    private void observeUsers() {
        msgViewModel.getLastMsgPerContact().observe(getViewLifecycleOwner(), msgs -> {
            mAllMsgs = msgs != null ? msgs : new ArrayList<>();
            mCurrentPage = 0;
            updatePage();
        });

        // Observe all messages -> update unsent list + count
        msgViewModel.getAllMsgs().observe(getViewLifecycleOwner(), allMsgs -> {
            updateUnsentList(allMsgs);
            updateUnsentCount(allMsgs);
        });
    }

    /** Update the full list of unsent messages */
    private void updateUnsentList(List<MsgEntity> allMsgs) {
        mAllUnsentMsgs.clear();
        if (allMsgs != null) {
            for (MsgEntity msg : allMsgs) {
                if (msg.isSendMsg() && !msg.isSend()) {
                    mAllUnsentMsgs.add(msg);
                }
            }
        }
        Log.v(TAG, "Unsent messages count: " + mAllUnsentMsgs.size());
    }

    private void updateUnsentCount(List<MsgEntity> allMsgs) {
        if (allMsgs == null) return;

        java.util.Map<String, Integer> unsentMap = new java.util.HashMap<>();
        int totalUnsent = 0;
        for (MsgEntity msg : allMsgs) {
            if (msg.isSendMsg() && !msg.isSend()) {
                String code = msg.getCodeNum();
                unsentMap.put(code, unsentMap.containsKey(code)
                        ? unsentMap.get(code) + 1 : 1);
                totalUnsent++;
            }
        }

        mAdapter.setUnsentMap(unsentMap);

        if (totalUnsent > 0) {
            binding.layoutUnsentInfo.setVisibility(View.VISIBLE);
            binding.textUnsentCount.setText(String.valueOf(totalUnsent));
        } else {
            binding.layoutUnsentInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    class BackgroundRunable implements Runnable {
        private final List<String> msgList;
        final Object lock = new Object();
        final private BleDevice bleDevice;
        private BluetoothGattCharacteristic characteristic;
        private int curCnt;
        private int totalCnt;

        public BackgroundRunable(List<String> msgList) {
            this.msgList   = msgList;
            this.bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();

            for (BluetoothGattCharacteristic charact : BLE.INSTANCE.getSelDeviceGatt()) {
                int charaProp = charact.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    this.characteristic = charact;
                    break;
                }
            }

            totalCnt = msgList.size();

            BLE.INSTANCE.getOutboxMsgStatus().observe(getViewLifecycleOwner(), sReceive -> {
                if (sReceive.startsWith("SENDING=")) {
                    String msg    = sReceive.substring(8);
                    String[] vals = msg.split(",");
                    if (vals[1].equals("OK")) {
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

                BleManager.getInstance().write(
                        bleDevice,
                        SERVICE_UUID.toString(),
                        characteristic.getUuid().toString(),
                        sendMsg.getBytes(),
                        new BleWriteCallback() {
                            @Override
                            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                                handler.post(() -> Log.v("WRITE", "write success, current: "
                                        + current + " total: " + total
                                        + " justWrite: " + HexUtil.formatHexString(justWrite, true)));
                            }
                            @Override
                            public void onWriteFailure(BleException exception) {}
                        });

                synchronized (lock) {
                    try {
                        lock.wait();
                        handler.post(() -> {
                            curCnt++;
                            binding.progressSendBar.setProgress(curCnt);
                            if (curCnt == totalCnt) {
                                dialog.dismiss();
                                binding.progressSendBar.setVisibility(View.GONE);
                            }
                        });
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        }
    }
}
