package com.example.souls.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * IdentityPdfExporter
 *
 * Generates a Souls Identity Certificate PDF using Android's built-in
 * PdfDocument API — no external dependencies required.
 *
 * PDF generation runs on a background thread so it never blocks the UI.
 * Errors surface as a Toast instead of failing silently.
 *
 * Requires in AndroidManifest.xml:
 *   <provider android:name="androidx.core.content.FileProvider"
 *       android:authorities="${applicationId}.provider"
 *       android:exported="false"
 *       android:grantUriPermissions="true">
 *     <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
 *         android:resource="@xml/file_provider_paths" />
 *   </provider>
 *
 * Requires res/xml/file_provider_paths.xml:
 *   <paths><cache-path name="exported_pdfs" path="exports/" /></paths>
 */
public class IdentityPdfExporter {

    private static final int PAGE_WIDTH  = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN      = 48;

    private static final int COLOR_BG         = Color.parseColor("#0D0D0D");
    private static final int COLOR_CARD       = Color.parseColor("#1A1A1A");
    private static final int COLOR_BORDER     = Color.parseColor("#2E2E2E");
    private static final int COLOR_ACCENT     = Color.parseColor("#A07CFF");
    private static final int COLOR_GREEN      = Color.parseColor("#4CAF50");
    private static final int COLOR_WHITE      = Color.WHITE;
    private static final int COLOR_GREY       = Color.parseColor("#888888");
    private static final int COLOR_LIGHT_GREY = Color.parseColor("#CCCCCC");

    /**
     * Generates the PDF on a background thread and opens the share sheet.
     * Call from any Activity or Fragment.
     */
    public static void export(Context context, SessionManager sm) {
        final Context appCtx = context.getApplicationContext();
        final Handler main   = new Handler(Looper.getMainLooper());

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Build PDF
                PdfDocument document = new PdfDocument();
                PdfDocument.PageInfo pageInfo =
                        new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                drawCertificate(page.getCanvas(), sm);
                document.finishPage(page);

                // Write to cache/exports/
                File outputDir = new File(appCtx.getCacheDir(), "exports");
                if (!outputDir.exists()) outputDir.mkdirs();

                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File outputFile = new File(outputDir, "soul_identity_" + ts + ".pdf");

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    document.writeTo(fos);
                }
                document.close();

                // Share via FileProvider
                Uri uri = FileProvider.getUriForFile(
                        appCtx,
                        appCtx.getPackageName() + ".provider",
                        outputFile
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/pdf");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT,
                        "Souls Identity Certificate — " + sm.getSoulId());
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(shareIntent, "Export Identity Certificate");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                main.post(() -> appCtx.startActivity(chooser));

            } catch (Exception e) {
                e.printStackTrace();
                main.post(() -> Toast.makeText(appCtx,
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private static void drawCertificate(Canvas canvas, SessionManager sm) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Background
        p.setColor(COLOR_BG);
        canvas.drawRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, p);

        float y = MARGIN;

        // Top accent bar
        p.setColor(COLOR_ACCENT);
        canvas.drawRect(0, 0, PAGE_WIDTH, 6, p);
        y += 16;

        // Header
        p.setColor(COLOR_ACCENT);
        p.setTextSize(28f);
        p.setFakeBoldText(true);
        p.setTextAlign(Align.LEFT);
        canvas.drawText("SOULS", MARGIN, y + 22, p);

        p.setColor(COLOR_GREY);
        p.setTextSize(10f);
        p.setFakeBoldText(false);
        p.setTextAlign(Align.RIGHT);
        canvas.drawText("Proof of Humanity · Blockchain Identity", PAGE_WIDTH - MARGIN, y + 22, p);
        y += 40;

        // Divider
        p.setColor(COLOR_BORDER);
        p.setStrokeWidth(1f);
        p.setStyle(Paint.Style.STROKE);
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, p);
        y += 20;

        // Title
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Align.CENTER);
        p.setColor(COLOR_WHITE);
        p.setTextSize(13f);
        p.setLetterSpacing(0.15f);
        canvas.drawText("SOUL IDENTITY CERTIFICATE", PAGE_WIDTH / 2f, y + 14, p);
        y += 36;

        // Verified badge
        RectF badge = new RectF(PAGE_WIDTH / 2f - 90, y, PAGE_WIDTH / 2f + 90, y + 30);
        p.setColor(Color.parseColor("#1A2E1A"));
        canvas.drawRoundRect(badge, 15, 15, p);
        p.setColor(COLOR_GREEN);
        p.setTextSize(11f);
        p.setFakeBoldText(true);
        p.setLetterSpacing(0.08f);
        canvas.drawText("✓  VERIFIED HUMAN", PAGE_WIDTH / 2f, y + 20, p);
        y += 48;

        // Soul ID card
        RectF soulIdCard = new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 70);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#16102E"));
        canvas.drawRoundRect(soulIdCard, 10, 10, p);
        p.setColor(COLOR_ACCENT);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        canvas.drawRoundRect(soulIdCard, 10, 10, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(COLOR_ACCENT);
        p.setTextSize(9f);
        p.setFakeBoldText(true);
        p.setLetterSpacing(0.12f);
        p.setTextAlign(Align.CENTER);
        canvas.drawText("SOUL ID", PAGE_WIDTH / 2f, y + 18, p);

        p.setColor(COLOR_WHITE);
        p.setTextSize(22f);
        p.setLetterSpacing(0.04f);
        canvas.drawText(orDash(sm.getSoulId()), PAGE_WIDTH / 2f, y + 52, p);
        y += 90;

        // Detail rows
        p.setLetterSpacing(0f);
        p.setFakeBoldText(false);
        y = drawRow(canvas, p, y, "SOUL HASH",             orDash(sm.getSoulHash()),      true);
        y = drawRow(canvas, p, y, "BLOCK HASH",            orDash(sm.getBlockHash()),     true);
        y = drawRow(canvas, p, y, "PREVIOUS HASH",         orDash(sm.getPreviousHash()),  true);
        y = drawRow(canvas, p, y, "CHAIN POSITION",
                sm.getBlockIndex() >= 0 ? "Block #" + sm.getBlockIndex() : "—",           false);
        y = drawRow(canvas, p, y, "REGISTERED VIA LAMP",  orDash(sm.getLampId()),        false);
        y = drawRow(canvas, p, y, "FINGERPRINT VERIFIED", formatDate(sm.getVerifiedAt()),false);
        y = drawRow(canvas, p, y, "BLOCK MINED",          formatDate(sm.getBlockTimestamp()), false);
        y = drawRow(canvas, p, y, "DEVICE KEY",           truncate(sm.getDeviceKey(), 36), true);
        y += 14;

        // Divider
        p.setColor(COLOR_BORDER);
        p.setStrokeWidth(1f);
        p.setStyle(Paint.Style.STROKE);
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, p);
        y += 18;

        // Legal statement
        p.setStyle(Paint.Style.FILL);
        p.setColor(COLOR_GREY);
        p.setTextSize(8.5f);
        p.setTextAlign(Align.CENTER);
        p.setFakeBoldText(false);
        canvas.drawText(
                "This certificate confirms the above Soul ID has been verified on the Souls blockchain",
                PAGE_WIDTH / 2f, y + 10, p);
        canvas.drawText(
                "via physical LAMP proximity verification. This record is immutable and tamper-proof.",
                PAGE_WIDTH / 2f, y + 22, p);
        y += 40;

        // Issue date
        String issued = "Certificate issued: "
                + new SimpleDateFormat("dd MMM yyyy · HH:mm 'UTC'", Locale.US).format(new Date());
        p.setColor(COLOR_GREY);
        p.setTextSize(8f);
        canvas.drawText(issued, PAGE_WIDTH / 2f, y, p);

        // Bottom accent bar
        p.setColor(COLOR_ACCENT);
        p.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, PAGE_HEIGHT - 6, PAGE_WIDTH, PAGE_HEIGHT, p);
    }

    private static float drawRow(Canvas canvas, Paint p,
                                 float y, String label, String value, boolean mono) {
        float rowH = 46f;
        RectF rect = new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowH);
        p.setStyle(Paint.Style.FILL);
        p.setColor(COLOR_CARD);
        canvas.drawRoundRect(rect, 6, 6, p);

        p.setColor(COLOR_GREY);
        p.setTextSize(8f);
        p.setFakeBoldText(false);
        p.setTextAlign(Align.LEFT);
        p.setLetterSpacing(0.10f);
        canvas.drawText(label, MARGIN + 12, y + 16, p);

        p.setColor(mono ? COLOR_LIGHT_GREY : COLOR_WHITE);
        p.setTextSize(mono ? 9f : 11f);
        p.setFakeBoldText(!mono);
        p.setLetterSpacing(mono ? 0.02f : 0f);
        canvas.drawText(value, MARGIN + 12, y + 34, p);

        return y + rowH + 6;
    }

    private static String orDash(String s) {
        return (s == null || s.isEmpty()) ? "—" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "—";
        if (s.length() <= max) return s;
        return s.substring(0, max / 2) + "…" + s.substring(s.length() - max / 4);
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        try {
            String clean   = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
            String pattern = clean.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS"
                    : "yyyy-MM-dd'T'HH:mm:ss";
            SimpleDateFormat in  = new SimpleDateFormat(pattern, Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = in.parse(clean);
            SimpleDateFormat out = new SimpleDateFormat("dd MMM yyyy · HH:mm 'UTC'", Locale.US);
            out.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return out.format(date);
        } catch (Exception e) {
            return iso;
        }
    }
}