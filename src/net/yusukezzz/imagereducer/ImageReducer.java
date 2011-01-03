package net.yusukezzz.imagereducer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

public class ImageReducer extends Activity {
    private final static String TEMP_DIR             = Environment.getExternalStorageDirectory().getAbsolutePath()
                                                             + File.separator + "ImageReducer";
    // 長辺
    private final static int    LONG_SIDE            = 640;
    // 画質
    private final static int    OUTPUT_IMAGE_QUALITY = 80;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        ContentResolver cr = getContentResolver();
        InputStream is = null;
        FileOutputStream fos = null;
        ByteArrayOutputStream baos = null;

        // 画像だったら縮小
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            try {
                // 元画像の向きを取得
                Cursor query = MediaStore.Images.Media.query(cr, uri, new String[] {
                        MediaStore.Images.ImageColumns.DISPLAY_NAME, MediaStore.Images.ImageColumns.ORIENTATION },
                        null, null);
                query.moveToFirst();
                String file_name = query.getString(0);
                int orientation = query.getInt(1);

                // オリジナルを bitmap で取得
                is = cr.openInputStream(uri);
                Bitmap bm_org = BitmapFactory.decodeStream(is);
                is.close();

                // width と height の長さを取得
                int width = bm_org.getWidth();
                int height = bm_org.getHeight();

                // 長辺を 640px に縮小したときの倍率を取得
                float scale = (width >= height) ? ((float) LONG_SIDE) / width : ((float) LONG_SIDE) / height;
                // 等倍縮小＆正しい向きになるように回転
                Matrix matrix = new Matrix();
                matrix.postScale(scale, scale);
                matrix.postRotate(orientation);
                // リサイズ＆回転後の画像生成
                Bitmap resized = Bitmap.createBitmap(bm_org, 0, 0, width, height, matrix, true);

                // jpeg 出力用ディレクトリ準備
                File dir = new File(TEMP_DIR);
                if (!dir.exists()) {
                    if (!dir.mkdirs()) {
                        throw new IOException("File.mkdirs() failed.");
                    }
                } else {
                    // 既にファイルがあったら消す
                    File[] files = dir.listFiles();
                    for (File f : files) {
                        if (!f.delete()) {
                            throw new IOException("File.delete() failed.");
                        }
                    }
                }

                // jpeg 出力
                String file_path = TEMP_DIR + File.separator + file_name;
                baos = new ByteArrayOutputStream();
                resized.compress(CompressFormat.JPEG, OUTPUT_IMAGE_QUALITY, baos);
                baos.flush();
                byte[] w = baos.toByteArray();
                baos.close();

                // ファイルとして保存
                fos = new FileOutputStream(file_path);
                fos.write(w, 0, w.length);
                fos.flush();
                fos.close();

                // intent を投げてアプリケーション選択
                Uri resized_uri = Uri.fromFile(new File(file_path));
                Intent i = new Intent();
                i.setAction(Intent.ACTION_SEND);
                i.setType("image/jpeg");
                i.putExtra(Intent.EXTRA_STREAM, resized_uri);
                startActivity(i);
            } catch (FileNotFoundException e) {
                // InputStream 生成に失敗
                debug(e.getMessage());
            } catch (IOException e) {
                // io error
                debug(e.getMessage());
            } catch (ActivityNotFoundException e) {
                // 送信先 activity が見つからない
                debug(e.getMessage());
            } catch (Exception e) {
                debug(e.getMessage());
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                    if (fos != null) {
                        fos.close();
                    }
                    if (baos != null) {
                        baos.close();
                    }
                } catch (IOException e2) {
                    debug(e2.getMessage());
                }
            }
        }
        // intent 先アプリから戻ってきたら終了
        setResult(RESULT_OK);
        finish();
    }

    public void debug(String str) {
        Log.d("ImageReducer", str);
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }
}