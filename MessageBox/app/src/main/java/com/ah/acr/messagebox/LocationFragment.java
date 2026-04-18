package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.adapter.LocationAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationViewModel;
import com.ah.acr.messagebox.database.LocationWithAddress;
import com.ah.acr.messagebox.databinding.FragmentLocationBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class LocationFragment extends Fragment {
    private static final String TAG = LocationFragment.class.getSimpleName();

    private FragmentLocationBinding binding;
    private LocationAdapter mAdapter;
    private LocationViewModel locationViewModel;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // 필터 칩 모드 값
    private static final int MODE_ALL = 0;
    private static final int MODE_TRACK = 2;
    private static final int MODE_SOS = 4;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentLocationBinding.inflate(inflater, container, false);

        setupRecyclerView();
        setupViewModel();
        setupFilterChips();
        setupDatePickers();
        setupSearch();
        setupRefresh();
        observeData();

        // ⭐ 초기 선택 상태 설정 (XML에서 못하는 부분)
        binding.chip7d.setSelected(true);     // 기본: 7일
        binding.chipAll.setSelected(true);    // 기본: All

        return binding.getRoot();
    }


    private void setupViewModel() {
        mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);
        addressViewModel = new ViewModelProvider(this).get(AddressViewModel.class);
    }


    private void setupRecyclerView() {
        mAdapter = new LocationAdapter(new LocationAdapter.OnLocationClickListener() {
            @Override
            public void onLocationClick(LocationEntity location) {
                handleLocationClick(location);
            }

            @Override
            public void onLocationDeleteClick(LocationEntity location) {
                handleLocationDelClick(location);
            }

            @Override
            public void onLocationCopyClick(LocationEntity location) {
                handleLocationCopyClick(location);
            }

            @Override
            public void onLocationMapClick(LocationEntity location) {
                handleLocationMapClick(location);
            }

            @Override
            public void onAddressClick(LocationWithAddress location) {
                handleAddressClick(location);
            }
        });

        RecyclerView recyclerView = binding.listLocation;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(mAdapter);
    }


    /** 필터 칩 클릭 리스너 */
    private void setupFilterChips() {
        // Quick 날짜 칩
        binding.chip1h.setOnClickListener(v -> selectQuickDate(v, 1, true));
        binding.chip24h.setOnClickListener(v -> selectQuickDate(v, 24, true));
        binding.chip3d.setOnClickListener(v -> selectQuickDate(v, 3, false));
        binding.chip7d.setOnClickListener(v -> selectQuickDate(v, 7, false));
        binding.chip30d.setOnClickListener(v -> selectQuickDate(v, 30, false));

        // 모드 필터 칩 (All / Track / SOS)
        binding.chipAll.setOnClickListener(v -> selectModeChip(v, MODE_ALL));
        binding.chipTrack.setOnClickListener(v -> selectModeChip(v, MODE_TRACK));
        binding.chipSos.setOnClickListener(v -> selectModeChip(v, MODE_SOS));
    }


    /** Quick 날짜 선택 */
    private void selectQuickDate(View chip, int value, boolean isHours) {
        // 모든 Quick 칩 선택 해제
        binding.chip1h.setSelected(false);
        binding.chip24h.setSelected(false);
        binding.chip3d.setSelected(false);
        binding.chip7d.setSelected(false);
        binding.chip30d.setSelected(false);

        // 선택한 칩 활성화
        chip.setSelected(true);

        // ViewModel 업데이트
        if (isHours) {
            locationViewModel.setQuickRange(value);
        } else {
            locationViewModel.setQuickDays(value);
        }
    }


    /** 모드 칩 선택 (All / Track / SOS) */
    private void selectModeChip(View chip, int mode) {
        binding.chipAll.setSelected(false);
        binding.chipTrack.setSelected(false);
        binding.chipSos.setSelected(false);

        chip.setSelected(true);
        locationViewModel.setFilterMode(mode);
    }


    /** 날짜 선택 Picker */
    private void setupDatePickers() {
        binding.btnStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.btnEndDate.setOnClickListener(v -> showDatePicker(false));
    }

    private void showDatePicker(boolean isStart) {
        Date current = isStart
                ? locationViewModel.getStartDate().getValue()
                : locationViewModel.getEndDate().getValue();

        Calendar cal = Calendar.getInstance();
        if (current != null) cal.setTime(current);

        new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    Calendar c = Calendar.getInstance();
                    c.set(year, month, day, isStart ? 0 : 23,
                            isStart ? 0 : 59, isStart ? 0 : 59);
                    if (isStart) {
                        locationViewModel.setStartDate(c.getTime());
                    } else {
                        locationViewModel.setEndDate(c.getTime());
                    }
                    // Quick 칩 선택 해제
                    clearQuickChips();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void clearQuickChips() {
        binding.chip1h.setSelected(false);
        binding.chip24h.setSelected(false);
        binding.chip3d.setSelected(false);
        binding.chip7d.setSelected(false);
        binding.chip30d.setSelected(false);
    }


    /** 검색어 입력 */
    private void setupSearch() {
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                locationViewModel.setSearchText(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }


    private void setupRefresh() {
        binding.buttonReflesh.setOnClickListener(view -> {
            BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            locationViewModel.refresh();
        });

        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            }
        });
    }


    /** 데이터 관찰 */
    private void observeData() {
        // 필터링된 장비별 최신 위치
        locationViewModel.getFilteredLocations().observe(getViewLifecycleOwner(), new Observer<List<LocationWithAddress>>() {
            @Override
            public void onChanged(List<LocationWithAddress> locations) {
                mAdapter.submitList(locations);

                // 카운트 업데이트
                int count = locations != null ? locations.size() : 0;
                binding.tvCount.setText(String.valueOf(count));
                binding.tvPoints.setText(String.valueOf(count));

                // 빈 상태 처리
                if (count == 0) {
                    binding.listLocation.setVisibility(View.GONE);
                    binding.emptyState.setVisibility(View.VISIBLE);
                } else {
                    binding.listLocation.setVisibility(View.VISIBLE);
                    binding.emptyState.setVisibility(View.GONE);
                }
            }
        });

        // 날짜 변경 시 UI 업데이트
        locationViewModel.getStartDate().observe(getViewLifecycleOwner(), date -> {
            if (date != null) {
                binding.tvStartDate.setText(dateFmt.format(date));
            }
        });

        locationViewModel.getEndDate().observe(getViewLifecycleOwner(), date -> {
            if (date != null) {
                binding.tvEndDate.setText(dateFmt.format(date));
            }
        });
    }


    // ═══════════════════════════════════════════════════════
    // 아이템 클릭 핸들러
    // ═══════════════════════════════════════════════════════

    public void handleLocationClick(LocationEntity location) {
        Log.v(TAG, "Click Item: " + location.getCodeNum());
        // TODO: Phase 4 - 상세 화면 이동 (전체 트랙)
        Toast.makeText(getContext(),
                "Track detail coming soon (" + location.getCodeNum() + ")",
                Toast.LENGTH_SHORT).show();
    }

    public void handleLocationDelClick(LocationEntity location) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Location")
                .setMessage("Are you sure you want to delete this location?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    locationViewModel.delete(location);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void handleLocationCopyClick(LocationEntity location) {
        String loc = String.format(Locale.US, "%f,%f",
                location.getLatitude(), location.getLongitude());
        ClipboardManager clipboard = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("copy", loc);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Copied to clipboard.", Toast.LENGTH_SHORT).show();
    }

    public void handleLocationMapClick(LocationEntity location) {
        Bundle bundle = new Bundle();
        bundle.putString("title", location.getNicName() != null
                ? location.getNicName() : location.getCodeNum());
        bundle.putDouble("lat", location.getLatitude());
        bundle.putDouble("lng", location.getLongitude());

        try {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_main_location_fragment_to_main_map_fragment, bundle);
        } catch (Exception e) {
            Log.e(TAG, "Navigation error: " + e);
            Toast.makeText(getContext(), "Map view not available.", Toast.LENGTH_SHORT).show();
        }
    }

    public void handleAddressClick(LocationWithAddress location) {
        showAddressDialog(location);
    }


    private void showAddressDialog(LocationWithAddress location) {
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_address);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etName = dialog.findViewById(R.id.et_name);
        EditText etCode = dialog.findViewById(R.id.et_code);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);
        ImageView btnClose = dialog.findViewById(R.id.btn_close);

        if (location.getAddress() == null) {
            etName.setText(location.getLocation().getCodeNum());
        } else {
            etName.setText(location.getAddress().getNumbersNic());
        }
        etCode.setText(location.getLocation().getCodeNum());
        etCode.setEnabled(false);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String code = etCode.getText().toString().trim();

            if (name.isEmpty() || code.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (location.getAddress() == null) {
                addressViewModel.insert(new AddressEntity(0, code, name, new Date(), null));
            } else {
                addressViewModel.updateNumbersNic(code, name);
            }

            dialog.dismiss();
            Toast.makeText(getContext(), "Saved.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}