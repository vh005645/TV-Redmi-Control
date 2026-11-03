# TV Guard - kiểm soát trẻ em trên Redmi TV (Android 11+)

Một APK dùng cho cả 2 thiết bị: mở lần đầu sẽ hỏi "TV" hay "điện thoại phụ huynh".
Giao tiếp qua Firebase Realtime Database (REST, không cần Google Play Services).

## 1. Tạo Firebase (1 lần)
1. https://console.firebase.google.com -> tạo project -> Build -> Realtime Database -> Create (chọn Singapore, "locked mode").
2. Tab Rules, dán rồi Publish:

    {
      "rules": {
        ".read": false,
        ".write": false,
        "tv": { "$code": { ".read": true, ".write": true } }
      }
    }

3. Copy URL database (dạng https://xxxx-default-rtdb.<vùng>.firebasedatabase.app).

## 2. Sửa URL rồi build
- Sửa `app/src/main/res/values/strings.xml`, dòng `db_url` (không có dấu / ở cuối).
- Đẩy cả thư mục lên GitHub -> tab Actions -> "Build APK" -> tải artifact `TVGuard-apk`.

## 3. Cài đặt
- TV: cài APK (USB / adb install), mở app, chọn "Đây là TV". TV hiện MÃ 10 ký tự.
  Bật dịch vụ Trợ năng "TV Guard" (Settings > Accessibility). Nếu TV không có menu này, dùng ADB:
    adb shell settings put secure enabled_accessibility_services com.tvguard.app/com.tvguard.app.TvService
    adb shell settings put secure accessibility_enabled 1
- Điện thoại: cài APK, chọn "Điện thoại phụ huynh", nhập mã TV, bấm "Lưu mã".

## Ghi chú
- Mã TV là "mật khẩu": ai biết mã + URL database thì điều khiển được TV. Giữ kín.
- Chụp màn hình dùng AccessibilityService.takeScreenshot (Android 11+), không hiện hộp thoại xin quyền.
  Nội dung có DRM (Netflix...) có thể ra ảnh đen.
- "Tắt màn hình" dùng GLOBAL_ACTION_LOCK_SCREEN; nhiều TV không hỗ trợ -> vẫn có màn đen + tắt tiếng.
  Nếu TV vào standby thật thì sẽ không nhận lệnh cho tới khi bật lại.
- Lệnh được TV hỏi mỗi 2 giây nên có độ trễ vài giây.
- Hẹn giờ lưu trên TV, vẫn đúng sau khi TV khởi động lại.
