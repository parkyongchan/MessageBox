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
 * 오프라인 지도 관리 Fragment
 * - MBTiles 파일 목록 표시
 * - SAF 로 파일 추가
 * - 파일 삭제
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

    // 파일 선택기
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

        // 파일 선택기 초기화
        setupFilePickerLauncher();

        // UI 초기화
        bindViews(view);
        setupRecyclerView();
        setupButtons();

        // 목록 로드
        loadMBTilesList();
    }


    @Override
    public void onResume() {
        super.onResume();
        // 탭 돌아올 때마다 새로고침
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
    //   파일 목록 로드
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
            // 최신순 정렬
            Collections.sort(fileList, (f1, f2) ->
                    Long.compare(f2.lastModified(), f1.lastModified()));
        }

        adapter.setItems(fileList);

        // UI 업데이트
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
    //   파일 선택기 (SAF)
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
        // .mbtiles 파일은 MIME 타입이 표준이 아님 → "*/*" 로 전체 허용
        String[] mimeTypes = {"*/*"};
        try {
            filePickerLauncher.launch(mimeTypes);
        } catch (Exception e) {
            Log.e(TAG, "파일 선택기 열기 실패: " + e.getMessage(), e);
            Toast.makeText(requireContext(),
                    "❌ 파일 선택기 열 수 없음",
                    Toast.LENGTH_SHORT).show();
        }
    }


    // ═══════════════════════════════════════════════════════
    //   파일 복사
    // ═══════════════════════════════════════════════════════

    private void copyFileToMBTilesDir(Uri sourceUri) {
        // 파일명 가져오기
        String fileName = getFileNameFromUri(sourceUri);
        if (fileName == null) {
            Toast.makeText(requireContext(),
                    "❌ 파일명을 가져올 수 없습니다",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 확장자 검증
        if (!fileName.toLowerCase().endsWith(".mbtiles")) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("잘못된 파일 형식")
                    .setMessage("선택한 파일: " + fileName + "\n\n" +
                            "MBTiles (.mbtiles) 파일만 추가할 수 있습니다.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }

        // 대상 폴더 확인
        File targetDir = getMBTilesDir();
        if (!targetDir.exists()) targetDir.mkdirs();

        // 중복 확인
        File targetFile = new File(targetDir, fileName);
        if (targetFile.exists()) {
            confirmOverwrite(sourceUri, targetFile, fileName);
            return;
        }

        // 복사 실행
        performCopy(sourceUri, targetFile, fileName);
    }


    private void confirmOverwrite(Uri sourceUri, File targetFile, String fileName) {
        new AlertDialog.Builder(requireContext())
                .setTitle("파일 이미 존재")
                .setMessage(fileName + " 이(가) 이미 있습니다.\n\n덮어쓰시겠습니까?")
                .setPositiveButton("덮어쓰기", (d, w) -> {
                    performCopy(sourceUri, targetFile, fileName);
                })
                .setNegativeButton("취소", null)
                .show();
    }


    private void performCopy(Uri sourceUri, File targetFile, String fileName) {
        // 진행 중 Toast
        Toast.makeText(requireContext(),
                "📥 " + fileName + " 복사 중...",
                Toast.LENGTH_SHORT).show();

        // 백그라운드 스레드에서 복사
        new Thread(() -> {
            boolean success = false;
            String errorMsg = null;

            try (InputStream in = requireContext().getContentResolver().openInputStream(sourceUri);
                 FileOutputStream out = new FileOutputStream(targetFile)) {

                if (in == null) throw new IOException("InputStream 을 열 수 없음");

                byte[] buffer = new byte[8192];
                int read;
                long totalBytes = 0;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalBytes += read;
                }
                out.flush();

                Log.v(TAG, String.format("복사 완료: %s (%s)",
                        fileName, MBTilesAdapter.formatFileSize(totalBytes)));
                success = true;
            } catch (Exception e) {
                errorMsg = e.getMessage();
                Log.e(TAG, "파일 복사 실패: " + errorMsg, e);
                // 실패 시 불완전한 파일 삭제
                if (targetFile.exists()) targetFile.delete();
            }

            // UI 스레드에서 결과 표시
            boolean finalSuccess = success;
            String finalErrorMsg = errorMsg;

            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (finalSuccess) {
                        Toast.makeText(requireContext(),
                                "✅ " + fileName + " 추가됨\n(" +
                                        MBTilesAdapter.formatFileSize(targetFile.length()) + ")",
                                Toast.LENGTH_LONG).show();
                        loadMBTilesList();  // 목록 새로고침
                    } else {
                        Toast.makeText(requireContext(),
                                "❌ 복사 실패: " + finalErrorMsg,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }


    // ═══════════════════════════════════════════════════════
    //   파일 삭제
    // ═══════════════════════════════════════════════════════

    private void confirmDelete(File file) {
        new AlertDialog.Builder(requireContext())
                .setTitle("파일 삭제")
                .setMessage(file.getName() + "\n(" + MBTilesAdapter.formatFileSize(file.length()) + ")\n\n" +
                        "이 오프라인 지도 파일을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (d, w) -> {
                    if (file.delete()) {
                        Toast.makeText(requireContext(),
                                "✅ " + file.getName() + " 삭제됨",
                                Toast.LENGTH_SHORT).show();
                        loadMBTilesList();
                    } else {
                        Toast.makeText(requireContext(),
                                "❌ 삭제 실패",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }


    // ═══════════════════════════════════════════════════════
    //   유틸리티
    // ═══════════════════════════════════════════════════════

    private File getMBTilesDir() {
        return new File(
                requireContext().getExternalFilesDir(null),
                MBTILES_SUBDIR
        );
    }


    /** Uri 에서 실제 파일명 가져오기 */
    @Nullable
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;

        // ContentResolver 로 시도
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
                Log.w(TAG, "ContentResolver query 실패: " + e.getMessage());
            }
        }

        // 실패 시 path 에서 파일명 추출
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
