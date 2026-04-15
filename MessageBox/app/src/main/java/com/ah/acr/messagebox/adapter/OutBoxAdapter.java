package com.ah.acr.messagebox.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.OutboxMsg;

import java.util.ArrayList;
import java.util.List;

public class OutBoxAdapter extends RecyclerView.Adapter<OutBoxAdapter.ViewHolder>{
    private List<OutboxMsg> mOutboxMsgs;
    //private List<Integer> mCheckedIds;
    private OnItemClickListener mListener = null ;
    private OnDeleteItemClickListener mDelListener = null ;

    public interface OnItemClickListener {
        void onItemClick(View v, int position) ;
    }
    public interface OnDeleteItemClickListener{
        void onItemClick(View v, int position) ;
    }
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mListener = listener ;
    }
    public void setOnDeleteItemClickListener(OnDeleteItemClickListener listener) {
        this.mDelListener = listener ;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView textReceiver;
        private final TextView textMsg;
        //private final Button btnBle;
        private final Button btnDel;

        public ViewHolder(View view, final OnItemClickListener listener) {
            super(view);
            checkBox = (CheckBox) view.findViewById(R.id.check_box);
            textReceiver = (TextView) view.findViewById(R.id.text_receiver);
            textMsg = (TextView) view.findViewById(R.id.text_msg);
            //btnBle = (Button) view.findViewById(R.id.button_ble_send);
            btnDel = (Button) view.findViewById(R.id.button_del);

            view.setOnClickListener(v ->{
                int pos = getAbsoluteAdapterPosition();
                if (pos != RecyclerView.NO_POSITION){
                    listener.onItemClick(v, pos);
                }
            });
        }

        public CheckBox getCheckBox() { return checkBox;  }

        public TextView getTextReceiver() {
            return textReceiver;
        }

        public TextView getTextMsg() {
            return textMsg;
        }

//        public Button getBtnBle() {
//            return btnBle;
//        }

        public Button getBtnDel() {
            return btnDel;
        }
    }

    public OutBoxAdapter() {
        //mCheckedIds = new ArrayList<>();
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.adapter_msg_outbox, viewGroup, false);

        return new ViewHolder(view, mListener);
    }

    public void setOutboxMsgs(List<OutboxMsg> outboxMsgs) {
        this.mOutboxMsgs = outboxMsgs;
        notifyDataSetChanged();
    }

    public List<OutboxMsg> getOutboxMsgs(){
        return mOutboxMsgs;
    }

//    public List<Integer> getCheckedIDs(){
//        return mCheckedIds;
//    }


    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        OutboxMsg outboxMsg = mOutboxMsgs.get(position);
        viewHolder.getCheckBox().setChecked(outboxMsg.isChecked());
        viewHolder.getTextReceiver().setText(outboxMsg.getReceiver());
        //String msg = outboxMsg.getMsg();
        //if (msg.length() >= 15) msg = outboxMsg.getMsg().substring(0, 14);
        viewHolder.getTextMsg().setText(outboxMsg.getMsg());
        //viewHolder.getBtnDel().setText("Del");
        viewHolder.getBtnDel().setOnClickListener(v ->{
            mDelListener.onItemClick(v, position);
        });

//        CheckBox seqCheckBox = viewHolder.getCheckBox();
//        if(mCheckedIds.contains(outboxMsg.getId())) seqCheckBox.setChecked(true);
//
//
//        seqCheckBox.setOnCheckedChangeListener((v, b)->{
//            if (seqCheckBox.isChecked()){
//                if(!mCheckedIds.contains(outboxMsg.getId())) mCheckedIds.add(outboxMsg.getId());
//            } else {
//                if(mCheckedIds.contains(outboxMsg.getId())) {
//                    for (int i=0;i<mCheckedIds.size();i++){
//                        if (mCheckedIds.get(i) == outboxMsg.getId()){
//                            mCheckedIds.remove(i);
//                            break;
//                        }
//                    }
//                }
//            }
//            mOutboxMsgs.get(position).setChecked(seqCheckBox.isChecked());
//        });
//
        viewHolder.getCheckBox().setOnClickListener(v->{
            mOutboxMsgs.get(position).setChecked(viewHolder.getCheckBox().isChecked());
        });


        if (!mOutboxMsgs.get(position).isSend()) {
            viewHolder.itemView.setBackgroundColor(Color.parseColor("#AED6F1"));
            //viewHolder.getBtnBle().setVisibility(View.VISIBLE);
        } else {
            viewHolder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"));
            //viewHolder.getBtnBle().setVisibility(View.GONE);
        }

        ViewGroup.LayoutParams layoutParams = viewHolder.itemView.getLayoutParams();
        layoutParams.height = 150;
        viewHolder.itemView.requestLayout();
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        if (mOutboxMsgs == null) return 0;
        return mOutboxMsgs.size();
    }
}
