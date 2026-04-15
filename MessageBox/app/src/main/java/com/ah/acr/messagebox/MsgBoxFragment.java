package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.ah.acr.messagebox.adapter.InBoxAdapter;
import com.ah.acr.messagebox.adapter.MsgBoxAdapter;
import com.ah.acr.messagebox.adapter.OutBoxAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.InboxMsg;
import com.ah.acr.messagebox.database.InboxViewModel;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.MsgViewModelFactory;
import com.ah.acr.messagebox.database.MsgWithAddress;
import com.ah.acr.messagebox.database.OutboxMsg;
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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;


public class MsgBoxFragment extends Fragment {
    private static final String TAG = MsgBoxFragment.class.getSimpleName();

    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private FragmentMsgBoxBinding binding;
    private BleViewModel mBleViewModel;
    private MsgViewModel msgViewModel;

    private AddressViewModel addressViewModel;
    private MsgBoxAdapter mAdapter;

    private ProgressDialog dialog;



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }



    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentMsgBoxBinding.inflate(inflater, container, false);

        binding.progressSendBar.setIndeterminate(false);
        //binding.progressSendBar.setIndeterminate(true);
        binding.progressSendBar.setProgress(80);
        binding.progressSendBar.setVisibility(View.GONE);

        setupRecyclerView();
        setupViewModel();
        setupClickListeners();

        return binding.getRoot();
    }

    private void setupViewModel() {
        mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        msgViewModel = new ViewModelProvider(this).get(MsgViewModel.class);
        addressViewModel = new ViewModelProvider(this).get(AddressViewModel.class);
        observeUsers();
    }

    private void setupRecyclerView() {
        mAdapter = new MsgBoxAdapter(new MsgBoxAdapter.OnMsgClickListener() {
            @Override
            public void onMessageClick(MsgEntity msg) {
                handleMsgClick(msg);
            }
            @Override
            public void onMsgDeleteClick(MsgEntity msg) {
                handleDeleteClick(msg);
            }
        });


        RecyclerView recyclerView = binding.listMsgbox ;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext())) ;
        DividerItemDecoration dividerDecoration =
                new DividerItemDecoration(recyclerView.getContext(), new LinearLayoutManager(getContext()).getOrientation());
        recyclerView.addItemDecoration(dividerDecoration);

        recyclerView.setAdapter(mAdapter);
    }

    private void handleMsgClick(MsgEntity msg) {
        // 토스트 메시지 표시
        //Toast.makeText(getContext(),"선택된 사용자: " + msg.getMsg(), Toast.LENGTH_SHORT).show();

        // 보내는 메시지, 받은 메시지 구분해서 열기
        if (msg.isSendMsg()) {
            Bundle bundle = new Bundle();
            bundle.putInt("id", msg.getId());
            NavHostFragment.findNavController(MsgBoxFragment.this)
                   .navigate(R.id.main_msgbox_fragment_to_main_outbox_sub_fragment, bundle);
        } else {
            Bundle bundle = new Bundle();
            bundle.putInt("id", msg.getId());
            NavHostFragment.findNavController(MsgBoxFragment.this)
                    .navigate(R.id.main_msgbox_fragment_to_main_inbox_sub_fragment, bundle);
        }

    }

    private void handleDeleteClick(MsgEntity msg) {
        new AlertDialog.Builder(getContext())
                .setTitle("Message Delete")
                .setMessage(getString(R.string.inbox_msg_del_alert))
                .setPositiveButton("Delete", (dialog, which) -> {
                    msgViewModel.deleteById(msg.getId());
                })
                .setNegativeButton("cancel", null)
                .show();

//              // 같은 기능임.
//                final Snackbar snackbar = Snackbar.make(getView(), getString(R.string.inbox_msg_del_alert), Snackbar.LENGTH_LONG);
//                snackbar.setAction("OK", v2 ->  {
//                    msgViewModel.delete(msg);
//                    snackbar.dismiss();
//                });
//                snackbar.show();

    }


    private void setupClickListeners() {

        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            }
        });

        binding.buttonReflesh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            }
        });

        binding.buttonMsgNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(MsgBoxFragment.this)
                        .navigate(R.id.main_msgbox_fragment_to_main_outbox_new_fragment);
            }
        });

        // data sending.......
        binding.buttonMsgSend.setOnClickListener(view->{

            List<MsgWithAddress> msgs = new ArrayList<>(mAdapter.getCurrentList());
            Collections.reverse(msgs);

            //List<MsgWithAddress> msgs = mAdapter.getCurrentList();
            //Collections.reverse(msgs);

            if(msgs == null) {
                Log.v(TAG, "보내야 할 메시지가 없음.");
                return;
            }

            dialog = new ProgressDialog(MsgBoxFragment.this.getContext());
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            //dialog.setIndeterminate(true);
            //dialog.setCancelable(true);
            dialog.setCancelable(false);
            dialog.setMessage("sendding messages......");



            List<String> msgList = new ArrayList<>();
            for (MsgWithAddress msg : msgs) {
                if (msg.getMsg().isSendMsg() && !msg.getMsg().isSend()) {

                    String codeNum = msg.getMsg().getCodeNum();
                    String title = msg.getMsg().getTitle();
                    String message = msg.getMsg().getMsg();
                    Log.v(TAG, codeNum + "---:---" + message);

                    ByteBuf buffer = Unpooled.buffer();
                    buffer.writeByte(0x07);

                    buffer.writeByte(codeNum.getBytes(StandardCharsets.US_ASCII).length);
                    buffer.writeCharSequence(codeNum, StandardCharsets.US_ASCII);

                    buffer.writeByte(title.getBytes(StandardCharsets.UTF_8).length);
                    buffer.writeCharSequence(title, StandardCharsets.UTF_8);
                    buffer.writeByte(message.getBytes(StandardCharsets.UTF_8).length);
                    buffer.writeCharSequence(message, StandardCharsets.UTF_8);


                    Log.v(TAG, "Size: " + buffer.readableBytes() + "   " + ByteBufUtil.hexDump(buffer));
//                    //byte[] header = new byte[21 + 1]; 0x06
//                    byte[] body = new byte[message.getBytes(StandardCharsets.UTF_8).length + 22];
//                    body[0] = 0x06; // free send.....
//                    Log.v(TAG, String.valueOf(body.length));
//
//                    System.arraycopy(codeNum.getBytes(StandardCharsets.UTF_8), 0, body, 1, codeNum.getBytes(StandardCharsets.UTF_8).length);
//                    System.arraycopy(message.getBytes(StandardCharsets.UTF_8), 0, body, 22, message.getBytes(StandardCharsets.UTF_8).length);
//
//                    Log.v(TAG, HexUtil.formatHexString(body));
                    byte[] body = new byte[buffer.readableBytes()];
                    buffer.readBytes(body);
                    String sms = String.format("SENDING=%d,%s", msg.getMsg().getId(), Base64.encodeToString(body, Base64.NO_WRAP));
                    msgList.add(sms);
                }
            }

            if (msgList.isEmpty()) return;

            // call thread
            BleDevice bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
            if (bleDevice != null || BleManager.getInstance().isConnected(bleDevice)) {
                dialog.show();
                binding.progressSendBar.setVisibility(View.VISIBLE);
                binding.progressSendBar.setMax(msgList.size());
                binding.progressSendBar.setProgress(0);

                new Thread(new MsgBoxFragment.BackgroundRunable(msgList)).start();

            } else {
                final Snackbar snackbar = Snackbar.make(getView(), getString(R.string.ble_test_nul_device), Snackbar.LENGTH_LONG);
                snackbar.setAction("OK", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackbar.dismiss();
                    }
                });
                snackbar.show();
            }

        }); //binding.buttonMsgSend.setOnClickListener

    }


    private void observeUsers() {
        msgViewModel.getAllMsgAddress().observe(getViewLifecycleOwner(), new Observer<List<MsgWithAddress>>() {
            @Override
            public void onChanged(List<MsgWithAddress> msgs) {
 //               Log.v(TAG, "onChanged" + msgs.size());
                mAdapter.submitList(msgs);
//                for (MsgWithAddress msg : msgs) {
//                    Log.v(TAG, msg.toString());
//                }
            }
        });

    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }


    class BackgroundRunable implements Runnable {
        private List<String> msgList;
        final Object lock = new Object();
        final private BleDevice bleDevice;
        private BluetoothGattCharacteristic characteristic;

        private int curCnt;
        private int totalCnt;

        public BackgroundRunable(List<String> msgList){
            this.msgList = msgList;

            this.bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
            for (BluetoothGattCharacteristic charact : BLE.INSTANCE.getSelDeviceGatt()) {
                int charaProp = charact.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    this.characteristic = charact;
                    break;
                }
            }

            totalCnt = msgList.size();

            BLE.INSTANCE.getOutboxMsgStatus().observe(getViewLifecycleOwner(), sReceive->{
                if (sReceive.startsWith("SENDING=")) {
                    String msg = sReceive.substring(8);
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

            for(String msg : msgList) {
                String sendMsg = String.format("%s\n", Base64.encodeToString(msg.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));

                BleManager.getInstance().write(
                        bleDevice,
                        SERVICE_UUID.toString(),
                        characteristic.getUuid().toString(),
                        sendMsg.getBytes(),
                        new BleWriteCallback() {

                            @Override
                            public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.v("WRITE", "write success, current: " + current
                                                + " total: " + total
                                                + " justWrite: " + HexUtil.formatHexString(justWrite, true));
                                    }
                                });
                            }

                            @Override
                            public void onWriteFailure(final BleException exception) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        //Log.v("WRITE", exception.toString());
                                    }
                                });
                            }
                        });
                synchronized (lock) {
                    try {
                        lock.wait();

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                curCnt++;
                                binding.progressSendBar.setProgress(curCnt);
                                if (curCnt == totalCnt) {
                                    dialog.dismiss();
                                    binding.progressSendBar.setVisibility(View.GONE);
                                }
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