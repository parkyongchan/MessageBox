package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
 * - Show MBTiles file list
 * - Add files via SAF
 * - Delete files
 */
public class OfflineMapFragment extends Fragment {

    private static final String TAG = "OfflineMapFragment";
    private static final String MBTILES_SUBDIR = "mbtiles";

    // UI
    private RecyclerView listMbtiles;
    private LinearLayout emptyState;
    private TextView tvFileCount;
    private TextView tvTotalSize;
    private LinearLayout btnAddFile;
    private ImageButton btnRefresh;

    // Adapter
    private MBTilesAdapter adapter;

    // File picker
    private ActivityResultLauncher<String[]> filePickerLauncher;


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

        // Initialize file picker
        setupFilePickerLauncher();

        // Initialize UI
        bindViews(view);
        setupRecyclerView();
        setupButtons();

        // Load list
        loadMBTilesList();
    }


    @Override
    public void onResume() {
        super.onResume();
        // Refresh when returning to this tab
        loadMBTilesList();
    }


    // ═══════════════════════════════════════════════════════
    //   UI Setup
    // ═══════════════════════════════════════════════════════

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


    // ═══════════════════════════════════════════════════════
    //   Load file list
    // ═══════════════════════════════════════════════════════

    private void loadMBTilesList() {
        File mbtilesDir = getMBTilesDir();
        if (!mbtilesDir.exists()) mbtilesDir.mkdirs();

        File[] files = mbtilesDir.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".mbtiles")
        );

        List<File> fileList = new ArrayList<>();
        if (files != null && files.length > 0) {
            fileList.addAll(Arrays.asList(files));
            // Sort by latest modified
            Collections.sort(fileList, (f1, f2) ->
                    Long.compare(f2.lastModified(), f1.lastModified()));
        }

        adapter.setItems(fileList);

        // Update UI
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


    // ═══════════════════════════════════════════════════════
    //   File picker (SAF)
    // ═══════════════════════════════════════════════════════

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
        // .mbtiles file MIME type is non-standard -> use "*/*" to allow all
        String[] mimeTypes = {"*/*"};
        try {
            filePickerLauncher.launch(mimeTypes);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open file picker: " + e.getMessage(), e);
            // Localized
            Toast.makeText(requireContext(),
                    getString(R.string.maps_picker_open_fail),
                    Toast.LENGTH_SHORT).show();
        }
    }


    // ═══════════════════════════════════════════════════════
    //   File copy
    // ═══════════════════════════════════════════════════════

    private void copyFileToMBTilesDir(Uri sourceUri) {
        // Get file name
        String fileName = getFileNameFromUri(sourceUri);
        if (fileName == null) {
            // Localized
            Toast.makeText(requireContext(),
                    getString(R.string.maps_filename_fail),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate extension - Localized
        if (!fileName.toLowerCase().endsWith(".mbtiles")) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.maps_invalid_file_title))
                    .setMessage(getString(R.string.maps_invalid_file_msg, fileName))
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show();
            return;
        }

        // Check target directory
        File targetDir = getMBTilesDir();
        if (!targetDir.exists()) targetDir.mkdirs();

        // Check duplicate
        File targetFile = new File(targetDir, fileName);
        if (targetFile.exists()) {
            confirmOverwrite(sourceUri, targetFile, fileName);
            return;
        }

        // Execute copy
        performCopy(sourceUri, targetFile, fileName);
    }


    private void confirmOverwrite(Uri sourceUri, File targetFile, String fileName) {
        // Localized
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.maps_overwrite_title))
                .setMessage(getString(R.string.maps_overwrite_msg, fileName))
                .setPositiveButton(getString(R.string.maps_btn_overwrite), (d, w) -> {
                    performCopy(sourceUri, targetFile, fileName);
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void performCopy(Uri sourceUri, File targetFile, String fileName) {
        // In-progress Toast - Localized
        Toast.makeText(requireContext(),
                getString(R.string.maps_copying, fileName),
                Toast.LENGTH_SHORT).show();

        // Copy on background thread
        new Thread(() -> {
            boolean success = false;
            String errorMsg = null;

            try (InputStream in = requireContext().getContentResolver().openInputStream(sourceUri);
                 FileOutputStream out = new FileOutputStream(targetFile)) {

                if (in == null) throw new IOException("Cannot open InputStream");

                byte[] buffer = new byte[8192];
                int read;
                long totalBytes = 0;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalBytes += read;
                }
                out.flush();

                Log.v(TAG, String.format("Copy completed: %s (%s)",
                        fileName, MBTilesAdapter.formatFileSize(totalBytes)));
                success = true;
            } catch (Exception e) {
                errorMsg = e.getMessage();
                Log.e(TAG, "File copy failed: " + errorMsg, e);
                // Delete incomplete file on failure
                if (targetFile.exists()) targetFile.delete();
            }

            // Show result on UI thread
            boolean finalSuccess = success;
            String finalErrorMsg = errorMsg;

            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (finalSuccess) {
                        // Localized
                        Toast.makeText(requireContext(),
                                getString(R.string.maps_copy_success,
                                        fileName,
                                        MBTilesAdapter.formatFileSize(targetFile.length())),
                                Toast.LENGTH_LONG).show();
                        loadMBTilesList();  // Refresh list
                    } else {
                        // Localized
                        Toast.makeText(requireContext(),
                                getString(R.string.maps_copy_fail, finalErrorMsg),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }


    // ═══════════════════════════════════════════════════════
    //   File delete
    // ═══════════════════════════════════════════════════════

    private void confirmDelete(File file) {
        // Localized
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


    // ═══════════════════════════════════════════════════════
    //   Utility
    // ═══════════════════════════════════════════════════════

    private File getMBTilesDir() {
        return new File(
                requireContext().getExternalFilesDir(null),
                MBTILES_SUBDIR
        );
    }


    /** Get actual file name from Uri */
    @Nullable
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;

        // Try ContentResolver
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

        // Fallback: extract from path
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
