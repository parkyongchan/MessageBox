package com.ah.acr.messagebox.adapter;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.clj.fastble.BleManager;
import com.clj.fastble.data.BleDevice;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder>{
    private Context context;
    private List<BleDevice> bleDeviceList=null;


    public DeviceAdapter(Context context) {
        this.context = context;
    }

    public void setBleDeviceList(List<BleDevice> list) {
        this.bleDeviceList = list;
        notifyDataSetChanged();
    }

    public interface OnDeviceClickListener {
        void onConnect(BleDevice bleDevice);
        void onDisConnect(BleDevice bleDevice);
        void onDetail(BleDevice bleDevice);
    }
    private OnDeviceClickListener mDeviceListener = null;
    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.mDeviceListener = listener;
    }

    public interface OnItemClickListener {
        void onItemClick(View v, int position) ;
    }
    private  OnItemClickListener mItemListener = null ;
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mItemListener = listener ;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView img_blue;
        public TextView txt_name;
        public TextView txt_mac;
        public TextView txt_rssi;
        public LinearLayout layout_idle;
        public LinearLayout layout_connected;
        public Button btn_disconnect;
        public Button btn_connect;

        public ViewHolder(View view, final OnItemClickListener listener) {
            super(view);

            img_blue = (ImageView) view.findViewById(R.id.img_blue);
            txt_name = (TextView) view.findViewById(R.id.txt_name);
            txt_mac = (TextView) view.findViewById(R.id.txt_mac);
            txt_rssi = (TextView) view.findViewById(R.id.txt_rssi);
            layout_idle = (LinearLayout) view.findViewById(R.id.layout_idle);
            layout_connected = (LinearLayout) view.findViewById(R.id.layout_connected);
            btn_disconnect = (Button) view.findViewById(R.id.btn_disconnect);
            btn_connect = (Button) view.findViewById(R.id.btn_connect);

            view.setOnClickListener(v ->{
                int pos = getAbsoluteAdapterPosition();
                if (pos != RecyclerView.NO_POSITION){
                    listener.onItemClick(v, pos);
                }
            });
        }

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {

        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.adapter_device, viewGroup, false);

        return new ViewHolder(view, mItemListener);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceAdapter.ViewHolder viewHolder, int position) {
        final BleDevice bleDevice = bleDeviceList.get(position);
        if (bleDevice == null) return;

        String name = bleDevice.getName();
        String mac = bleDevice.getMac();
        int rssi = bleDevice.getRssi();

        viewHolder.txt_name.setText(name);
        viewHolder.txt_mac.setText(mac);
        viewHolder.txt_rssi.setText(String.valueOf(rssi));
        boolean isConnected = BleManager.getInstance().isConnected(bleDevice);

        if (isConnected) {
            viewHolder.img_blue.setImageResource(R.mipmap.ic_blue_connected);
            viewHolder.txt_name.setTextColor(0xFF1DE9B6);
            viewHolder.txt_mac.setTextColor(0xFF1DE9B6);
            viewHolder.layout_idle.setVisibility(View.GONE);
            viewHolder.layout_connected.setVisibility(View.VISIBLE);

            //viewHolder.btn_disconnect = (Button) convertView.findViewById(R.id.btn_disconnect);
            //viewHolder.btn_connect = (Button) convertView.findViewById(R.id.btn_connect);
        } else {
            viewHolder.img_blue.setImageResource(R.mipmap.ic_blue_remote);
            viewHolder.txt_name.setTextColor(0xFF000000);
            viewHolder.txt_mac.setTextColor(0xFF000000);
            viewHolder.layout_idle.setVisibility(View.VISIBLE);
            viewHolder.layout_connected.setVisibility(View.GONE);
        }

        viewHolder.btn_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDeviceListener != null) {
                    mDeviceListener.onConnect(bleDevice);
                }
            }
        });

        viewHolder.btn_disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDeviceListener != null) {
                    mDeviceListener.onDisConnect(bleDevice);
                }
            }
        });

        ViewGroup.LayoutParams layoutParams = viewHolder.itemView.getLayoutParams();
        layoutParams.height = 150;
        viewHolder.itemView.requestLayout();
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public int getItemCount() {
        if (bleDeviceList == null) return 0;
        return bleDeviceList.size();
    }
}
