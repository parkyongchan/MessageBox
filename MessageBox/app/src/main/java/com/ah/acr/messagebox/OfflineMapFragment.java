package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.Dialog;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.adapter.MBTilesAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Offline Map Manager Fragment
 *
 * BUGFIX (2026-04-25):
 *   - Added progress dialog for large file copy (4+ GB MBTiles)
 *   - Cause: Users thought app was frozen during long copy operations
 *   - Solution: Modal dialog with percentage, size, cancel button
 */
public class OfflineMapFragment extends Fragment {

    private static final String TAG = "OfflineMapFragment";
    private static final String MBTILES_SUBDIR = "mbtiles";
    private static final int BUFFER_SIZE = 65536;  // 64KB - faster for large files
    private static final long PROGRESS_UPDATE_THRESHOLD = 256 * 1024;  // Update every 256KB

    private RecyclerView listMbtiles;
    private LinearLayout emptyState;
    private TextView tvFileCount;
    private TextView tvTotalSize;
    private LinearLayout btnAddFile;
    private ImageButton btnRefresh;

    private MBTilesAdapter adapter;

    private ActivityResultLauncher<String[]> filePickerLauncher;

    // ⭐ Copy progress state
    private volatile boolean copyCancelled = false;
    private Thread currentCopyThread = null;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_offline_map, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupFilePickerLauncher();
        bindViews(view);
        setupRecyclerView();
        setupButtons();
        loadMBTilesList();
    }


    @Override
    public void onResume() {
        super.onResume();
        loadMBTilesList();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancel any in-progress copy
        copyCancelled = true;
        if (currentCopyThread != null) {
            currentCopyThread.interrupt();
        }
    }


    private void bindViews(View view) {
        listMbtiles = view.findViewById(R.id.list_mbtiles);
        emptyState = view.findViewById(R.id.empty_state);
        tvFileCount = view.findViewById(R.id.tv_file_count);
        tvTotalSize = view.findViewById(R.id.tv_total_size);
        btnAddFile = view.findViewById(R.id.btn_add_file);
        btnRefresh = view.findViewById(R.id.btn_refresh);
    }


    private void setupRecyclerView() {
        adapter = new MBTilesAdapter(this::confirmDelete);
        listMbtiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        listMbtiles.setAdapter(adapter);
    }


    private void setupButtons() {
        btnAddFile.setOnClickListener(v -> openFilePicker());
        btnRefresh.setOnClickListener(v -> loadMBTilesList());
    }


    private void loadMBTilesList() {
        File mbtilesDir = getMBTilesDir();
        if (!mbtilesDir.exists()) mbtilesDir.mkdirs();

        File[] files = mbtilesDir.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".mbtiles")
        );

        List<File> fileList = new ArrayList<>();
        if (files != null && files.length > 0) {
            fileList.addAll(Arrays.asList(files));
            Collections.sort(fileList, (f1, f2) ->
                    Long.compare(f2.lastModified(), f1.lastModified()));
        }

        adapter.setItems(fileList);
        updateStats();
        updateEmptyState();
    }


    private void updateStats() {
        tvFileCount.setText(String.valueOf(adapter.getTotalCount()));
        tvTotalSize.setText(MBTilesAdapter.formatFileSize(adapter.getTotalSize()));
    }


    private void updateEmptyState() {
        if (adapter.getItemCount() == 0) {
            listMbtiles.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            listMbtiles.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }


    private void setupFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        copyFileToMBTilesDir(uri);
                    }
                }
        );
    }


    private void openFilePicker() {
        String[] mimeTypes = {"*/*"};
        try {
            filePickerLauncher.launch(mimeTypes);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open file picker: " + e.getMessage(), e);
            Toast.makeText(requireContext(),
                    getString(R.string.maps_picker_open_fail),
                    Toast.LENGTH_SHORT).show();
        }
    }


    private void copyFileToMBTilesDir(Uri sourceUri) {
        String fileName = getFileNameFromUri(sourceUri);
        if (fileName == null) {
            Toast.makeText(requireContext(),
                    getString(R.string.maps_filename_fail),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!fileName.toLowerCase().endsWith(".mbtiles")) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.maps_invalid_file_title))
                    .setMessage(getString(R.string.maps_invalid_file_msg, fileName))
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show();
            return;
        }

        File targetDir = getMBTilesDir();
        if (!targetDir.exists()) targetDir.mkdirs();

        File targetFile = new File(targetDir, fileName);
        if (targetFile.exists()) {
            confirmOverwrite(sourceUri, targetFile, fileName);
            return;
        }

        performCopyWithProgress(sourceUri, targetFile, fileName);
    }


    private void confirmOverwrite(Uri sourceUri, File targetFile, String fileName) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.maps_overwrite_title))
                .setMessage(getString(R.string.maps_overwrite_msg, fileName))
                .setPositiveButton(getString(R.string.maps_btn_overwrite), (d, w) -> {
                    performCopyWithProgress(sourceUri, targetFile, fileName);
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    /**
     * ⭐ NEW: Copy file with progress dialog.
     *
     * Shows modal dialog with:
     * - File name
     * - Progress bar (percentage)
     * - "X.XX GB / Y.YY GB" copied
     * - Cancel button
     *
     * Cancel deletes partial file.
     */
    private void performCopyWithProgress(Uri sourceUri, File targetFile, String fileName) {
        // Get total size first (for progress calculation)
        final long totalSize = getFileSize(sourceUri);

        // Build progress dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_copy_progress, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_progress_title);
        TextView tvFileName = dialogView.findViewById(R.id.tv_progress_filename);
        TextView tvProgressText = dialogView.findViewById(R.id.tv_progress_text);
        TextView tvProgressPercent = dialogView.findViewById(R.id.tv_progress_percent);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);

        tvTitle.setText(getString(R.string.maps_progress_title));
        tvFileName.setText(fileName);
        tvProgressText.setText(getString(R.string.maps_progress_starting));
        tvProgressPercent.setText("0%");
        progressBar.setProgress(0);

        builder.setView(dialogView);
        builder.setNegativeButton(getString(R.string.btn_cancel), (d, w) -> {
            copyCancelled = true;
            if (currentCopyThread != null) {
                currentCopyThread.interrupt();
            }
        });
        builder.setCancelable(false);

        AlertDialog progressDialog = builder.create();
        progressDialog.show();

        // Reset cancel flag
        copyCancelled = false;

        // Start copy thread
        currentCopyThread = new Thread(() -> {
            boolean success = false;
            String errorMsg = null;
            long copiedBytes = 0;

            try (InputStream in = requireContext().getContentResolver().openInputStream(sourceUri);
                 FileOutputStream out = new FileOutputStream(targetFile)) {

                if (in == null) throw new IOException("Cannot open InputStream");

                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                long lastUpdate = 0;

                while ((read = in.read(buffer)) != -1) {
                    if (copyCancelled || Thread.currentThread().isInterrupted()) {
                        throw new IOException("Cancelled by user");
                    }

                    out.write(buffer, 0, read);
                    copiedBytes += read;

                    // Throttle UI updates (every 256KB)
                    if (copiedBytes - lastUpdate >= PROGRESS_UPDATE_THRESHOLD || copiedBytes == totalSize) {
                        lastUpdate = copiedBytes;
                        final long copiedFinal = copiedBytes;
                        uiHandler.post(() -> {
                            if (totalSize > 0) {
                                int percent = (int) ((copiedFinal * 100) / totalSize);
                                progressBar.setProgress(percent);
                                tvProgressPercent.setText(percent + "%");
                                tvProgressText.setText(String.format("%s / %s",
                                        MBTilesAdapter.formatFileSize(copiedFinal),
                                        MBTilesAdapter.formatFileSize(totalSize)));
                            } else {
                                tvProgressText.setText(MBTilesAdapter.formatFileSize(copiedFinal));
                            }
                        });
                    }
                }

                out.flush();

                if (!copyCancelled) {
                    Log.v(TAG, String.format("Copy completed: %s (%s)",
                            fileName, MBTilesAdapter.formatFileSize(copiedBytes)));
                    success = true;
                }

            } catch (Exception e) {
                errorMsg = e.getMessage();
                Log.e(TAG, "File copy failed: " + errorMsg, e);
                if (targetFile.exists()) targetFile.delete();
            }

            // Show result on UI thread
            final boolean finalSuccess = success;
            final String finalErrorMsg = errorMsg;
            final long finalCopiedBytes = copiedBytes;
            final boolean wasCancelled = copyCancelled;

            uiHandler.post(() -> {
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {}

                if (getActivity() == null) return;

                if (finalSuccess) {
                    Toast.makeText(requireContext(),
                            getString(R.string.maps_copy_success,
                                    fileName,
                                    MBTilesAdapter.formatFileSize(targetFile.length())),
                            Toast.LENGTH_LONG).show();
                    loadMBTilesList();
                } else if (wasCancelled) {
                    Toast.makeText(requireContext(),
                            getString(R.string.maps_copy_cancelled),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(),
                            getString(R.string.maps_copy_fail, finalErrorMsg),
                            Toast.LENGTH_LONG).show();
                }

                currentCopyThread = null;
            });
        });

        currentCopyThread.start();
    }


    /**
     * Get file size from Uri (for progress calculation).
     * Returns -1 if unable to determine.
     */
    private long getFileSize(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0) {
                    return cursor.getLong(sizeIdx);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Cannot get file size: " + e.getMessage());
        }
        return -1;
    }


    private void confirmDelete(File file) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.maps_delete_title))
                .setMessage(getString(R.string.maps_delete_msg,
                        file.getName(),
                        MBTilesAdapter.formatFileSize(file.length())))
                .setPositiveButton(getString(R.string.addr_btn_delete), (d, w) -> {
                    if (file.delete()) {
                        Toast.makeText(requireContext(),
                                getString(R.string.maps_delete_success, file.getName()),
                                Toast.LENGTH_SHORT).show();
                        loadMBTilesList();
                    } else {
                        Toast.makeText(requireContext(),
                                getString(R.string.maps_delete_fail),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private File getMBTilesDir() {
        return new File(
                requireContext().getExternalFilesDir(null),
                MBTILES_SUBDIR
        );
    }


    @Nullable
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;

        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "ContentResolver query failed: " + e.getMessage());
            }
        }

        if (fileName == null && uri.getPath() != null) {
            String path = uri.getPath();
            int slashIdx = path.lastIndexOf('/');
            if (slashIdx >= 0) {
                fileName = path.substring(slashIdx + 1);
            }
        }

        return fileName;
    }
}
