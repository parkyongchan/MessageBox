package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceStatus;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.databinding.FragmentSosBinding;
import com.ah.acr.messagebox.search.SearchDialogFragment;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;


public class SosFragment extends Fragment {
    private static final String TAG = SosFragment.class.getSimpleName();

    private FragmentSosBinding binding;
    private KeyViewModel mKeyViewModel;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    // Dark theme SOS colors
    private static final int COLOR_SOS_ACTIVE    = 0xFFFF5252;  // Red (emergency)
    private static final int COLOR_SOS_INACTIVE  = 0xFF3A1A1A;  // Dark red (inactive)
    private static final int COLOR_STOP_ACTIVE   = 0xFF00E5D1;  // Cyan (stop active)
    private static final int COLOR_STOP_INACTIVE = 0xFF152A4A;  // Dark card background


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);
        addressViewModel = new ViewModelProvider(requireActivity()).get(AddressViewModel.class);
        mBleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentSosBinding.inflate(inflater, container, false);

        binding.getRoot().setOnClickListener(v -> hideKeyboard());

        // Receiver card click -> dialog
        binding.layoutReceiverDisplay.setOnClickListener(v -> showReceiverMenu());

        // Device setting receive (existing logic)
        BLE.INSTANCE.getDeviceSet().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s == null || !s.startsWith("SET=")) return;

                String msg = s.substring(4);
                String[] vals = msg.split(",");

                if (vals[0].equals("OK") || vals[0].equals("FAIL")) {
                    // Localized
                    Toast.makeText(getContext(),
                            getString(R.string.sos_setting_result) + vals[0],
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // SOS protocol: SET=SOS,T0000,D0000,receiver
                // vals[0]=SOS, vals[1]=T, vals[2]=D, vals[3]=receiver
                // Note: existing code used vals[4] but actual receiver seems to be vals[3]
                //       kept for backward compatibility with length check
                String receiver = null;
                if (vals.length > 4) {
                    receiver = vals[4];
                } else if (vals.length > 3) {
                    receiver = vals[3];
                }

                if (receiver != null) {
                    if (!receiver.equals("0")) {
                        final String finalReceiver = receiver;
                        addressViewModel.getAddressByNumbers(receiver).observe(getViewLifecycleOwner(), addressEntity -> {
                            if (addressEntity != null) {
                                setReceiverFromContact(finalReceiver, addressEntity.getNumbersNic());
                            } else {
                                setReceiverManual(finalReceiver);
                            }
                        });
                    } else {
                        setReceiverWeb();
                    }
                }
            }
        });

        // Observe device status (show SOS mode)
        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), new Observer<DeviceStatus>() {
            @Override
            public void onChanged(@Nullable final DeviceStatus status) {
                if (BLE.INSTANCE.getSelectedDevice().getValue() != null && status != null) {
                    updateStartStopButtonState(status.isSosMode());
                }
            }
        });

        return binding.getRoot();
    }


    /** Update Start/Stop button visual state */
    private void updateStartStopButtonState(boolean isSosMode) {
        if (binding == null) return;

        if (isSosMode) {
            binding.buttonSetStart.setBackgroundColor(COLOR_SOS_INACTIVE);
            binding.buttonSetStop.setBackgroundColor(COLOR_STOP_ACTIVE);
        } else {
            binding.buttonSetStart.setBackgroundColor(COLOR_SOS_ACTIVE);
            binding.buttonSetStop.setBackgroundColor(COLOR_STOP_INACTIVE);
        }
    }


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // SOS Start (LOCATION=4) - Localized
        binding.buttonSetStart.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.sos_dialog_start_title))
                    .setMessage(getString(R.string.sos_dialog_start_message))
                    .setPositiveButton(getString(R.string.sos_btn_start), (d, w) ->
                            BLE.INSTANCE.getWriteQueue().offer("LOCATION=4"))
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
        });

        // SOS Stop (LOCATION=5)
        binding.buttonSetStop.setOnClickListener(v ->
                BLE.INSTANCE.getWriteQueue().offer("LOCATION=5"));

        // Save button
        binding.buttonSetSave.setOnClickListener(v -> handleSave());
    }


    // ═══════════════════════════════════════════════════════════════
    //   RECEIVER MENU
    // ═══════════════════════════════════════════════════════════════

    /** Receiver selection dialog - Localized */
    private void showReceiverMenu() {
        String[] options = {
                getString(R.string.sos_receiver_option_web),
                getString(R.string.sos_receiver_option_contact),
                getString(R.string.sos_receiver_option_manual)
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.sos_receiver_menu_title))
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: setReceiverWeb(); break;
                        case 1: showAddressBookPicker(); break;
                        case 2: showManualInputDialog(); break;
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    /** Set to Web Server (empty value) - Localized */
    private void setReceiverWeb() {
        binding.textReceiver.setText("");
        binding.textReceiverIcon.setText("📧");
        binding.textReceiverLabel.setText(getString(R.string.sos_receiver_web));
        binding.textReceiverSub.setText(getString(R.string.sos_receiver_web_sub));
    }


    /** Set to contact from Address Book */
    private void setReceiverFromContact(String number, String nickname) {
        binding.textReceiver.setText(nickname != null ? nickname : number);
        binding.textReceiverIcon.setText("📇");
        binding.textReceiverLabel.setText(nickname != null ? nickname : number);
        binding.textReceiverSub.setText(number);
    }


    /** Set to manually entered number - Localized */
    private void setReceiverManual(String number) {
        binding.textReceiver.setText(number);
        binding.textReceiverIcon.setText("⌨");
        binding.textReceiverLabel.setText(number);
        binding.textReceiverSub.setText(getString(R.string.sos_receiver_manual_sub));
    }


    /** Address Book picker dialog */
    private void showAddressBookPicker() {
        setupFragmentResultListener();
        SearchDialogFragment searchDialog = new SearchDialogFragment();
        searchDialog.show(getParentFragmentManager(), "SearchDialog");
    }


    /** Manual input dialog - Localized */
    private void showManualInputDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.sos_manual_hint));
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.sos_manual_dialog_title))
                .setView(input)
                .setPositiveButton(getString(R.string.btn_ok), (dialog, which) -> {
                    String number = input.getText().toString().trim();
                    if (!number.isEmpty() && number.matches("\\d+")) {
                        setReceiverManual(number);
                    } else if (!number.isEmpty()) {
                        // Localized
                        Toast.makeText(getContext(),
                                getString(R.string.sos_imei_digits_only),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   SAVE LOGIC
    // ═══════════════════════════════════════════════════════════════

    private void handleSave() {
        String nicName = binding.textReceiver.getText().toString().trim();

        if (nicName.isEmpty()) {
            // Send to Web Server
            buildAndSendSetting("0");
        } else {
            // Lookup in Address Book
            addressViewModel.getAddressByNicName(nicName).observe(getViewLifecycleOwner(), addressEntity -> {
                String codeNum = nicName;
                if (addressEntity != null) {
                    codeNum = addressEntity.getNumbers();
                }

                if (!codeNum.matches("\\d+")) {
                    // Localized
                    Toast.makeText(getContext(),
                            getString(R.string.sos_invalid_receiver),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                buildAndSendSetting(codeNum);
            });
        }
    }


    private void buildAndSendSetting(String codeNum) {
        StringBuilder setting = new StringBuilder();
        setting.append("SET=SOS,T0000,D0000,");
        setting.append(codeNum);

        Log.v(TAG, setting.toString());
        BLE.INSTANCE.getWriteQueue().offer(setting.toString());

        // Localized
        Toast.makeText(getContext(),
                getString(R.string.sos_settings_sent),
                Toast.LENGTH_SHORT).show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   SEARCH DIALOG (existing)
    // ═══════════════════════════════════════════════════════════════

    private void setupFragmentResultListener() {
        getParentFragmentManager().setFragmentResultListener("search_result", this,
                new FragmentResultListener() {
                    @Override
                    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                        int selectedId = bundle.getInt("selected_id");
                        String selectedTitle = bundle.getString("selected_nic");
                        String selectedDescription = bundle.getString("selected_code");
                        handleSearchResult(selectedId, selectedTitle, selectedDescription);
                    }
                });
    }


    private void handleSearchResult(int id, String title, String code) {
        if (getContext() != null) {
            setReceiverFromContact(code, title);
        }
    }


    private void hideKeyboard() {
        if (getActivity() != null && requireActivity().getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager)
                    requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(
                    requireActivity().getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
