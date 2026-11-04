# TV Guard v1.4 - kiểm soát trẻ em trên Redmi TV (Android 11+)

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

## Mã TV

Mã TV mặc định được gán cứng trong `app/src/main/res/values/strings.xml` (dòng `tv_code`): **emzty9p9e3**
Mã không đổi khi cài lại hay cập nhật. App điện thoại tự điền sẵn mã này, không cần nhập.
Trên cả TV và điện thoại, mã chỉ hiện dạng chữ nhỏ; muốn đổi thủ công thì bấm **"Thay đổi mã TV"** (giống phần URL),
nhập mã mới (a-z, 0-9, 4-40 ký tự) rồi **"Lưu mã TV"**. Để trống rồi lưu = quay về mã mặc định.
Hai máy phải dùng cùng một mã. Mã này như mật khẩu, hãy để repo GitHub ở chế độ **Private**.

## Khóa ký cố định

File `app/tvguard.keystore` được dùng để ký mọi bản build, nên các bản build sau cài đè được
bằng `adb install -r` mà không phải gỡ app, không mất quyền đã cấp. (Lần đầu chuyển sang bản có
khóa này cần gỡ bản cũ 1 lần: `adb uninstall com.tvguard.app`.)

## Cài đặt trên TV (chạy trên máy tính)

    adb connect IP-CỦA-TV:5555
    adb install -r TVGuard.apk
    adb shell pm grant com.tvguard.app android.permission.WRITE_SECURE_SETTINGS
    adb shell dumpsys deviceidle whitelist +com.tvguard.app
    adb shell cmd appops set com.tvguard.app RUN_IN_BACKGROUND allow
    adb shell appops set com.tvguard.app SYSTEM_ALERT_WINDOW allow

Mở app trên TV, chọn "Đây là TV". App tự bật dịch vụ Trợ năng (nhờ lệnh pm grant).
Nếu chưa tự bật, bấm "Mở cài đặt Trợ năng", hoặc chạy:

    adb shell settings put secure enabled_accessibility_services com.tvguard.app/com.tvguard.app.TvService
    adb shell settings put secure accessibility_enabled 1

Xong bấm "Thu nhỏ app (dịch vụ vẫn chạy nền)". KHÔNG vuốt xóa / Force stop app trên TV.

## Tự chạy khi bật TV

Khi TV khởi động (hoặc sau khi cập nhật app), app tự bật dịch vụ rồi mở ở chế độ thu nhỏ (tự quay về nền sau ~1 giây,
giao diện được ẩn trong lúc đó). Cần chạy lệnh `appops ... SYSTEM_ALERT_WINDOW allow` ở trên (1 lần) để Android cho phép
app tự mở màn hình từ nền. Nếu TV có mục "Tự khởi động / Autostart" trong quản lý ứng dụng thì bật cho TV Guard.

## Tắt nguồn TV khi hết giờ (shutdown)

Khi hết giờ có 3 lựa chọn: Màn hình đen, Khung ảnh, hoặc **Tắt nguồn TV**. App thường không có quyền tắt nguồn, nên
chế độ này dùng **ADB nội bộ**: app tự kết nối vào adb của chính TV (127.0.0.1:5555) và chạy `reboot -p`
(nếu lỗi thì chạy `input keyevent 26` = phím Nguồn). Nếu vẫn không tắt được, app khóa màn hình/đưa TV vào chế độ chờ.
Cần làm 1 lần:

1. Trên TV bật "Gỡ lỗi ADB qua mạng" (cùng thứ bạn đã dùng cho `adb connect IP:5555`).
2. Mở app TV, bấm **"Kiểm tra quyền tắt nguồn (ADB nội bộ)"**. TV hiện hộp thoại "Cho phép gỡ lỗi?" -> chọn
   **"Luôn cho phép"**. Dòng "Tắt nguồn TV (ADB nội bộ)" đổi thành "SẴN SÀNG".

Khi TV đang khóa ở chế độ tắt nguồn, TV chờ ~8 giây (để kịp nhận lệnh "Mở khóa") rồi tự tắt; nếu bật lại mà vẫn còn bị
khóa thì sẽ tắt lại. Muốn xem TV phải bấm "Mở khóa"/"Hủy hẹn giờ" trên điện thoại.

## Hẹn giờ trên điện thoại

- **Đặt giờ**: hiện ngay giờ sẽ khóa/tắt, ví dụ 10:30 đặt 20 phút -> "10:50 sẽ tắt nguồn TV". Trạng thái TV cũng hiển thị
  "Đã hẹn: 10:50 ..." và thời gian còn lại.
- **Cộng thêm**: cộng vào giờ khóa hiện tại.
- **Hủy hẹn giờ**: bỏ hẹn giờ (nếu TV đang khóa vì hết giờ thì mở khóa luôn); trạng thái hiện
  "Không giới hạn thời gian xem TV".

## Chạy ẩn và chống thoát

- App tự bật lại dịch vụ khi TV khởi động / khi app được cập nhật (BootReceiver).
- App trên TV không hiện trong danh sách đa nhiệm (Recent apps), trẻ không vuốt xóa được.
- Bấm "Thu nhỏ app" khi cài xong; phím Back trong app cũng chỉ thu nhỏ. Dịch vụ giữ nền (KeepService) tự bật lại Trợ năng nếu bị tắt.
- Nút "Ẩn biểu tượng app khỏi màn hình chính" (hoặc "Ẩn icon app TV" trên điện thoại)
  làm icon biến mất khỏi TV. Mở lại: `adb shell am start -n com.tvguard.app/.MainActivity`
  hoặc bấm "Hiện icon app TV" trên điện thoại.
- Trẻ bị chặn mở Cài đặt / gỡ cài đặt (tự quay về màn hình chính), nên không tắt được dịch vụ
  hay Force stop. Khi cần vào Cài đặt: điện thoại bấm "Mở Cài đặt TV 10 phút", hoặc qua ADB:

      adb shell am start -n com.tvguard.app/.MainActivity --ei allow_min 10

## Khóa TV

Khi đang KHÓA (hết giờ / Khóa ngay): màn hình đen hoặc khung ảnh, tắt tiếng. Lớp phủ khóa giữ tiêu điểm
bàn phím nên mọi nút (Back, OK, mũi tên...) bị nuốt, không tới app bên dưới; nút Nguồn vẫn dùng được.
Nút Home do hệ thống xử lý nên có thể về màn hình chính phía sau, nhưng màn hình vẫn đen và không điều khiển được. Hẹn giờ chạy ngay trên TV nên rút dây mạng vẫn bị khóa.

Cứu hộ nếu TV bị khóa mà mất mạng (chạy ADB):

    adb shell am start -n com.tvguard.app/.MainActivity --ez unlock true

## Điện thoại phụ huynh

Cài APK, chọn "Điện thoại phụ huynh". Mã TV đã tự điền sẵn; nếu TV dùng mã khác thì bấm "Thay đổi mã TV" để nhập.

## Ghi chú

- Mã TV là "mật khẩu": ai biết mã + URL database thì điều khiển được TV. Giữ kín.
- Android không cho app thường tự chống gỡ cài đặt tuyệt đối (cần Device Owner). Các biện pháp trên
  chặn trẻ thao tác thông thường qua điều khiển TV, không chống được người rành kỹ thuật dùng ADB.
- Chụp màn hình dùng AccessibilityService.takeScreenshot (Android 11+). Nội dung DRM có thể ra ảnh đen.
- "Tắt nguồn TV" cần ADB qua mạng đang bật trên TV (cổng 5555, kiểu `adb tcpip`); Wireless debugging kiểu mới (cổng ngẫu nhiên) không dùng được.
- Lệnh được TV hỏi mỗi 2 giây nên có độ trễ vài giây.
