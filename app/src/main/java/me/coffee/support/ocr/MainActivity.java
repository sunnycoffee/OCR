package me.coffee.support.ocr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import me.xuan.bdocr.FileUtil;
import me.xuan.bdocr.sdk.OCR;
import me.xuan.bdocr.sdk.OnResultListener;
import me.xuan.bdocr.sdk.exception.OCRError;
import me.xuan.bdocr.sdk.model.AccessToken;
import me.xuan.bdocr.ui.camera.CameraActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.bank_btn).setOnClickListener(v -> {
            startBdBankCardOcr(100);
        });
        findViewById(R.id.idcard_btn).setOnClickListener(v -> {
            startBdIdCardOcr(false, 200);
        });
    }

    public void startBdBankCardOcr(int requestCode) {
        //初始化OCR
        initAccessTokenWithAkSk(new OnResultListener<AccessToken>() {
            @Override
            public void onResult(AccessToken result) {
                String token = result.getAccessToken();
                Log.i("ocr", "百度OCR初始化成功，access_token: " + token);
                //开始启动OCR
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH, FileUtil.getSaveBankCardFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_BANK_CARD);
                intent.putExtra(CameraActivity.KEY_AUTO_RECOGNITION, true);
                startActivityForResult(intent, requestCode);
            }

            @Override
            public void onError(OCRError error) {
                error.printStackTrace();
                Toast.makeText(MainActivity.this, "获取token失败" + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public void startBdIdCardOcr(boolean isBackSide, int requestCode) {
        //初始化OCR
        initAccessTokenWithAkSk(new OnResultListener<AccessToken>() {
            @Override
            public void onResult(AccessToken result) {
                String token = result.getAccessToken();
                //开始启动OCR
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                if (isBackSide) {
                    intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH, FileUtil.getSaveIdCardFrontFile(getApplicationContext()).getAbsolutePath());
                    intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_BACK);
                } else {
                    intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH, FileUtil.getSaveIdCardBackFile(getApplicationContext()).getAbsolutePath());
                    intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_FRONT);
                }
                intent.putExtra(CameraActivity.KEY_AUTO_RECOGNITION, true);
                startActivityForResult(intent, requestCode);
            }

            @Override
            public void onError(OCRError error) {
                error.printStackTrace();
            }
        });
    }


    /**
     * 用明文ak，sk初始化
     */
    private void initAccessTokenWithAkSk(OnResultListener<AccessToken> listener) {
        OCR.getInstance(this).setAutoCacheToken(true);
        OCR.getInstance(this).initAccessTokenWithAkSk(listener, getApplicationContext(),
                BuildConfig.DB_AK, BuildConfig.DB_SK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        List<String> listResult = data.getStringArrayListExtra("listResult");

        StringBuffer sb = new StringBuffer();
        for (String item : listResult) {
          sb.append(item).append(";");
        }
        TextView tv = findViewById(R.id.tv);
        tv.setText(sb.toString());

        String path = listResult.get(6);
        Bitmap bm = BitmapFactory.decodeFile(path);
        ImageView iv = findViewById(R.id.iv);
        iv.setImageBitmap(bm);
    }
}