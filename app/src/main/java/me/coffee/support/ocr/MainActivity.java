package me.coffee.support.ocr;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import me.xuan.bdocr.FileUtil;
import me.xuan.bdocr.sdk.OCR;
import me.xuan.bdocr.sdk.OnResultListener;
import me.xuan.bdocr.sdk.exception.OCRError;
import me.xuan.bdocr.sdk.model.AccessToken;
import me.xuan.bdocr.ui.camera.CameraActivity;

public class MainActivity extends AppCompatActivity {

    private TextView resultTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultTv = findViewById(R.id.tv);

        findViewById(R.id.bank_btn).setOnClickListener(v -> {
            startBdBankCardOcr(100);
        });
        findViewById(R.id.idcard_btn).setOnClickListener(v -> {
            startBdIdCardOcr(false, 200);
        });
        findViewById(R.id.idcard_back_btn).setOnClickListener(v -> {
            startBdIdCardOcr(true, 201);
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
                CameraActivity.start(MainActivity.this,
                        CameraActivity.CONTENT_TYPE_BANK_CARD,
                        FileUtil.getSaveBankCardFile(getApplication()).getAbsolutePath(),
                        false,
                        true,
                        requestCode);
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
                if (isBackSide) {
                    CameraActivity.start(MainActivity.this,
                            CameraActivity.CONTENT_TYPE_ID_CARD_BACK,
                            FileUtil.getSaveIdCardFrontFile(getApplicationContext()).getAbsolutePath(),
                            false,
                            false,
                            requestCode);
                } else {
                    CameraActivity.start(MainActivity.this,
                            null,
                            CameraActivity.CONTENT_TYPE_ID_CARD_FRONT,
                            FileUtil.getSaveIdCardFrontFile(getApplicationContext()).getAbsolutePath(),
                            false,
                            true,
                            requestCode, (activity, type, path) -> {
                                ArrayList<String> list = new ArrayList<>();
                                list.add("张三");
                                list.add("男");
                                list.add("汉");
                                activity.setRecResult(null, list);
                            });
                }
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
        if (resultCode != Activity.RESULT_OK || data == null) return;
        final boolean recognition = data.getBooleanExtra(CameraActivity.KEY_AUTO_RECOGNITION, false);
        final String path = data.getStringExtra(CameraActivity.KEY_OUTPUT_FILE_PATH);

        if (path != null) {
            Bitmap bm = BitmapFactory.decodeFile(path);
            ImageView iv = findViewById(R.id.iv);
            iv.setImageBitmap(bm);
        }

        if (recognition) {
            List<String> listResult = data.getStringArrayListExtra(CameraActivity.KEY_REC_RESULT_ES);
            if (listResult == null || listResult.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (String item : listResult) {
                sb.append(item).append(";");
            }
            resultTv.setText(sb.toString());
        } else {
            resultTv.setText(null);
        }
    }
}