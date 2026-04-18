package com.ah.acr.messagebox;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.FirmUpdate;
import com.ah.acr.messagebox.databinding.FragmentFirmwareBinding;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class FirmwareFragment extends Fragment {

    private static final UUID BLE_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

    private static final String TAG = FirmwareFragment.class.getSimpleName();
    private FragmentFirmwareBinding binding;
    private Uri selectedFileUri;
    private byte[] firmwareData;

    private int sendPacketSize = 0;

    private BleViewModel mBleViewModel;
    private volatile boolean isTransferCancelled = false;


    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    if (selectedFileUri != null) {
                        handleSelectedFile(selectedFileUri);
                    }
                }
            });


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        mBleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentFirmwareBinding.inflate(inflater, container, false);

        setupViews();
        observeBleConnection();

        return binding.getRoot();
    }


    private void setupViews() {
        // 파일 선택 버튼
        binding.buttonSelectFile.setOnClickListener(v -> openFilePicker());

        // 전송 버튼
        binding.buttonSend.setOnClickListener(v -> sendFirmware());

        // 취소 버튼
        binding.buttonCancel.setOnClickListener(v -> cancelTransfer());

        // 초기 상태
        binding.progressContainer.setVisibility(View.GONE);
        binding.buttonSend.setEnabled(false);
        binding.buttonCancel.setEnabled(false);
    }

    private void observeBleConnection() {
        BLE.INSTANCE.getFirmwareUdateState().observe(getViewLifecycleOwner(), new Observer<FirmUpdate>() {
            @Override
            public void onChanged(FirmUpdate firmUpdate) {
                Log.v("UpdateState", firmUpdate.getState());

                if (firmUpdate.getState().equals("START")) {
                    updateFirmwareDataSender(firmUpdate.getIdx());

                } else if (firmUpdate.getState().equals("NEXT")) {
                    updateFirmwareDataSender(firmUpdate.getIdx() + 1);

                } else if (firmUpdate.getState().equals("FAILEND")) {
                    binding.textProgressStatus.setText("Upload failed!");
                    resetUI();

                } else if (firmUpdate.getState().equals("RESEND")) {
                    updateFirmwareDataSender(firmUpdate.getIdx());

                } else if (firmUpdate.getState().equals("END")) {
                    binding.textProgressStatus.setText("Upload complete!");
                    Toast.makeText(getContext(), "Firmware uploaded successfully", Toast.LENGTH_SHORT).show();
                    resetUI();
                }
            }
        });
    }


    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "Select Firmware File"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error opening file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedFile(Uri uri) {
        try {
            String fileName = getFileName(uri);
            binding.textFileName.setText(fileName);

            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                firmwareData = readBytes(inputStream);
                inputStream.close();

                binding.buttonSend.setEnabled(true);

                Toast.makeText(getContext(),
                        "File loaded: " + fileName + " (" + firmwareData.length + " bytes)",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            binding.textFileName.setText("Select a firmware file...");
            binding.buttonSend.setEnabled(false);
        }
    }

    private String getFileName(Uri uri) {
        String fileName = "Unknown";
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        }
        if (fileName.equals("Unknown")) {
            fileName = uri.getLastPathSegment();
        }
        return fileName;
    }

    private byte[] readBytes(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[8192];
        int bytesRead;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }

        return output.toByteArray();
    }


    private void sendFirmware() {
        if (firmwareData == null || firmwareData.length == 0) {
            Toast.makeText(getContext(), "No firmware file selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
            Toast.makeText(getContext(),
                    "Device is not connected. Please connect via BLE first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // UI 업데이트
        binding.buttonSelectFile.setEnabled(false);
        binding.buttonSend.setEnabled(false);
        binding.buttonCancel.setEnabled(true);
        binding.progressContainer.setVisibility(View.VISIBLE);
        binding.textProgressStatus.setText("Uploading firmware...");
        binding.progressBar.setProgress(0);
        binding.textProgressPercentage.setText("0%");

        try {
            sendPacketSize = Integer.parseInt(binding.editPacketSize.getText().toString());
        } catch (NumberFormatException e) {
            sendPacketSize = 256;
        }

        String updateStr = String.format("UOPEN=%d", firmwareData.length);
        String sendMsg = String.format("%s\n", Base64.encodeToString(updateStr.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        bleSendMessage(sendMsg);
    }

    private void cancelTransfer() {
        isTransferCancelled = true;
        Toast.makeText(getContext(), "Transfer cancelled", Toast.LENGTH_SHORT).show();
        resetToInitialState();
    }

    private void updateFirmwareDataSender(int idx) {
        int totalChunks = (int) Math.ceil((double) firmwareData.length / sendPacketSize);

        if (totalChunks < idx) {
            if (BLE.INSTANCE.getSelectedDevice().getValue() != null) {
                String sendMsg = String.format("UFILE=%d,END\n", idx);
                bleSendMessage(sendMsg);
            }
            return;
        }

        int start = (idx - 1) * sendPacketSize;
        int end = Math.min(start + sendPacketSize, firmwareData.length);
        byte[] chunk = new byte[end - start];
        System.arraycopy(firmwareData, start, chunk, 0, chunk.length);

        if (BLE.INSTANCE.getSelectedDevice().getValue() != null) {
            String sendMsg = String.format("UFILE=%d,%d,%s\n", idx, chunk.length, Base64.encodeToString(chunk, Base64.NO_WRAP));
            bleSendMessage(sendMsg);
        }

        // 진행률 업데이트
        final int progress = (int) (((idx) * 100.0) / totalChunks);
        binding.progressBar.setProgress(progress);
        binding.textProgressPercentage.setText(progress + "%");
    }


    private void resetUI() {
        if (binding == null) return;
        binding.buttonSelectFile.setEnabled(true);
        binding.buttonSend.setEnabled(firmwareData != null);
        binding.buttonCancel.setEnabled(false);
        binding.progressContainer.setVisibility(View.GONE);
        binding.progressBar.setProgress(0);
    }


    private void resetToInitialState() {
        if (binding == null) return;
        selectedFileUri = null;
        firmwareData = null;

        binding.textFileName.setText("Select a firmware file...");
        binding.buttonSelectFile.setEnabled(true);
        binding.buttonSend.setEnabled(false);
        binding.buttonCancel.setEnabled(false);
        binding.progressContainer.setVisibility(View.GONE);
        binding.progressBar.setProgress(0);
        binding.textProgressPercentage.setText("0%");
        binding.textProgressStatus.setText("");
    }

    BluetoothGattCharacteristic getWriteCharacteristic(final BleDevice bleDevice) {
        BluetoothGattService service = BleManager.getInstance().getBluetoothGatt(bleDevice).getService(BLE_SERVICE_UUID);
        if (service == null) return null;
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            int charaProp = characteristic.getProperties();
            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                return characteristic;
            }
        }
        return null;
    }

    public void bleSendMessage(String msg) {
        Log.v("WRITE BLE", "MSG SIZE : " + msg.length());
        BleDevice bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
        BluetoothGattCharacteristic characteristic = getWriteCharacteristic(bleDevice);
        if (characteristic == null) {
            BleManager.getInstance().disconnect(bleDevice);
            return;
        }

        BleManager.getInstance().write(
                bleDevice,
                BLE_SERVICE_UUID.toString(),
                characteristic.getUuid().toString(),
                msg.getBytes(),
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                        // success
                    }

                    @Override
                    public void onWriteFailure(final BleException exception) {
                        // failure
                    }
                });
    }


    private void hideKeyboard() {
        if (getActivity() != null && requireActivity().getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(requireActivity().getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
