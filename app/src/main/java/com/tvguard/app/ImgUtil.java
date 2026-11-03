package com.tvguard.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImgUtil {
    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return o.toByteArray();
    }

    static Bitmap decode(byte[] data, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= max || o.outHeight / (s * 2) >= max) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        if (b == null) return null;
        int rot = 0;
        try {
            ExifInterface ex = new ExifInterface(new ByteArrayInputStream(data));
            int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            if (ori == 6) rot = 90;
            else if (ori == 3) rot = 180;
            else if (ori == 8) rot = 270;
        } catch (Exception e) { }
        if (rot != 0) {
            Matrix m = new Matrix();
            m.postRotate(rot);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        }
        return scale(b, max);
    }

    static Bitmap scale(Bitmap b, int max) {
        int w = b.getWidth(), h = b.getHeight();
        float f = Math.min(1f, (float) max / Math.max(w, h));
        if (f >= 1f) return b;
        return Bitmap.createScaledBitmap(b, Math.round(w * f), Math.round(h * f), true);
    }

    static String toB64(Bitmap b, int max, int quality) {
        Bitmap s = scale(b, max);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        s.compress(Bitmap.CompressFormat.JPEG, quality, o);
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    static Bitmap fromB64(String s) {
        byte[] d = Base64.decode(s, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(d, 0, d.length);
    }
}
