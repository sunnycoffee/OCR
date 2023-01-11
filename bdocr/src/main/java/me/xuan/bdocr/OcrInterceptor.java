package me.xuan.bdocr;

import java.util.ArrayList;

import me.xuan.bdocr.ui.camera.CameraActivity;

/**
 * ocr解析拦截器
 *
 * @author kongfei
 */
public interface OcrInterceptor {

    /**
     * 拦截处理方法,完成后通过handler调用
     * <code>{@link CameraActivity#setRecResult(String, ArrayList)}</code>.
     *
     * @param handler
     * @param type
     * @param path
     */
    void onRecognition(CameraActivity handler, String type, String path);
}
