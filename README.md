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

## 2. Build
- Đẩy cả thư mục lên GitHub -> tab Actions -> "Build APK" -> tải artifact `TVGuard-apk`.
- URL Firebase có 2 cách đặt (chọn 1):
  - Cách A (khuyên dùng, không cần build lại): nhập URL ngay trong app (xem bước 3).
  - Cách B: sửa `db_url` trong `app/src/main/res/values/strings.xml` rồi build lại.

## 3. Cài đặt
- Điện thoại: cài APK, chọn "Điện thoại phụ huynh", dán URL Firebase vào ô trên cùng,
  nhập mã TV, bấm "Lưu URL & mã".
- TV: cài APK (`adb install TVGuard.apk`), mở app, chọn "Đây là TV". TV hiện MÃ 10 ký tự.
  Nếu chưa đặt URL ở strings.xml, nhập URL vào ô trên TV rồi bấm "Lưu URL"
  (hoặc đặt từ máy tính cho nhanh):

      adb shell am start -n com.tvguard.app/.MainActivity --es db_url "https://xxxx-default-rtdb.asia-southeast1.firebasedatabase.app"

- Bật dịch vụ Trợ năng trên TV (chọn 1 trong 2):
  1. Cấp quyền 1 lần bằng ADB, app sẽ tự bật dịch vụ mỗi khi mở (hoặc bấm nút "Mở cài đặt Trợ năng"):

         adb shell pm grant com.tvguard.app android.permission.WRITE_SECURE_SETTINGS

  2. Hoặc bật thẳng:

         adb shell settings put secure enabled_accessibility_services com.tvguard.app/com.tvguard.app.TvService
         adb shell settings put secure accessibility_enabled 1

  Kết nối ADB tới TV: bật Tùy chọn nhà phát triển (Cài đặt > Giới thiệu > bấm nhiều lần vào Số bản dựng),
  bật Gỡ lỗi USB/Gỡ lỗi mạng, rồi `adb connect IP-của-TV:5555`.

## Ghi chú
- Mã TV là "mật khẩu": ai biết mã + URL database thì điều khiển được TV. Giữ kín.
- Chụp màn hình dùng AccessibilityService.takeScreenshot (Android 11+), không hiện hộp thoại xin quyền.
  Nội dung có DRM (Netflix...) có thể ra ảnh đen.
- "Tắt màn hình" dùng GLOBAL_ACTION_LOCK_SCREEN; nhiều TV không hỗ trợ -> vẫn có màn đen + tắt tiếng.
  Nếu TV vào standby thật thì sẽ không nhận lệnh cho tới khi bật lại.
- Lệnh được TV hỏi mỗi 2 giây nên có độ trễ vài giây.
- Hẹn giờ lưu trên TV, vẫn đúng sau khi TV khởi động lại.
