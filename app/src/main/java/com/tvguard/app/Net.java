package com.tvguard.app;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Net {
    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            InputStream in = c.getInputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static JSONObject getObj(String url) throws Exception {
        String s = get(url).trim();
        if (s.isEmpty() || s.equals("null")) return null;
        return new JSONObject(s);
    }

    static void put(String url, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setRequestMethod("PUT");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] b = json.getBytes("UTF-8");
        c.setFixedLengthStreamingMode(b.length);
        try {
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.close();
            int code = c.getResponseCode();
            if (code != 200 && code != 204) throw new IOException("HTTP " + code);
        } finally {
            c.disconnect();
        }
    }

    // Doi loi ky thuat thanh cau tieng Viet de biet can sua gi
    static String explain(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (e instanceof java.net.UnknownHostException)
            return "không tìm thấy máy chủ (URL Firebase sai hoặc không có Internet)";
        if (e instanceof java.net.SocketTimeoutException)
            return "quá thời gian chờ (mạng chậm hoặc bị chặn)";
        if (m.contains("HTTP 401")) return "Firebase từ chối (401) - kiểm tra lại Rules, nhớ bấm Publish";
        if (m.contains("HTTP 403")) return "Firebase từ chối (403) - kiểm tra Rules";
        if (m.contains("HTTP 404")) return "không thấy database (404) - kiểm tra URL, đã tạo Realtime Database chưa?";
        if (m.contains("HTTP 423")) return "database đang bị khóa/tạm dừng (423)";
        return e.getClass().getSimpleName() + ": " + m;
    }
}
