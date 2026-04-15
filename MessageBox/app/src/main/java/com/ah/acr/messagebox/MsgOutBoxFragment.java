package com.ah.acr.messagebox;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.adapter.OutBoxAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.database.OutboxMsg;
import com.ah.acr.messagebox.databinding.FragmentLocationBinding;
import com.ah.acr.messagebox.databinding.FragmentMsgBoxBinding;
import com.ah.acr.messagebox.databinding.FragmentMsgOutBoxBinding;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;
import com.google.android.material.snackbar.Snackbar;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class MsgOutBoxFragment extends Fragment {
    private static final String TAG = MsgOutBoxFragment.class.getSimpleName();
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    //private OutboxViewModel mOutboxViewModel;
    private FragmentMsgOutBoxBinding binding;
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
        binding = FragmentMsgOutBoxBinding.inflate(inflater, container, false);

        binding.progressSendBar.setIndeterminate(false);
        //binding.progressSendBar.setIndeterminate(true);
        binding.progressSendBar.setProgress(80);
        binding.progressSendBar.setVisibility(View.GONE);


//        mOutboxViewModel = new OutboxViewModelFactory(
//                MsgRoomDatabase.Companion.getDatabase(getContext()).outboxDao()
//        ).create(OutboxViewModel.class);
//        mOutboxViewModel.getAllOutboxMsgs().observe(getViewLifecycleOwner(), new Observer<List<OutboxMsg>>(){
//            @Override
//            public void onChanged(List<OutboxMsg> outboxMsg) {
//                ((OutBoxAdapter)binding.listOutbox.getAdapter()).setOutboxMsgs(outboxMsg);
//            }
//        });

        RecyclerView recyclerView = binding.listOutbox ;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext())) ;
        DividerItemDecoration dividerDecoration = new DividerItemDecoration(recyclerView.getContext(), new LinearLayoutManager(getContext()).getOrientation());
        recyclerView.addItemDecoration(dividerDecoration);

        OutBoxAdapter adapter = new OutBoxAdapter();
        adapter.setOnItemClickListener((view, pos) -> {
//                final OutboxMsg msg = mOutboxViewModel.getAllOutboxMsgs().getValue().get(pos);
//                Bundle bundle = new Bundle();
//                bundle.putInt("id", msg.getId());
//                NavHostFragment.findNavController(MsgOutBoxFragment.this)
//                        .navigate(R.id.action_main_outbox_fragment_to_main_outbox_sub_fragment, bundle);
        });

        adapter.setOnDeleteItemClickListener((view, pos) -> {
//                final Snackbar snackbar = Snackbar.make(getView(), getString(R.string.outbox_msg_del_alert), Snackbar.LENGTH_LONG);
//                snackbar.setAction("OK", v2 ->  {
//                    final OutboxMsg msg = mOutboxViewModel.getAllOutboxMsgs().getValue().get(pos);
//                    mOutboxViewModel.deleteOutboxMsg(msg);
//                    snackbar.dismiss();
//                });
//                snackbar.show();
        });

        recyclerView.setAdapter(adapter);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        binding.buttonOutboxNewMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                NavHostFragment.findNavController(MsgOutBoxFragment.this)
//                        .navigate(R.id.action_main_outbox_fragment_to_main_outbox_sub_new_fragment);
            }
        });

        binding.checkBoxAll.setOnClickListener(v->{
            List<OutboxMsg> msgs = ((OutBoxAdapter)(binding.listOutbox.getAdapter())).getOutboxMsgs();
            for (OutboxMsg msg : msgs) {
                msg.setChecked(binding.checkBoxAll.isChecked());
                binding.listOutbox.getAdapter().notifyDataSetChanged();
            }
        });

        binding.buttonBleSend.setOnClickListener(v->{
            dialog = new ProgressDialog(MsgOutBoxFragment.this.getContext());
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            //dialog.setIndeterminate(true);
            //dialog.setCancelable(true);
            dialog.setCancelable(false);
            dialog.setMessage("sendding messages......");

            List<OutboxMsg> msgs = ((OutBoxAdapter)(binding.listOutbox.getAdapter())).getOutboxMsgs();

            List<String> msgList = new ArrayList<>();
            for (OutboxMsg msg : msgs) {
                if (!msg.isSend()) {
                    String title = msg.getTitle();
                    String message = msg.getMsg();
                    Log.v(TAG, title + "---:---" + message);

                    //byte[] header = new byte[21 + 1]; 0x06
                    byte[] body = new byte[message.getBytes(StandardCharsets.UTF_8).length + 22];
                    body[0] = 0x06; // free send.....
                    Log.v(TAG, String.valueOf(body.length));

                    System.arraycopy(title.getBytes(StandardCharsets.UTF_8), 0, body, 1, title.getBytes(StandardCharsets.UTF_8).length);
                    System.arraycopy(message.getBytes(StandardCharsets.UTF_8), 0, body, 22, message.getBytes(StandardCharsets.UTF_8).length);

                    Log.v(TAG, HexUtil.formatHexString(body));

                    String sms = String.format("SENDING=%d,%s", msg.getId(), Base64.encodeToString(body, Base64.NO_WRAP));
                    msgList.add(sms);
                    ////BLE.INSTANCE.getWriteQueue().offer(sms);
                    ////((MainActivity) getActivity()).bleSendMessage(sms);
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

                new Thread(new BackgroundRunable(msgList)).start();

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
        });

        binding.outboxMsgDelete.setOnClickListener(v->{
            final Snackbar snackbar = Snackbar.make(getView(), getString(R.string.outbox_msg_del_alert), Snackbar.LENGTH_LONG);
            snackbar.setAction("OK", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<OutboxMsg> msgs = ((OutBoxAdapter)(binding.listOutbox.getAdapter())).getOutboxMsgs();
                    //List<Integer> ids = ((OutBoxAdapter)(binding.listOutbox.getAdapter())).getCheckedIDs();

                    for (OutboxMsg msg : msgs) {
                        if (msg.isChecked()){
                            Log.v(TAG, msg.toString());
                            //mOutboxViewModel.deleteOutboxMsg(msg);
                        }
                    }
                    snackbar.dismiss();
                }
            });
            snackbar.show();
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