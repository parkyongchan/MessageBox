package com.ah.acr.messagebox.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.InboxMsg;
import com.ah.acr.messagebox.database.OutboxMsg;


import java.util.List;

public class InBoxAdapter extends RecyclerView.Adapter<InBoxAdapter.ViewHolder>{
    private List<InboxMsg> mInboxMsgs;
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
        private final TextView textSender;
        private final TextView textMsg;
        private final Button btnDel;

        public ViewHolder(View view, final OnItemClickListener listener) {
            super(view);

            textSender = (TextView) view.findViewById(R.id.text_sender);
            textMsg = (TextView) view.findViewById(R.id.text_msg);
            btnDel = (Button) view.findViewById(R.id.btn_del);

            view.setOnClickListener(v ->{
                int pos = getAbsoluteAdapterPosition();
                if (pos != RecyclerView.NO_POSITION){
                    listener.onItemClick(v, pos);
                }
            });
        }

        public TextView getTextSender() {
            return textSender;
        }

        public TextView getTextMsg() {
            return textMsg;
        }

        public Button getBtnDel() {
            return btnDel;
        }
    }


    public InBoxAdapter() {

    }

    public void setInboxMsgs(List<InboxMsg> inboxMsgs) {
        this.mInboxMsgs = inboxMsgs;
        notifyDataSetChanged();
    }

    public List<InboxMsg> getInboxMsgs(){
        return mInboxMsgs;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.adapter_msg_inbox, viewGroup, false);


        return new ViewHolder(view, mListener);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        //Log.v("mInBoxMsgs.get(position)" ,mInBoxMsgs.get(position).toString());
        viewHolder.getTextSender().setText(mInboxMsgs.get(position).getSerial()); //.getSender());
        viewHolder.getTextMsg().setText(mInboxMsgs.get(position).getMsg());
        //viewHolder.getBtnDel().setText("Del");

        viewHolder.getBtnDel().setOnClickListener(v ->{
            mDelListener.onItemClick(v, position);
        });

        if (mInboxMsgs.get(position).isNew()) {
            viewHolder.itemView.setBackgroundColor(Color.parseColor("#FBEEE6"));
        } else {
            viewHolder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        }

        ViewGroup.LayoutParams layoutParams = viewHolder.itemView.getLayoutParams();
        layoutParams.height = 150;
        viewHolder.itemView.requestLayout();
    }

    @Override
    public int getItemCount() {
        if (mInboxMsgs == null) return 0;
        return mInboxMsgs.size();
    }
}
