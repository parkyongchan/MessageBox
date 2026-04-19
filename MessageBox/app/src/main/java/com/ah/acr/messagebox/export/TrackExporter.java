package com.ah.acr.messagebox.export;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.ah.acr.messagebox.database.MyTrackEntity;
import com.ah.acr.messagebox.database.MyTrackPointEntity;
import com.ah.acr.messagebox.util.ImeiStorage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class TrackExporter {

    private static final String TAG = "TrackExporter";
    private static final String EXPORTS_DIR = "exports";

    public enum Format {
        GPX("gpx", "application/gpx+xml"),
        KML("kml", "application/vnd.google-earth.kml+xml"),
        // ⭐ CSV MIME: use Excel-compatible type
        CSV("csv", "application/vnd.ms-excel");

        public final String ext;
        public final String mimeType;

        Format(String ext, String mimeType) {
            this.ext = ext;
            this.mimeType = mimeType;
        }
    }


    public static class ExportResult {
        public final boolean success;
        public final File file;
        public final String errorMessage;

        private ExportResult(boolean success, File file, String error) {
            this.success = success;
            this.file = file;
            this.errorMessage = error;
        }

        public static ExportResult ok(File file) {
            return new ExportResult(true, file, null);
        }

        public static ExportResult fail(String error) {
            return new ExportResult(false, null, error);
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    public static ExportResult exportTrack(
            Context context,
            MyTrackEntity track,
            List<MyTrackPointEntity> points,
            Format format
    ) {
        if (track == null || points == null || points.isEmpty()) {
            return ExportResult.fail("No track points to export");
        }

        try {
            File exportsDir = getExportsDir(context);
            String filename = buildFilename(context, track, format);
            File outFile = new File(exportsDir, filename);

            String content;
            switch (format) {
                case GPX:
                    content = buildGpx(track, points);
                    break;
                case KML:
                    content = buildKml(track, points);
                    break;
                case CSV:
                    content = buildCsv(track, points);
                    break;
                default:
                    return ExportResult.fail("Unknown format");
            }

            writeToFile(outFile, content);

            Log.v(TAG, "Exported " + format + " to " + outFile.getAbsolutePath());
            return ExportResult.ok(outFile);

        } catch (Exception e) {
            Log.e(TAG, "Export failed: " + e.getMessage(), e);
            return ExportResult.fail("Export failed: " + e.getMessage());
        }
    }


    /** Share Intent - simple and compatible */
    public static Intent buildShareIntent(Context context, File file, Format format) {
        Uri fileUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(format.mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return Intent.createChooser(intent, "Share track");
    }


    public static File getExportsDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), EXPORTS_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            Log.v(TAG, "Exports dir created: " + created + " at " + dir.getAbsolutePath());
        }
        return dir;
    }


    public static String getDisplayPath(File file) {
        return "exports/" + file.getName();
    }


    // ═══════════════════════════════════════════════════════════════
    //   FILENAME BUILDER (with IMEI prefix)
    // ═══════════════════════════════════════════════════════════════

    private static String buildFilename(
            Context context,
            MyTrackEntity track,
            Format format
    ) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US);
        String timestamp = track.getStartTime() != null
                ? fmt.format(track.getStartTime())
                : fmt.format(new java.util.Date());

        String imei = ImeiStorage.getSanitizedLast(context);

        String filename;
        if (imei != null && !imei.isEmpty()) {
            filename = imei + "_track_" + timestamp + "." + format.ext;
        } else {
            filename = "track_" + timestamp + "." + format.ext;
        }

        return filename;
    }


    // ═══════════════════════════════════════════════════════════════
    //   GPX BUILDER
    // ═══════════════════════════════════════════════════════════════

    private static String buildGpx(MyTrackEntity track, List<MyTrackPointEntity> points) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"TYTO Connect\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");

        sb.append("  <metadata>\n");
        sb.append("    <n>").append(escapeXml(track.getName())).append("</n>\n");
        if (track.getStartTime() != null) {
            sb.append("    <time>").append(iso.format(track.getStartTime())).append("</time>\n");
        }
        sb.append("  </metadata>\n");

        sb.append("  <trk>\n");
        sb.append("    <n>").append(escapeXml(track.getName())).append("</n>\n");
        sb.append("    <trkseg>\n");

        for (MyTrackPointEntity p : points) {
            sb.append("      <trkpt lat=\"")
                    .append(String.format(Locale.US, "%.7f", p.getLatitude()))
                    .append("\" lon=\"")
                    .append(String.format(Locale.US, "%.7f", p.getLongitude()))
                    .append("\">\n");

            if (p.getAltitude() != 0) {
                sb.append("        <ele>")
                        .append(String.format(Locale.US, "%.2f", p.getAltitude()))
                        .append("</ele>\n");
            }

            if (p.getTimestamp() != null) {
                sb.append("        <time>")
                        .append(iso.format(p.getTimestamp()))
                        .append("</time>\n");
            }

            sb.append("      </trkpt>\n");
        }

        sb.append("    </trkseg>\n");
        sb.append("  </trk>\n");
        sb.append("</gpx>\n");

        return sb.toString();
    }


    // ═══════════════════════════════════════════════════════════════
    //   KML BUILDER
    // ═══════════════════════════════════════════════════════════════

    private static String buildKml(MyTrackEntity track, List<MyTrackPointEntity> points) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        sb.append("  <Document>\n");
        sb.append("    <n>").append(escapeXml(track.getName())).append("</n>\n");

        if (track.getStartTime() != null) {
            sb.append("    <description>Started: ")
                    .append(iso.format(track.getStartTime()))
                    .append("</description>\n");
        }

        sb.append("    <Style id=\"trackStyle\">\n");
        sb.append("      <LineStyle>\n");
        sb.append("        <color>ff0000ff</color>\n");
        sb.append("        <width>4</width>\n");
        sb.append("      </LineStyle>\n");
        sb.append("    </Style>\n");

        if (!points.isEmpty()) {
            MyTrackPointEntity start = points.get(0);
            sb.append("    <Placemark>\n");
            sb.append("      <n>Start</n>\n");
            sb.append("      <Point>\n");
            sb.append("        <coordinates>")
                    .append(String.format(Locale.US, "%.7f,%.7f,%.2f",
                            start.getLongitude(), start.getLatitude(), start.getAltitude()))
                    .append("</coordinates>\n");
            sb.append("      </Point>\n");
            sb.append("    </Placemark>\n");
        }

        if (points.size() > 1) {
            MyTrackPointEntity end = points.get(points.size() - 1);
            sb.append("    <Placemark>\n");
            sb.append("      <n>End</n>\n");
            sb.append("      <Point>\n");
            sb.append("        <coordinates>")
                    .append(String.format(Locale.US, "%.7f,%.7f,%.2f",
                            end.getLongitude(), end.getLatitude(), end.getAltitude()))
                    .append("</coordinates>\n");
            sb.append("      </Point>\n");
            sb.append("    </Placemark>\n");
        }

        sb.append("    <Placemark>\n");
        sb.append("      <n>Path</n>\n");
        sb.append("      <styleUrl>#trackStyle</styleUrl>\n");
        sb.append("      <LineString>\n");
        sb.append("        <tessellate>1</tessellate>\n");
        sb.append("        <coordinates>\n");
        for (MyTrackPointEntity p : points) {
            sb.append("          ")
                    .append(String.format(Locale.US, "%.7f,%.7f,%.2f",
                            p.getLongitude(), p.getLatitude(), p.getAltitude()))
                    .append("\n");
        }
        sb.append("        </coordinates>\n");
        sb.append("      </LineString>\n");
        sb.append("    </Placemark>\n");

        sb.append("  </Document>\n");
        sb.append("</kml>\n");

        return sb.toString();
    }


    // ═══════════════════════════════════════════════════════════════
    //   CSV BUILDER
    // ═══════════════════════════════════════════════════════════════

    private static String buildCsv(MyTrackEntity track, List<MyTrackPointEntity> points) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        StringBuilder sb = new StringBuilder();

        sb.append("# Track: ").append(track.getName()).append("\n");
        sb.append("# Distance: ").append(String.format(Locale.US, "%.2f km",
                track.getTotalDistance() / 1000.0)).append("\n");
        sb.append("# Points: ").append(track.getPointCount()).append("\n");
        sb.append("# Avg Speed: ").append(String.format(Locale.US, "%.2f km/h",
                track.getAvgSpeed())).append("\n");
        sb.append("# Max Speed: ").append(String.format(Locale.US, "%.2f km/h",
                track.getMaxSpeed())).append("\n");
        if (track.getStartTime() != null) {
            sb.append("# Started: ").append(fmt.format(track.getStartTime())).append("\n");
        }
        sb.append("\n");

        sb.append("Index,Timestamp,Latitude,Longitude,Altitude_m,Speed_kmh,Bearing_deg,Accuracy_m\n");

        for (int i = 0; i < points.size(); i++) {
            MyTrackPointEntity p = points.get(i);
            sb.append(i + 1).append(",");
            sb.append(p.getTimestamp() != null ? fmt.format(p.getTimestamp()) : "").append(",");
            sb.append(String.format(Locale.US, "%.7f", p.getLatitude())).append(",");
            sb.append(String.format(Locale.US, "%.7f", p.getLongitude())).append(",");
            sb.append(String.format(Locale.US, "%.2f", p.getAltitude())).append(",");
            sb.append(String.format(Locale.US, "%.2f", p.getSpeed())).append(",");
            sb.append(String.format(Locale.US, "%.1f", p.getBearing())).append(",");
            sb.append(String.format(Locale.US, "%.1f", p.getAccuracy())).append("\n");
        }

        return sb.toString();
    }


    // ═══════════════════════════════════════════════════════════════
    //   UTIL
    // ═══════════════════════════════════════════════════════════════

    private static void writeToFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            // UTF-8 BOM for CSV to help Excel recognize encoding
            if (file.getName().toLowerCase().endsWith(".csv")) {
                writer.write('\ufeff');
            }
            writer.write(content);
            writer.flush();
        }
    }


    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
