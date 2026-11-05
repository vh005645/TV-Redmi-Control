# TV Guard v1.18 - kiểm soát trẻ em trên Redmi TV (Android 11+)

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

### Nếu HyperOS không cho app tự khởi động

Trên màn hình app TV có mục **"Nhật ký khởi động"** (dòng mới nhất ở cuối). Sau khi tắt TV rồi bật lại, đợi 1-2 phút, mở app
và xem nhật ký: nếu không có dòng "Nhận broadcast ...BOOT_COMPLETED" hoặc "Tiến trình app khởi động" lúc máy vừa bật (số giây
"máy bật" nhỏ) thì HyperOS đang chặn tự khởi động. Thử cấp quyền (lệnh nào báo lỗi thì bỏ qua, mỗi ROM một khác):

    adb shell appops set com.tvguard.app AUTO_START allow
    adb shell cmd appops set com.tvguard.app 10008 allow
    adb shell cmd appops set com.tvguard.app 10021 allow
    adb shell cmd deviceidle whitelist +com.tvguard.app

(10008 = tự khởi động, 10021 = mở cửa sổ khi chạy nền, trên MIUI/HyperOS.) Hoặc vào Cài đặt TV > Ứng dụng > TV Guard, bật
"Tự khởi động"/"Autostart" và đặt pin "Không hạn chế" nếu có. Ngoài ra app có job nền (WakeJob, 15 phút/lần, hệ thống giữ lại sau
khi khởi động lại) để tự bật lại dịch vụ nếu broadcast bị chặn; job này chạy chậm hơn broadcast.

## Tự chạy khi bật TV (v1.6 - nhiều lớp dự phòng)

Khi TV bật lại, app tự khôi phục và tự mở thu nhỏ qua các đường sau (đường nào chạy trước cũng được):

1. `BOOT_COMPLETED` / `QUICKBOOT_POWERON` / `TIME_SET` (BootReceiver).
2. Dịch vụ Trợ năng do hệ thống tự bind; nếu đã bật trong Settings mà chưa kết nối thì app **tắt/bật lại** để ép bind (Cfg.ensureService).
3. KeepService nghe sự kiện **màn hình bật / hết chế độ chờ** (SCREEN_ON, USER_PRESENT...) -> khôi phục + mở app thu nhỏ.
   (Bản cũ chỉ tự mở trong 10 phút đầu sau khi bật máy và chỉ 1 lần/tiến trình nên TV thoát chế độ chờ không mở lại được.)
4. Báo thức canh gác 60 giây/lần: tiến trình bị HyperOS giết lúc TV tắt thì báo thức đánh thức lại.
5. WakeJob: job định kỳ 15 phút + job "chuỗi" có ràng buộc mạng, được hệ thống giữ lại sau khi khởi động lại.
6. Nếu mở app từ nền bị HyperOS chặn: app dùng ADB nội bộ chạy `am start ... --ez minimize true`.
7. Tự cấp lại quyền chạy nền qua ADB nội bộ (autostart 10008/10021, SYSTEM_ALERT_WINDOW, deviceidle, standby-bucket)
   sau khi bạn đã bấm "Kiểm tra quyền tắt nguồn" + "Luôn cho phép" 1 lần.

8. (v1.7) Dịch vụ "neo" KeepListener (NotificationListener, tự bật bằng WRITE_SECURE_SETTINGS): hệ thống tự khởi động lại nó
   khi tiến trình bị giết, rồi nó ép bind lại dịch vụ Trợ năng. Vòng lặp Firebase có "nhịp tim" 60 giây, treo thì tự tạo lại,
   và được tạo lại mỗi khi TV bật màn hình. Nhật ký giờ ghi 80 dòng và ghi **lý do tiến trình trước bị kết thúc**.
9. (v1.8) Khi trợ lý giọng nói của TV (`com.xiaomi.voicecontrol`) hiện lên lúc bật TV, dịch vụ Trợ năng coi đó là tín hiệu
   "TV vừa bật": khôi phục ngay và mở app thu nhỏ (tối đa 1 lần/phút khôi phục, 1 lần/10 phút mở app).
   Chỉ có tác dụng khi dịch vụ Trợ năng đã kết nối.

### v1.9 - nguyên nhân thật và cách xử lý
Redmi nội địa: bấm nút nguồn điều khiển ("Short press the on key" = Screen off) chỉ tắt màn hình, vài phút sau mới tắt hẳn
và lúc đó HyperOS **buộc dừng (Force stop) mọi app** -> app ở trạng thái "stopped", mất dịch vụ Trợ năng, không nhận BOOT_COMPLETED
của lần bật sau. Tắt bằng `reboot -p` (nút khóa của app) thì không bị vậy.
- KeepService nghe SCREEN_OFF: màn hình tắt -> chờ 3 giây -> nếu vẫn tắt thì chạy `reboot -p` (ADB nội bộ). Có nút bật/tắt trong màn hình TV.
- Trước khi tắt nguồn, nếu đang khóa kiểu "Tắt nguồn" thì app **mở khóa** và đẩy trạng thái lên Firebase; thêm lớp dự phòng:
  phát hiện TV vừa khởi động lại (boot_count đổi) thì mở khóa + bỏ hẹn giờ đã hết hạn.
- Đã bỏ: báo thức canh gác 60 giây, job chuỗi 1 phút, tự mở app khi sáng màn hình / hiện trợ lý giọng nói / job nền.
  App chỉ tự mở thu nhỏ khi TV khởi động (BOOT_COMPLETED / dịch vụ Trợ năng kết nối trong 10 phút đầu) và khi cập nhật app.

### v1.10 - chế độ "Shutdown" của nút nguồn
Ở chế độ **Shutdown**, bấm nút nguồn là HyperOS buộc dừng mọi app NGAY (không phải vài phút sau), nên code trong app không kịp chạy.
Vì vậy app đặt một tiến trình **shell** (`assets/tvguardwatch.sh`, chạy qua ADB nội bộ, không thuộc app nên không bị buộc dừng):
- đọc `getevent`: thấy `KEY_POWER DOWN` -> `reboot -p` ngay;
- đọc logcat: thấy system_server buộc dừng hàng loạt app (có TV Guard) trong vài giây -> `reboot -p`.
Nhật ký của bộ canh: `/data/local/tmp/tvguard_watch.log`; app ghi lại 6 dòng cuối vào "Nhật ký khởi động" mỗi lần chạy lại.
Chế độ "Screen off" vẫn được xử lý bằng SCREEN_OFF trong app (chờ 1 giây). Khóa kiểu "Tắt nguồn" được mở khi TV khởi động lại (boot_count).

### v1.13
- Log thật cho thấy phím nguồn của điều khiển Bluetooth KHÔNG hiện ra là KEY_POWER trong getevent; dấu hiệu chắc chắn duy nhất là
  system_server buộc dừng com.tvguard.app. Bộ canh (assets/tvguardwatch.sh, ver 3) giờ phản ứng ngay khi có dòng đó:
  mở lại app (gỡ trạng thái "stopped" để lần bật sau nhận BOOT_COMPLETED) rồi `reboot -p` sau 3 giây.
- Nếu ngay trước đó có bấm Back/Home liên tục (>=2 lần trong 8 giây) hoặc vừa tự cấp quyền (30 giây): chỉ MỞ LẠI app, không tắt TV.
- Tên gói trợ lý giọng nói: com.xiaomi.mitv.voicecontrol.

### v1.14
Mỗi lần TV tắt, system_server buộc dừng (Force stop) TV Guard; Android khi đó **gỡ dịch vụ Trợ năng của app khỏi danh sách đã bật**
nên lần bật sau hệ thống không bind nó. Bộ canh shell (ver 4) giờ tự ghi lại TvService / KeepListener vào
`enabled_accessibility_services` / `enabled_notification_listeners` ngay khi thấy dòng "Force stopping com.tvguard.app" (và 1s, 3s sau đó),
đồng thời quét 8 giây/lần. Nhật ký bộ canh: dòng "da bat lai Tro nang/thong bao trong danh sach he thong".

### v1.15 - HomeShim (launcher trung gian)
App Drawer (`tungbui.drawer`) là launcher mặc định; hệ thống tự khởi động launcher mặc định mỗi lần bật TV (không bị chặn tự khởi động).
`HomeShim` là một Activity HOME trong suốt: chạy `Cfg.recover` rồi mở ngay launcher thật (`HomeShim.REAL_HOME`), nếu lỗi thì mở launcher khác.
- Thử trước (chưa đổi mặc định): `adb shell am start -n com.tvguard.app/.HomeShim` -> phải thấy App Drawer hiện lên.
- Đặt làm launcher mặc định: `adb shell cmd package set-home-activity com.tvguard.app/.HomeShim`
- HOÀN TÁC: `adb shell cmd package set-home-activity tungbui.drawer/.MainActivity`

Cách kiểm tra: tắt TV, bật lại, đợi 1-2 phút, mở app xem "Nhật ký khởi động" (dòng "Khôi phục (...)", "Ép bind lại...").

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
