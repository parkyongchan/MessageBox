package com.ah.acr.messagebox.splash;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ah.acr.messagebox.R;

/**
 * A single onboarding page. Uses the `position` argument to pick
 * the correct illustration, title, and description.
 */
public class OnboardingPageFragment extends Fragment {

    private static final String ARG_POSITION = "position";

    public static OnboardingPageFragment newInstance(int position) {
        OnboardingPageFragment f = new OnboardingPageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int position = getArguments() != null ? getArguments().getInt(ARG_POSITION, 0) : 0;

        ImageView imageView = view.findViewById(R.id.page_image);
        TextView titleView = view.findViewById(R.id.page_title);
        TextView descView = view.findViewById(R.id.page_description);
        View backgroundView = view.findViewById(R.id.page_background);

        switch (position) {
            case 0: // Power on
                imageView.setImageResource(R.drawable.ill_onboarding_power);
                titleView.setText(R.string.onboarding_title_power);
                descView.setText(R.string.onboarding_desc_power);
                backgroundView.setBackgroundResource(R.color.onboarding_bg_power);
                break;
            case 1: // BLE pair
                imageView.setImageResource(R.drawable.ill_onboarding_ble);
                titleView.setText(R.string.onboarding_title_ble);
                descView.setText(R.string.onboarding_desc_ble);
                backgroundView.setBackgroundResource(R.color.onboarding_bg_ble);
                break;
            case 2: // Clear sky
                imageView.setImageResource(R.drawable.ill_onboarding_clearsky);
                titleView.setText(R.string.onboarding_title_clearsky);
                descView.setText(R.string.onboarding_desc_clearsky);
                backgroundView.setBackgroundResource(R.color.onboarding_bg_clearsky);
                break;
            case 3: // High placement
                imageView.setImageResource(R.drawable.ill_onboarding_highplace);
                titleView.setText(R.string.onboarding_title_highplace);
                descView.setText(R.string.onboarding_desc_highplace);
                backgroundView.setBackgroundResource(R.color.onboarding_bg_highplace);
                break;
        }
    }
}
