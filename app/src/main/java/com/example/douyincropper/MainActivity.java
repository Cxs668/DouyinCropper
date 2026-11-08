package com.example.douyincropper;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 1001;
    private static final int DELETE_REQUEST = 1002;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> successfulSources = new ArrayList<>();
    private TextView status;
    private Button cropButton;
    private ProgressBar progress;
    private ArrayList<Uri> selected = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(24));
        root.setBackgroundColor(0xFF101018);

        TextView title = new TextView(this);
        title.setText("9:16 图片批量裁剪"); title.setTextColor(0xFFFFFFFF);
        title.setTextSize(25); title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView sub = new TextView(this);
        sub.setText("为抖音准备竖版图片 · 自动居中裁剪 · 高清保存");
        sub.setTextColor(0xFFB8B6C5); sub.setTextSize(14);
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(48)));

        Button choose = button("选择图片（支持多选）");
        root.addView(choose, params( dp(54), 18));
        choose.setOnClickListener(v -> pickImages());

        cropButton = button("开始批量裁剪");
        root.addView(cropButton, params(dp(54), 14));
        cropButton.setEnabled(false);
        cropButton.setOnClickListener(v -> processImages());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));

        status = new TextView(this);
        status.setText("请选择一张或多张图片。裁剪结果将保存到：\nPictures/Douyin9x16");
        status.setTextColor(0xFFE4E2EC); status.setTextSize(15);
        status.setPadding(0, dp(24), 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(this);
        note.setText("裁剪方式：保持原图比例，取画面中央区域裁成 9:16，不会拉伸。\n\n源图片只会在全部对应裁剪文件成功保存后，向 Android 申请删除授权；你可以在系统确认窗口中取消。未成功处理的图片不会申请删除。\n\n建议先用少量图片测试构图效果。");
        note.setTextColor(0xFF9B99A8); note.setTextSize(13);
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private LinearLayout.LayoutParams params(int h, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, h); p.bottomMargin = dp(bottom); return p;
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(15); return b;
    }

    private void pickImages() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGES && resultCode == RESULT_OK && data != null) {
            selected.clear();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) selected.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) selected.add(data.getData());
            cropButton.setEnabled(!selected.isEmpty());
            status.setText("已选择 " + selected.size() + " 张图片。\n点击“开始批量裁剪”执行。");
        } else if (requestCode == DELETE_REQUEST) {
            if (resultCode == RESULT_OK) status.setText("裁剪完成，系统已接受源文件删除请求。");
            else status.setText("裁剪文件已保存；你取消了源文件删除，原图保留。");
        }
    }

    private void processImages() {
        if (selected.isEmpty()) return;
        cropButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE); progress.setMax(selected.size()); progress.setProgress(0);
        successfulSources.clear();
        ArrayList<Uri> batch = new ArrayList<>(selected);
        status.setText("正在处理 0 / " + batch.size() + " …");
        worker.execute(() -> {
            int ok = 0, failed = 0;
            for (Uri uri : batch) {
                try {
                    Bitmap src;
                    try (InputStream in = getContentResolver().openInputStream(uri)) { src = BitmapFactory.decodeStream(in); }
                    if (src == null) throw new Exception("无法读取图片");
                    Bitmap out = crop916(src);
                    src.recycle();
                    if (saveBitmap(out)) { ok++; successfulSources.add(uri); } else failed++;
                    out.recycle();
                } catch (Exception e) { failed++; }
                final int done = ok + failed, good = ok, bad = failed;
                runOnUiThread(() -> { progress.setProgress(done); status.setText("正在处理 " + done + " / " + batch.size() + " …\n成功 " + good + " 张，失败 " + bad + " 张"); });
            }
            final int good = ok, bad = failed;
            runOnUiThread(() -> {
                cropButton.setEnabled(true); progress.setVisibility(View.GONE);
                status.setText("处理完成！\n成功保存：" + good + " 张\n处理失败：" + bad + " 张\n保存位置：Pictures/Douyin9x16");
                requestDeleteSources();
            });
        });
    }

    private Bitmap crop916(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        double target = 9.0 / 16.0;
        int cw, ch;
        if ((double)w / h > target) { ch = h; cw = (int)Math.round(h * target); }
        else { cw = w; ch = (int)Math.round(w / target); }
        int left = (w - cw) / 2, top = (h - ch) / 2;
        Bitmap out = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(src, new Rect(left, top, left + cw, top + ch), new Rect(0, 0, 1080, 1920), paint);
        return out;
    }

    private boolean saveBitmap(Bitmap bitmap) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Douyin_9x16_" + stamp + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Douyin9x16");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) throw new Exception("保存失败");
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            return true;
        } catch (Exception e) {
            getContentResolver().delete(uri, null, null); return false;
        }
    }

    private void requestDeleteSources() {
        if (successfulSources.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                PendingIntent request = MediaStore.createDeleteRequest(getContentResolver(), successfulSources);
                startIntentSenderForResult(request.getIntentSender(), DELETE_REQUEST, null, 0, 0, 0);
            } catch (Exception e) {
                status.append("\n无法发起系统删除授权，请在相册手动清理原图。");
            }
        } else {
            status.append("\n当前 Android 版本不支持批量系统删除授权，请在相册手动清理原图。");
        }
    }

    @Override protected void onDestroy() { super.onDestroy(); worker.shutdown(); }
}
