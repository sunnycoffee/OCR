package me.xuan.bdocr.sdk.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.ExifInterface;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Author: xuan
 * Created on 2019/10/23 14:56.
 * <p>
 * Describe:
 */
public class ImageUtil {
    public ImageUtil() {
    }

    public static void resize(String inputPath, String outputPath, int dstWidth, int dstHeight) {
        resize(inputPath, outputPath, dstWidth, dstHeight, 80);
    }

    public static void resize(String inputPath, String outputPath, int dstWidth, int dstHeight, int quality) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(inputPath, options);
            int inWidth = options.outWidth;
            int inHeight = options.outHeight;
            Matrix m = new Matrix();
            ExifInterface exif = new ExifInterface(inputPath);
            int rotation = exif.getAttributeInt("Orientation", 1);
            if (rotation != 0) {
                m.preRotate((float) ExifUtil.exifToDegrees(rotation));
            }

            int maxPreviewImageSize = Math.max(dstWidth, dstHeight);
            int size = Math.min(options.outWidth, options.outHeight);
            size = Math.min(size, maxPreviewImageSize);
            options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(options, size, size);
            options.inScaled = true;
            options.inDensity = options.outWidth;
            options.inTargetDensity = size * options.inSampleSize;
            Bitmap roughBitmap = BitmapFactory.decodeFile(inputPath, options);
            FileOutputStream out = new FileOutputStream(outputPath);

            try {
                roughBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            } catch (Exception var25) {
                var25.printStackTrace();
            } finally {
                try {
                    out.close();
                } catch (Exception var24) {
                    var24.printStackTrace();
                }

            }
        } catch (IOException var27) {
            var27.printStackTrace();
        }

    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;

            for (int halfWidth = width / 2; halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth; inSampleSize *= 2) {
                ;
            }
        }

        return inSampleSize;
    }

    public static Bitmap compressImage(Context context, String path) {
        if (path == null) {
            return null;
        }

        Bitmap result = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Bitmap original = BitmapFactory.decodeFile(path, options);

        try {
            ExifInterface exif = new ExifInterface(path);
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            int rotationInDegrees = me.xuan.bdocr.ui.util.ImageUtil.exifToDegrees(rotation);
            if (rotation != 0f) {
                matrix.preRotate(rotationInDegrees);
            }

            // 图片太大会导致内存泄露，所以在显示前对图片进行裁剪。
            int maxPreviewImageSize = 2560;

            int min = Math.min(options.outWidth, options.outHeight);
            min = Math.min(min, maxPreviewImageSize);

            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Point screenSize = new Point();
            windowManager.getDefaultDisplay().getSize(screenSize);
            min = Math.min(min, screenSize.x * 2 / 3);

            options.inSampleSize = me.xuan.bdocr.ui.util.ImageUtil.calculateInSampleSize(options, min, min);
            options.inScaled = true;
            options.inDensity = options.outWidth;
            options.inTargetDensity = min * options.inSampleSize;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            options.inJustDecodeBounds = false;
            result = BitmapFactory.decodeFile(path, options);
        } catch (IOException e) {
            e.printStackTrace();
            result = original;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void saveToFile(String base64, String outFilePath) {
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        saveBitmap(bm, Bitmap.CompressFormat.JPEG, outFilePath);
        bm.recycle();
    }

    private static void saveBitmap(Bitmap bm, Bitmap.CompressFormat format, String path) {
        FileOutputStream out = null;
        try {
            File file = new File(path);
            out = new FileOutputStream(file);
            bm.compress(format, 100, out);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static void compressImageToFile(Bitmap image, File file) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 100;
        image.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        while (baos.toByteArray().length / 1024 > 2048) {
            quality -= 10;
            baos.reset();
            image.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        }
        Log.d("image", "原图压缩比例:" + quality + "%");
        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        writeFileFromIS(file, is);
    }

    private static boolean writeFileFromIS(final File file, final InputStream is) {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file), 40960);
            byte[] data = new byte[40960];
            for (int len; (len = is.read(data)) != -1; ) {
                os.write(data, 0, len);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
