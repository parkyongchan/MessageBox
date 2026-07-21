package com.ah.acr.messagebox.nav;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.MsgRoomDatabase;
import com.ah.acr.messagebox.nav.db.NavDao;
import com.ah.acr.messagebox.nav.db.NavRoute;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * [NAV] Saved routes list. Tap a row -> open guidance (NavGuideFragment).
 * Long-press -> confirm & delete (CASCADE removes its segments/weather too).
 */
public class NavRoutesFragment extends DialogFragment {

    private LinearLayout mList;
    private TextView mEmpty;
    private final Handler mMain = new Handler(Looper.getMainLooper());

    public static NavRoutesFragment newInstance() { return new NavRoutesFragment(); }

    @Override public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_nav_routes, container, false);
        mList = root.findViewById(R.id.nav_routes_list);
        mEmpty = root.findViewById(R.id.nav_routes_empty);
        root.findViewById(R.id.nav_routes_close).setOnClickListener(v -> dismiss());
        reload();
        return root;
    }

    private NavDao dao() {
        return MsgRoomDatabase.Companion
                .getDatabase(requireContext().getApplicationContext()).navDao();
    }

    private void reload() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                final List<NavRoute> routes = dao().getAllRoutes();
                mMain.post(() -> render(routes));
            } catch (Exception e) {
                mMain.post(() -> { if (mEmpty != null) { mEmpty.setText("Load failed: " + e.getMessage()); mEmpty.setVisibility(View.VISIBLE); } });
            }
        });
    }

    private void render(List<NavRoute> routes) {
        if (!isAdded() || mList == null) return;
        mList.removeAllViews();
        if (routes == null || routes.isEmpty()) {
            mEmpty.setVisibility(View.VISIBLE);
            return;
        }
        mEmpty.setVisibility(View.GONE);
        int dp = (int) getResources().getDisplayMetrics().density;

        for (final NavRoute r : routes) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8 * dp);
            row.setLayoutParams(lp);
            row.setBackgroundColor(0xFF12233A);
            row.setPadding(14 * dp, 12 * dp, 14 * dp, 12 * dp);

            TextView t1 = new TextView(getContext());
            t1.setText(String.format(Locale.US, "%s · %.1f km · %s",
                    modeEn(r.mode), r.totalDistanceM / 1000.0, fmtDur(r.totalDurationS)));
            t1.setTextColor(0xFF00E5D1);
            t1.setTextSize(14f);
            t1.setTypeface(t1.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(t1);

            TextView t2 = new TextView(getContext());
            String dest = (r.destName != null && !r.destName.isEmpty())
                    ? r.destName
                    : String.format(Locale.US, "%.4f, %.4f", r.destLat, r.destLon);
            String when = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", r.fetchedAt).toString();
            t2.setText("→ " + dest + "   ·   " + when + "   ·   #" + r.id);
            t2.setTextColor(0xFFB0C4DE);
            t2.setTextSize(12f);
            t2.setPadding(0, 4 * dp, 0, 0);
            row.addView(t2);

            row.setOnClickListener(v -> {
                NavGuideFragment.newInstance(r.id).show(getParentFragmentManager(), "nav_guide");
                dismiss();
            });
            row.setOnLongClickListener(v -> { confirmDelete(r); return true; });

            mList.addView(row);
        }
    }

    private void confirmDelete(final NavRoute r) {
        if (getContext() == null) return;
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("Delete route")
            .setMessage("Delete this route and its saved weather?\n\n"
                    + modeEn(r.mode) + " · " + String.format(Locale.US, "%.1f km", r.totalDistanceM / 1000.0)
                    + " (#" + r.id + ")")
            .setPositiveButton("Delete", (d, w) -> Executors.newSingleThreadExecutor().execute(() -> {
                try { dao().deleteRoute(r.id); } catch (Exception ignore) {}
                mMain.post(this::reload);
            }))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private static String modeEn(String mode) {
        if ("WALK".equals(mode)) return "Walk";
        if ("VEHICLE".equals(mode)) return "Vehicle";
        if ("VESSEL".equals(mode)) return "Vessel";
        return mode != null ? mode : "-";
    }

    private static String fmtDur(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return "<1m";
    }
}
