package net.yusukezzz.imagereducer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
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
import android.widget.LinearLayout;
import android.widget.Toast;

public class ImageReducer extends Activity {
    private final static String TEMP_DIR             = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "ImageReducer";
    private final static String SENDTO               = TEMP_DIR + File.separator + "sendto.txt";
    // 長辺
    private final static int    LONG_SIDE            = 640;
    // 画質
    private final static int    OUTPUT_IMAGE_QUALITY = 80;
    private List<AppInfo>       listData;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        setContentView(layout);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) == false) {
            File f = new File(SENDTO);
            if (f.exists()) {
                if (f.delete()) {
                    debug("自動SEND対象を削除しました。再実行で選択し直せます。");
                    finish();
                }
            } else {
                PackageManager pm = this.getPackageManager();
                Intent i = new Intent(Intent.ACTION_SEND, null);
                i.setType("image/jpeg");
                List<ResolveInfo> apps = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
                // 自分は省く
                for (int n = 0; n < apps.size(); n++) {
                    if (apps.get(n).activityInfo.packageName.equals(this.getPackageName())) {
                        apps.remove(n);
                    }
                }
                Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
                // リストの作成
                listData = new ArrayList<AppInfo>();
                for (ResolveInfo rInfo : apps) {
                    // 表示する各アプリを設定
                    AppInfo data = new AppInfo();
                    Context c = null;
                    try {
                        c = this.createPackageContext(rInfo.activityInfo.packageName, Context.CONTEXT_RESTRICTED);
                    } catch (NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    Resources res = c.getResources();
                    data.setText(rInfo.loadLabel(pm).toString());
                    data.setPackageName(rInfo.activityInfo.packageName);
                    data.setClassName(rInfo.activityInfo.name);
                    listData.add(data);
                }
                int items = listData.size();
                String names[] = new String[items];
                for (int j = 0; j < items; j++) {
                    names[j] = listData.get(j).getText();
                }
                // ListDialog から選択
                new AlertDialog.Builder(this).setTitle("choose send target").setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        writeSendto(listData.get(which).getPackageName(), listData.get(which).getClassName());
                        debug("set: " + listData.get(which).getClassName());
                        finish();
                    }
                }).show();
            }
        } else {
            ContentResolver cr = getContentResolver();
            InputStream is = null;
            FileOutputStream fos = null;
            ByteArrayOutputStream baos = null;
            // 画像だったら縮小
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            try {
                // 元画像の向きを取得
                Cursor query = MediaStore.Images.Media.query(cr, uri, new String[]
                    { MediaStore.Images.ImageColumns.DISPLAY_NAME, MediaStore.Images.ImageColumns.ORIENTATION }, null, null);
                query.moveToFirst();
                String file_name = query.getString(0);
                int orientation = query.getInt(1);

                // オリジナルを bitmap で取得
                is = cr.openInputStream(uri);
                Bitmap bm_org = BitmapFactory.decodeStream(is);
                is.close();
                // リサイズ
                Bitmap resized = this.resizeImage(bm_org, LONG_SIDE, orientation);

                // jpeg 出力用ディレクトリ準備
                File dir = new File(TEMP_DIR);
                if (!dir.exists()) {
                    if (!dir.mkdirs()) { throw new IOException("File.mkdirs() failed."); }
                } else {
                    // 既にファイルがあったら消す
                    File[] files = dir.listFiles();
                    for (File f : files) {
                        if (f.getName().indexOf("sendto.txt") == -1) {
                            if (!f.delete()) { throw new IOException("File.delete() failed."); }
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

                // 自動send対象の読み出し
                File f = new File(SENDTO);
                String packageName = null;
                String className = null;
                if (f.exists()) {
                    FileReader fr = new FileReader(SENDTO);
                    BufferedReader br = new BufferedReader(fr);
                    packageName = br.readLine();
                    className = br.readLine();
                }

                // intent を投げてアプリケーション選択
                Uri resized_uri = Uri.fromFile(new File(file_path));
                Intent i = new Intent();
                i.setAction(Intent.ACTION_SEND);
                // 自動send先が指定されていればセット
                if (packageName != null && className != null) {
                    i.setClassName(packageName, className);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                i.setType("image/jpg");
                i.putExtra(Intent.EXTRA_STREAM, resized_uri);
                startActivity(i);
                // intent 先アプリから戻ってきたら終了
                setResult(RESULT_OK);
                finish();
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
    }

    private Bitmap resizeImage(Bitmap source, int long_side, int orientation) {
        // width と height の長さを取得
        int width = source.getWidth();
        int height = source.getHeight();

        // 長辺を 640px に縮小したときの倍率を取得
        float scale = (width >= height) ? ((float) LONG_SIDE) / width : ((float) LONG_SIDE) / height;
        // 等倍縮小＆正しい向きになるように回転
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postRotate(orientation);
        // リサイズ＆回転後の画像生成
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true);
    }

    private void writeSendto(String packageName, String className) {
        File d = new File(TEMP_DIR);
        if (d.exists() == false) {
            d.mkdirs();
        }
        File f = new File(SENDTO);
        try {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(f)));
            pw.println(packageName);
            pw.println(className);
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void debug(String str) {
        Log.d("ImageReducer", str);
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }

    public class AppInfo {
        private String text;
        private String className;
        private String packageName;

        public void setText(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getClassName() {
            return this.className;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getPackageName() {
            return this.packageName;
        }
    }
}