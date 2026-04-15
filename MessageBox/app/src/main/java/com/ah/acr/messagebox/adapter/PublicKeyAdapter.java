package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.data.KeyValue;

import java.util.List;

public class PublicKeyAdapter extends RecyclerView.Adapter<PublicKeyAdapter.ViewHolder>{
    private List<KeyValue> keyList;

    public interface OnItemClickListener {
        void onItemClick(View v, int position) ;
    }
    private OnItemClickListener mListener = null ;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mListener = listener ;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textKey;
        private final TextView textValue;
        private final Button buttonDel;

        public ViewHolder(View view) {
            super(view);
            // Define click listener for the ViewHolder's View

            textKey = (TextView) view.findViewById(R.id.textKey);
            textValue = (TextView) view.findViewById(R.id.textValue);
            buttonDel = (Button) view.findViewById(R.id.button_del);

//            view.setOnClickListener(v ->{
//                    int pos = getAbsoluteAdapterPosition();
//                    if (pos != RecyclerView.NO_POSITION){
//
//                        if(mListener != null) {
//                            mListener.onItemClick(v, pos);
//                        }
//                    }
//                });
        }

        public TextView getTextKey() {
            return textKey;
        }

        public TextView getTextValue() {
            return textValue;
        }

        public Button getButtonDel() {
            return buttonDel;
        }
    }


    public PublicKeyAdapter(List<KeyValue> keyList) {
        this.keyList = keyList;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.adapter_public_key, viewGroup, false);

        return new ViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.getTextKey().setText(keyList.get(position).getKey());
        viewHolder.getTextValue().setText(keyList.get(position).getValue());
        viewHolder.getButtonDel().setText("Del");

        viewHolder.getButtonDel().setOnClickListener(v ->{
            mListener.onItemClick(v, position);
        });

        ViewGroup.LayoutParams layoutParams = viewHolder.itemView.getLayoutParams();
        layoutParams.height = 150;
        viewHolder.itemView.requestLayout();
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return keyList.size();
    }
}
