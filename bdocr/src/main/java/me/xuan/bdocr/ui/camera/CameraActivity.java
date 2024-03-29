/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package me.xuan.bdocr.ui.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import me.xuan.bdocr.OcrInterceptor;
import me.xuan.bdocr.R;
import me.xuan.bdocr.ShowLoadingInterface;
import me.xuan.bdocr.sdk.OCR;
import me.xuan.bdocr.sdk.OnResultListener;
import me.xuan.bdocr.sdk.exception.OCRError;
import me.xuan.bdocr.sdk.model.BankCardParams;
import me.xuan.bdocr.sdk.model.BankCardResult;
import me.xuan.bdocr.sdk.model.IDCardParams;
import me.xuan.bdocr.sdk.model.IDCardResult;
import me.xuan.bdocr.sdk.utils.ImageUtil;
import me.xuan.bdocr.ui.crop.CropView;
import me.xuan.bdocr.ui.crop.FrameOverlayView;

public class CameraActivity extends FragmentActivity implements ShowLoadingInterface {

    public static final String KEY_OUTPUT_FILE_PATH = "outputFilePath";
    public static final String KEY_CONTENT_TYPE = "contentType";
    public static final String KEY_NATIVE_TOKEN = "nativeToken";
    public static final String KEY_NATIVE_ENABLE = "nativeEnable";
    public static final String KEY_NATIVE_MANUAL = "nativeEnableManual";
    public static final String KEY_AUTO_RECOGNITION = "autorecogniton";
    public static final String KEY_REC_RESULT = "recresult";
    public static final String KEY_REC_RESULT_ES = "listResult";
    public static final String KEY_AUTO_CROP = "autoCrop";
    public static final String KEY_SHOW_EXAMPLE = "showExample";

    public static final String CONTENT_TYPE_GENERAL = "general";
    public static final String CONTENT_TYPE_ID_CARD_FRONT = "IDCardFront";
    public static final String CONTENT_TYPE_ID_CARD_BACK = "IDCardBack";
    public static final String CONTENT_TYPE_BANK_CARD = "bankCard";

    private static final int REQUEST_CODE_PICK_IMAGE = 100;
    private static final int PERMISSIONS_REQUEST_CAMERA = 800;
    private static final int PERMISSIONS_EXTERNAL_STORAGE = 801;

    private File outputFile;
    private String contentType;
    private Handler handler = new Handler();

    private boolean isNativeEnable;
    private boolean isNativeManual;
    private boolean isAutoRecg;
    private boolean isAutoCrop;
    private boolean isShowExample;

    private OCRCameraLayout takePictureContainer;
    private OCRCameraLayout cropContainer;
    private OCRCameraLayout confirmResultContainer;
    private ImageView lightButton;
    private CameraView cameraView;
    private ImageView displayImageView;
    private CropView cropView;
    private FrameOverlayView overlayView;
    private MaskView cropMaskView;
    private ImageView takePhotoBtn;
    private PermissionCallback permissionCallback = new PermissionCallback() {
        @Override
        public boolean onRequestPermission() {
            ActivityCompat.requestPermissions(CameraActivity.this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSIONS_REQUEST_CAMERA);
            return false;
        }
    };
    protected static OcrInterceptor mOcrInterceptor;

    public static void start(Activity context, String contentType, String outFilePath, boolean autoRecognition, boolean showExample, int requestCode) {
        start(context, null, contentType, outFilePath, autoRecognition, showExample, requestCode, null);
    }

    public static void start(Activity context, Class<? extends CameraActivity> target, String contentType, String outFilePath, boolean autoRecognition, boolean showExample, int requestCode) {
        start(context, target, contentType, outFilePath, autoRecognition, showExample, requestCode, null);
    }

    public static void start(Activity context, Class<? extends CameraActivity> target, String contentType, String outFilePath, boolean autoRecognition, boolean showExample, int requestCode, OcrInterceptor ocrInterceptor) {
        mOcrInterceptor = ocrInterceptor;
        if (target == null) target = CameraActivity.class;
        Intent starter = new Intent(context, target);
        starter.putExtra(KEY_CONTENT_TYPE, contentType);
        starter.putExtra(KEY_OUTPUT_FILE_PATH, outFilePath);
        starter.putExtra(KEY_AUTO_RECOGNITION, autoRecognition);
        starter.putExtra(KEY_SHOW_EXAMPLE, showExample);
        context.startActivityForResult(starter, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.bd_ocr_activity_camera);

        takePictureContainer = (OCRCameraLayout) findViewById(R.id.take_picture_container);
        confirmResultContainer = (OCRCameraLayout) findViewById(R.id.confirm_result_container);

        cameraView = (CameraView) findViewById(R.id.camera_view);
        cameraView.getCameraControl().setPermissionCallback(permissionCallback);
        lightButton = (ImageView) findViewById(R.id.light_button);
        lightButton.setOnClickListener(lightButtonOnClickListener);
        takePhotoBtn = (ImageView) findViewById(R.id.take_photo_button);
        findViewById(R.id.album_button).setOnClickListener(albumButtonOnClickListener);
        takePhotoBtn.setOnClickListener(takeButtonOnClickListener);

        // confirm result;
        displayImageView = (ImageView) findViewById(R.id.display_image_view);
        confirmResultContainer.findViewById(R.id.confirm_button).setOnClickListener(confirmButtonOnClickListener);
        confirmResultContainer.findViewById(R.id.cancel_button).setOnClickListener(confirmCancelButtonOnClickListener);
        findViewById(R.id.rotate_button).setOnClickListener(rotateButtonOnClickListener);

        cropView = (CropView) findViewById(R.id.crop_view);
        cropContainer = (OCRCameraLayout) findViewById(R.id.crop_container);
        overlayView = (FrameOverlayView) findViewById(R.id.overlay_view);
        cropContainer.findViewById(R.id.confirm_button).setOnClickListener(cropConfirmButtonListener);
        cropMaskView = (MaskView) cropContainer.findViewById(R.id.crop_mask_view);
        cropContainer.findViewById(R.id.cancel_button).setOnClickListener(cropCancelButtonListener);

        setOrientation(getResources().getConfiguration());
        initParams();

        cameraView.setAutoPictureCallback(autoTakePictureCallback);

        View idCardExamView = (View) findViewById(R.id.id_card_exam_container);
        View idCardBackExamView = (View) findViewById(R.id.id_card_back_exam_container);
        View bankCardExamView = (View) findViewById(R.id.bank_card_exam_container);
        if (contentType.equals(CONTENT_TYPE_ID_CARD_FRONT) && isShowExample) {
            idCardExamView.setVisibility(View.VISIBLE);
        } else {
            idCardExamView.setVisibility(View.GONE);
        }

        if (contentType.equals(CONTENT_TYPE_ID_CARD_BACK) && isShowExample) {
            idCardBackExamView.setVisibility(View.VISIBLE);
        } else {
            idCardBackExamView.setVisibility(View.GONE);
        }

        if (contentType.equals(CONTENT_TYPE_BANK_CARD) && isShowExample) {
            bankCardExamView.setVisibility(View.VISIBLE);
        } else {
            bankCardExamView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    private void initParams() {
        String outputPath = getIntent().getStringExtra(KEY_OUTPUT_FILE_PATH);
        final String token = getIntent().getStringExtra(KEY_NATIVE_TOKEN);
        isNativeEnable = getIntent().getBooleanExtra(KEY_NATIVE_ENABLE, true);
        isNativeManual = getIntent().getBooleanExtra(KEY_NATIVE_MANUAL, false);

        if (token == null && !isNativeManual) {
            isNativeEnable = false;
        }

        if (outputPath != null) {
            outputFile = new File(outputPath);
        }

        contentType = getIntent().getStringExtra(KEY_CONTENT_TYPE);
        if (contentType == null) {
            contentType = CONTENT_TYPE_GENERAL;
        }

        isAutoRecg = getIntent().getBooleanExtra(KEY_AUTO_RECOGNITION, false);
        isAutoCrop = getIntent().getBooleanExtra(KEY_AUTO_CROP, true);
        isShowExample = getIntent().getBooleanExtra(KEY_SHOW_EXAMPLE, true);

        if (mOcrInterceptor == null) {
            int maskType;
            switch (contentType) {
                case CONTENT_TYPE_ID_CARD_FRONT:
                    maskType = MaskView.MASK_TYPE_ID_CARD_FRONT;
                    overlayView.setVisibility(View.INVISIBLE);
                    if (isNativeEnable) {
                        takePhotoBtn.setVisibility(View.INVISIBLE);
                    }
                    break;
                case CONTENT_TYPE_ID_CARD_BACK:
                    maskType = MaskView.MASK_TYPE_ID_CARD_BACK;
                    overlayView.setVisibility(View.INVISIBLE);
                    if (isNativeEnable) {
                        takePhotoBtn.setVisibility(View.INVISIBLE);
                    }
                    break;
                case CONTENT_TYPE_BANK_CARD:
                    maskType = MaskView.MASK_TYPE_BANK_CARD;
                    overlayView.setVisibility(View.INVISIBLE);
                    break;
                case CONTENT_TYPE_GENERAL:
                default:
                    maskType = MaskView.MASK_TYPE_NONE;
                    cropMaskView.setVisibility(View.INVISIBLE);
                    break;
            }

            cameraView.setMaskType(maskType, this);
            cropMaskView.setMaskType(maskType);
        } else {
            cropMaskView.setVisibility(View.INVISIBLE);
            cameraView.setMaskType(MaskView.MASK_TYPE_NONE, this);
            cropMaskView.setMaskType(MaskView.MASK_TYPE_NONE);
        }
    }

    private void showTakePicture() {
        cameraView.getCameraControl().resume();
        updateFlashMode();
        takePictureContainer.setVisibility(View.VISIBLE);
        confirmResultContainer.setVisibility(View.INVISIBLE);
        cropContainer.setVisibility(View.INVISIBLE);
    }

    private void showCrop() {
        cameraView.getCameraControl().pause();
        updateFlashMode();
        takePictureContainer.setVisibility(View.INVISIBLE);
        confirmResultContainer.setVisibility(View.INVISIBLE);
        cropContainer.setVisibility(View.VISIBLE);
        //剪裁图片滑动限制
        cropView.setRestrictBound(cameraView.getMaskRect());
    }

    private void showResultConfirm() {
        cameraView.getCameraControl().pause();
        updateFlashMode();
        takePictureContainer.setVisibility(View.INVISIBLE);
        confirmResultContainer.setVisibility(View.VISIBLE);
        cropContainer.setVisibility(View.INVISIBLE);
    }

    // take photo;
    private void updateFlashMode() {
        int flashMode = cameraView.getCameraControl().getFlashMode();
        if (flashMode == ICameraControl.FLASH_MODE_TORCH) {
            lightButton.setImageResource(R.drawable.bd_ocr_light_on);
        } else {
            lightButton.setImageResource(R.drawable.bd_ocr_light_off);
        }
    }

    private View.OnClickListener albumButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ActivityCompat.requestPermissions(CameraActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            PERMISSIONS_EXTERNAL_STORAGE);
                    return;
                }
            }
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
        }
    };

    private View.OnClickListener lightButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (cameraView.getCameraControl().getFlashMode() == ICameraControl.FLASH_MODE_OFF) {
                cameraView.getCameraControl().setFlashMode(ICameraControl.FLASH_MODE_TORCH);
            } else {
                cameraView.getCameraControl().setFlashMode(ICameraControl.FLASH_MODE_OFF);
            }
            updateFlashMode();
        }
    };

    private View.OnClickListener takeButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            cameraView.takePicture(outputFile, takePictureCallback);
        }
    };

    private CameraView.OnTakePictureCallback autoTakePictureCallback = new CameraView.OnTakePictureCallback() {
        @Override
        public void onPictureTaken(final Bitmap bitmap) {
            CameraThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                        bitmap.recycle();
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Intent intent = new Intent();
                    intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, contentType);
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                }
            });
        }
    };

    private CameraView.OnTakePictureCallback takePictureCallback = new CameraView.OnTakePictureCallback() {
        @Override
        public void onPictureTaken(final Bitmap bitmap) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    takePictureContainer.setVisibility(View.INVISIBLE);
                    displayImageView.setImageBitmap(bitmap);
                    showResultConfirm();
                }
            });
        }
    };

    private View.OnClickListener cropCancelButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // 释放cropView中的bitmap;
            cropView.setFilePath(null);
            showTakePicture();
        }
    };

    private View.OnClickListener cropConfirmButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int maskType = cropMaskView.getMaskType();
            Rect rect;
            switch (maskType) {
                case MaskView.MASK_TYPE_BANK_CARD:
                case MaskView.MASK_TYPE_ID_CARD_BACK:
                case MaskView.MASK_TYPE_ID_CARD_FRONT:
                    rect = cropMaskView.getFrameRect();
                    break;
                case MaskView.MASK_TYPE_NONE:
                default:
                    rect = overlayView.getFrameRect();
                    break;
            }
            Bitmap cropped = cropView.crop(rect);
            displayImageView.setImageBitmap(cropped);
            cropAndConfirm();
        }
    };

    private void cropAndConfirm() {
        cameraView.getCameraControl().pause();
        updateFlashMode();
        doConfirmResult();
    }

    private void doConfirmResult() {
        showRecgLoading();
        CameraThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bitmap = ((BitmapDrawable) displayImageView.getDrawable()).getBitmap();
                    ImageUtil.compressImageToFile(bitmap, outputFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //可以在此上传应用并识别
                if (isAutoRecg) {
                    if (CameraActivity.CONTENT_TYPE_ID_CARD_FRONT.equals(contentType)) {
                        //身份证正面
                        recIDCard(IDCardParams.ID_CARD_SIDE_FRONT, outputFile.getAbsolutePath());
                    } else if (CameraActivity.CONTENT_TYPE_ID_CARD_BACK.equals(contentType)) {
                        //身份证反面
                        recIDCard(IDCardParams.ID_CARD_SIDE_BACK, outputFile.getAbsolutePath());
                    } else if (CameraActivity.CONTENT_TYPE_BANK_CARD.equals(contentType)) {
                        //银行卡识别
                        recBankCard(outputFile.getAbsolutePath());
                    }
                } else if (mOcrInterceptor != null) {
                    mOcrInterceptor.onRecognition(CameraActivity.this, contentType, outputFile.getAbsolutePath());
                } else {
                    Intent intent = new Intent();
                    intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, contentType);
                    intent.putExtra(CameraActivity.KEY_AUTO_RECOGNITION, false);
                    intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH, outputFile.getAbsolutePath());
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                }
            }
        });
    }

    private View.OnClickListener confirmButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            doConfirmResult();
        }
    };

    private View.OnClickListener confirmCancelButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            displayImageView.setImageBitmap(null);
            showTakePicture();
        }
    };

    private View.OnClickListener rotateButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            cropView.rotate(90);
        }
    };

    private void recBankCard(String filePath) {
        BankCardParams param = new BankCardParams();
        param.setImageFile(new File(filePath));
        OCR.getInstance(this).recognizeBankCard(param, new OnResultListener<BankCardResult>() {
            @Override
            public void onResult(BankCardResult result) {
                String res = String.format("卡号：%s\n有效期：%s\n类型：%s\n发卡行：%s",
                        result.getBankCardNumber(),
                        result.getValidDate(),
                        result.getBankCardType().name(),
                        result.getBankName());
                Log.i("BANKCARD", res);
                ArrayList<String> listResult = new ArrayList();
                listResult.add(result.getBankCardNumber().replaceAll(" ", ""));
                listResult.add(result.getBankName());
                listResult.add("");
                listResult.add("");
                listResult.add(result.getBankCardType().name());
                listResult.add("");
                listResult.add(outputFile.getAbsolutePath());
                setRecResult(res, listResult);
            }

            @Override
            public void onError(OCRError error) {
                mOcrInterceptor = null;
                hideRecgLoading();
                Log.i("BANKCARD", error.getMessage());
                Toast.makeText(CameraActivity.this, "银行卡识别失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cropView.setFilePath(null);
                        showTakePicture();
                    }
                }, 1000);
            }
        });
    }

    private void recIDCard(String idCardSide, String filePath) {
        IDCardParams param = new IDCardParams();
        param.setImageFile(new File(filePath));
        // 设置身份证正反面
        param.setIdCardSide(idCardSide);
        // 设置方向检测
        param.setDetectDirection(true);
        // 设置图像参数压缩质量0-100, 越大图像质量越好但是请求时间越长。 不设置则默认值为20
        param.setImageQuality(80);
        // 获取剪裁后的图片
        param.setDetectCard(isAutoCrop);
        OCR.getInstance(this).recognizeIDCard(param, new OnResultListener<IDCardResult>() {
            @Override
            public void onResult(IDCardResult result) {
                if (result != null) {
                    Log.i("IDCARD", result.toString());
                    ArrayList<String> listResult = new ArrayList();
                    if ("front".equals(result.getIdCardSide())) {
                        listResult.add(result.getName().getWords());
                        listResult.add(result.getGender().getWords());
                        listResult.add(result.getEthnic().getWords());
                        listResult.add(result.getBirthday().getWords());
                        listResult.add(result.getAddress().getWords());
                        listResult.add(result.getIdNumber().getWords());
                        ImageUtil.saveToFile(result.getCardImage(), outputFile.getAbsolutePath());
                        listResult.add(outputFile.getAbsolutePath());
                    } else {
                        listResult.add(result.getIssueAuthority().getWords());
                        listResult.add(result.getSignDate().getWords() + "-" + result.getExpiryDate().getWords());
                        ImageUtil.saveToFile(result.getCardImage(), outputFile.getAbsolutePath());
                        listResult.add(outputFile.getAbsolutePath());
                    }

                    setRecResult(result.toString(), listResult);
                }
            }

            @Override
            public void onError(OCRError error) {
                mOcrInterceptor = null;
                hideRecgLoading();
                Log.i("IDCARD", error.getMessage());
                Toast.makeText(CameraActivity.this, "身份证识别失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cropView.setFilePath(null);
                        showTakePicture();
                    }
                }, 1000);
            }
        });
    }

    public void setRecResult(String result, ArrayList<String> resultArr) {
        mOcrInterceptor = null;
        hideRecgLoading();
        Intent intent = new Intent();
        intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, contentType);
        intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH, outputFile.getAbsolutePath());
        intent.putExtra(CameraActivity.KEY_REC_RESULT, result);
        intent.putExtra(CameraActivity.KEY_AUTO_RECOGNITION, true);
        intent.putStringArrayListExtra(CameraActivity.KEY_REC_RESULT_ES, resultArr);
        setResult(Activity.RESULT_OK, intent);
        CameraActivity.this.finish();
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(contentURI, null, null, null, null);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (cursor == null) {
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setOrientation(newConfig);
    }

    private void setOrientation(Configuration newConfig) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int orientation;
        int cameraViewOrientation = CameraView.ORIENTATION_PORTRAIT;
        switch (newConfig.orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                cameraViewOrientation = CameraView.ORIENTATION_PORTRAIT;
                orientation = OCRCameraLayout.ORIENTATION_PORTRAIT;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                orientation = OCRCameraLayout.ORIENTATION_HORIZONTAL;
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) {
                    cameraViewOrientation = CameraView.ORIENTATION_HORIZONTAL;
                } else {
                    cameraViewOrientation = CameraView.ORIENTATION_INVERT;
                }
                break;
            default:
                orientation = OCRCameraLayout.ORIENTATION_PORTRAIT;
                cameraView.setOrientation(CameraView.ORIENTATION_PORTRAIT);
                break;
        }
        takePictureContainer.setOrientation(orientation);
        cameraView.setOrientation(cameraViewOrientation);
        cropContainer.setOrientation(orientation);
        confirmResultContainer.setOrientation(orientation);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                //银行卡默认使用剪裁框（拦截处理的情况除外）
                if (CONTENT_TYPE_BANK_CARD.equals(contentType) && mOcrInterceptor == null) {
                    cropView.setFilePath(getRealPathFromURI(uri));
                    showCrop();
                } else {
                    displayImageView.setImageBitmap(ImageUtil.compressImage(this, getRealPathFromURI(uri)));
                    showResultConfirm();
                }
            } else {
                cameraView.getCameraControl().resume();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraView.getCameraControl().refreshPermission();
                } else {
                    Toast.makeText(getApplicationContext(), R.string.camera_permission_required, Toast.LENGTH_LONG)
                            .show();
                }
                break;
            }
            case PERMISSIONS_EXTERNAL_STORAGE:
            default:
                break;
        }
    }

    /**
     * 做一些收尾工作
     */
    private void doClear() {
        CameraThreadPool.cancelAutoFocusTimer();
//        if (isNativeEnable && !isNativeManual) {
//            IDcardQualityProcess.getInstance().releaseModel();
//        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.doClear();
    }

    @Override
    public void showRecgLoading() {
    }

    @Override
    public void hideRecgLoading() {
    }
}
