# TV Guard v1.1 - kiểm soát trẻ em trên Redmi TV (Android 11+)

Một APK dùng cho cả 2 thiết bị: mở lần đầu sẽ hỏi "TV" hay "điện thoại phụ huynh".
Giao tiếp qua Firebase Realtime Database (REST).

URL mặc định đã được gán sẵn:
https://tv-redmi-control-default-rtdb.asia-southeast1.firebasedatabase.app
Chỉ khi cần đổi mới bấm nút "Thay đổi URL" (để trống rồi Lưu = quay về URL mặc định).

## Firebase Rules (tab Rules, xóa hết code cũ rồi dán, bấm Publish)

    {
      "rules": {
        ".read": false,
        ".write": false,
        "tv": { "$code": { ".read": true, ".write": true } }
      }
    }

## Cài đặt trên TV (chạy trên máy tính, 1 lần)

    adb connect IP-CỦA-TV:5555
    adb install -r TVGuard.apk
    adb shell pm grant com.tvguard.app android.permission.WRITE_SECURE_SETTINGS
    adb shell dumpsys deviceidle whitelist +com.tvguard.app
    adb shell cmd appops set com.tvguard.app RUN_IN_BACKGROUND allow

Mở app trên TV, chọn "Đây là TV". App tự bật dịch vụ Trợ năng (nhờ lệnh pm grant).
Nếu chưa tự bật, bấm "Mở cài đặt Trợ năng", hoặc chạy:

    adb shell settings put secure enabled_accessibility_services com.tvguard.app/com.tvguard.app.TvService
    adb shell settings put secure accessibility_enabled 1

Sau khi cài đè bản mới bằng `adb install -r`, nếu phím điều khiển vẫn chưa bị chặn khi khóa,
hãy tắt rồi bật lại dịch vụ (hoặc khởi động lại TV).

## Chạy ẩn và chống thoát

- App tự bật lại dịch vụ khi TV khởi động / khi app được cập nhật (BootReceiver).
- App trên TV không hiện trong danh sách đa nhiệm (Recent apps), trẻ không vuốt xóa được.
- Bấm "Đóng app (dịch vụ vẫn chạy nền)" khi cài xong. Dịch vụ chạy nền, không cần mở app.
- Nút "Ẩn biểu tượng app khỏi màn hình chính" (hoặc "Ẩn icon app TV" trên điện thoại)
  làm icon biến mất khỏi TV. Mở lại: `adb shell am start -n com.tvguard.app/.MainActivity`
  hoặc bấm "Hiện icon app TV" trên điện thoại.
- Trẻ bị chặn mở Cài đặt / gỡ cài đặt (tự quay về màn hình chính), nên không tắt được dịch vụ
  hay Force stop. Khi cần vào Cài đặt: điện thoại bấm "Mở Cài đặt TV 10 phút", hoặc qua ADB:

      adb shell am start -n com.tvguard.app/.MainActivity --ei allow_min 10

## Khóa TV

Khi đang KHÓA (hết giờ / Khóa ngay): màn hình đen hoặc khung ảnh, tắt tiếng, và mọi nút của
điều khiển bị vô hiệu hóa, chỉ còn nút Nguồn. Hẹn giờ chạy ngay trên TV nên rút dây mạng vẫn bị khóa.

Cứu hộ nếu TV bị khóa mà mất mạng (chạy ADB):

    adb shell am start -n com.tvguard.app/.MainActivity --ez unlock true

## Điện thoại phụ huynh

Cài APK, chọn "Điện thoại phụ huynh", nhập mã 10 ký tự hiện trên TV, bấm "Lưu mã".

## Ghi chú

- Mã TV là "mật khẩu": ai biết mã + URL database thì điều khiển được TV. Giữ kín.
- Android không cho app thường tự chống gỡ cài đặt tuyệt đối (cần Device Owner). Các biện pháp trên
  chặn trẻ thao tác thông thường qua điều khiển TV, không chống được người rành kỹ thuật dùng ADB.
- Chụp màn hình dùng AccessibilityService.takeScreenshot (Android 11+). Nội dung DRM có thể ra ảnh đen.
- "Tắt màn hình" dùng GLOBAL_ACTION_LOCK_SCREEN; nhiều TV không hỗ trợ -> vẫn có màn đen + tắt tiếng.
- Lệnh được TV hỏi mỗi 2 giây nên có độ trễ vài giây.
