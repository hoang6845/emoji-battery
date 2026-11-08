# Dữ liệu và chức năng Apply

> Cập nhật nguồn public 2026-11-05: xem [bảng key Firebase hiện tại](../firebase-review/FIREBASE_KEYS.md). Sticker, wallpaper, emotion, background và hai bộ animation đã nối repo `hoang6845/data_emoji_battery`; các mô tả dùng chung animation/sticker tạm và wallpaper nguồn pet bên dưới là tài liệu trước lần thay nguồn này.

Cập nhật 2026-11-02. Đã đọc code hai nguồn `C:/emoji_battery_android` và `C:/pet_on_screen_android`. Phần runtime mới chỉ được sửa/rà code, **chưa chạy build hoặc test theo yêu cầu người dùng**. Kết quả 4 schema tests của lượt trước không xác nhận runtime mới.

Phạm vi: Apply pet, status bar/theme, charging, wallpaper, notch, sticker, gesture, Battery Troll và danh mục. Không triển khai Premium/IAP, coin, mua đồ, nuôi pet; không thay đổi logic Search/Favorites.

## Firebase: tạo key gì, copy JSON nào?

Tạo parameter trong Remote Config của Firebase EmojiBattery, dán value và Publish changes. Tên key **không có `.json`**. Dùng String cho token, filename/URL và JSON array. Code khai báo defaults trên thiết bị; không tự tạo parameter trên Firebase Console.

### Token

| Key Firebase EmojiBattery | Copy value từ Firebase app nguồn |
| --- | --- |
| `emoji_access_key_remote_data` | `access_key_remote_data` của emoji |
| `emoji_access_key_remote_data_backup` | `access_key_remote_data_backup` của emoji; có thể trống |
| `pet_access_key_remote_data` | `access_key_remote_data` của pet |

Không để token trong source, JSON artwork hoặc assets. Code chỉ gửi token tới repo tương ứng, không theo HTTP redirect. Hai app cùng tên key cũ nên đã tách token để không ghi đè nhau.

### JSON nguồn emoji

Copy nguyên JSON array đang publish dưới đúng key tương ứng. Nếu muốn bundled fallback, tên file bằng key + `.json`.

| Key Firebase | Tên file fallback | Chức năng |
| --- | --- | --- |
| `data_theme_v3` | `data_theme_v3.json` | Home/theme, áp `battery_src` + `emoji_src` |
| `theme_type_v3` | `theme_type_v3.json` | Thứ tự/tên nhóm theme |
| `data_emoji_v3` | `data_emoji_v3.json` | Emoji động trong status bar |
| `data_battery_v3` | `data_battery_v3.json` | Battery icon; ảnh fallback cho Troll |
| `data_background` | `data_background.json` | Background status bar, không phải wallpaper điện thoại |
| `data_emotion` | `data_emotion.json` | Emotion |
| `data_animation` | `data_animation.json` | Animation status bar và danh sách media sạc |
| `data_notch` | `data_notch.json` | Kiểu notch |
| `data_pet` | `data_pet.json` | Pet idle, `gifUrls` |
| `data_pet_moving_v3` | `data_pet_moving_v3.json` | Pet moving, `walkStatuses`, `climb/drop/fly` |
| `pet_type` | `pet_type.json` | Thứ tự/tên nhóm pet moving |
| `data_battery_troll` | `data_battery_troll.json` | **Key mới tùy chọn**, artwork riêng cho Troll |

Không đổi tên JSON moving v2 trong repo nguồn thành v3: phải copy đúng schema v3 từ Firebase nguồn.

### JSON nguồn pet

| Key Firebase | Value |
| --- | --- |
| `data_character_pet_on_screen` | `data_character_pet_on_screen.json` hoặc JSON array đầy đủ |
| `data_wallpaper_pet_on_screen` | `data_wallpaper_pet_on_screen.json` hoặc JSON array đầy đủ |
| `data_category_pet_on_screen` | JSON array tên nhóm của app pet |

Character và wallpaper để trống vẫn thử filename mặc định trên GitHub. App pet nguồn mới lấy wallpaper từ file GitHub, nên ưu tiên file đang dùng thay vì JSON cũ trên Firebase.

File category nguồn có tại `C:/pet_on_screen_android/app/src/main/assets/data_category_pet_on_screen.json`.

### GitHub và fallback

Đã dùng nguyên base URL từ code nguồn:

- Emoji: `https://raw.githubusercontent.com/Braly-Danang-2/emoji_battery_data/main/`
- Pet: `https://raw.githubusercontent.com/BaseAppTech/data_pet_on_screen/main/`

Path tương đối được ghép đúng repo; URL HTTPS đầy đủ được giữ nguyên. Nếu chuyển artwork sang repo khác, có thể dùng URL HTTPS public; token hiện chỉ gắn cho hai repo trên.

Copy các file fallback vào `app/src/main/assets/source_data/`. Các file pet cũng đặt tên bằng key + `.json`. Không cần gộp JSON nguồn vào `app_content.json`.

Catalog tổng bundled/cache được hiển thị trước; sau fetch, dữ liệu nguồn hợp lệ ghi vào cache riêng từng key rồi cập nhật màn hình. JSON sai hoặc download lỗi giữ cache/file fallback. `[]` chủ động xóa danh sách; value trống giữ fallback (character/wallpaper còn thử filename mặc định). Fetch Firebase tối thiểu cách 1 giờ. Khi Firebase chưa initialized chỉ có catalog tổng bundled/cache.

Key `emoji_battery_content_url` vẫn dùng được cho catalog tổng schema 1/2, đặc biệt `stickers` và `links`. Dữ liệu riêng từ hai nguồn có ưu tiên trên catalog tổng. Debug mặc định dùng data thật; `-PuiTestMode=true` dành cho preview UI.

## Chức năng đã nối code Apply

| Chức năng | Luồng code hiện tại |
| --- | --- |
| Pet | Chọn tối đa 6 pet; tải/cache toàn bộ frame trước khi Apply; có tiến độ/hủy/lỗi; lưu snapshot rồi chạy `AnimatedPetService` |
| Pet idle/moving/character | GIF idle; frame walk theo mốc pin 20/50/80/100; action climb/drop/fly và 11 action character. Thiếu action thì fallback walk/thumbnail. Có kéo thả, tap, chuyển action và chuyển hướng ở biên |
| Vị trí/chuyển động pet | Vị trí đầu theo lựa chọn; Idle, Walk, Bounce, Follow touch. Follow touch/kéo thả nhận touch trên cửa sổ pet, không theo dõi touch toàn màn hình của app khác |
| Status bar/theme | Lưu media item đã chọn; `StatusRenderer` hiển thị ảnh/GIF emoji, battery, emotion, animation, background, ngày/giờ và % pin thật; theme tách `battery_src` + `emoji_src`; dùng cùng renderer ở preview và overlay |
| Status controls | Kích thước, màu, format ngày, font local, style icon và bật/tắt từng phần được đọc khi render; Apply cập nhật service |
| Charging | Tải trước media khi Apply, enable monitor. Cắm sạc mở cửa sổ media từ service đang chạy; rút sạc/tap hoặc hết thời gian thì đóng. Ảnh/GIF/WebP qua ImageDecoder; MP4/WebM qua VideoView; có % pin và tùy chọn ẩn thông tin |
| Wallpaper | Chọn ảnh full; Apply mở chọn Home screen/Lock screen/Both; tải rồi gọi WallpaperManager, báo thành công hoặc lỗi. Preview Charging dùng ảnh đó làm nền màn sạc |
| Notch | Nút chọn style đọc `data_notch`; Hidden tắt; Save áp ảnh, width/height và offset X/Y |
| Sticker | Apply tối đa 5 ảnh vào overlay dùng FLAG_NOT_TOUCHABLE; lưu size/rotation/offset riêng từng sticker; import ảnh bằng Open Document và giữ quyền URI; bật/tắt áp vào service |
| Gesture | Tap, long press và ba swipe trên overlay chạy action đã chọn; Open App mở EmojiBattery; các action hệ thống qua Accessibility do người dùng tự bật |
| Battery Troll | Chọn artwork; số giả tùy chỉnh hoặc random; tab Real dùng số pin thật; áp size, bật/tắt artwork và phần trăm; không chỉnh giá trị pin thật của hệ điều hành |

Các overlay chạy foreground service có thông báo Stop, kiểm tra quyền hiển thị trên app khác, giải phóng view/media/callback khi đóng. Dữ liệu đã chọn được lưu snapshot để cập nhật catalog không làm mất selection. Media đã tải nằm trong `files/applied_media`; không cần token lại khi đọc media đã cache.

## Danh mục giống nguồn

- Home hiển thị theme battery từ `data_theme_v3`, nhóm theo `type` với thứ tự/nhãn từ `theme_type_v3`.
- Pet moving dùng `pet_type`; pet idle có nhóm Idle. Character dùng `data_category_pet_on_screen`, ghép nhóm qua `/<type>/` trong thumbnail đúng cách app pet nguồn lọc.
- Wallpaper dùng `category` trong từng item và nhãn/thứ tự từ `data_category_pet_on_screen`.
- Nhãn dùng `name`, hoặc ngôn ngữ hiện tại, fallback `en`/mã nguồn. Có All để xem toàn bộ.
- Emoji/Battery/Emotion/Background/Animation nguồn emoji là danh sách phẳng. Đã bỏ tab giả định Naruto/BTS/... ở picker status bar; không tự gán danh mục không có trong nguồn.
- Search/Favorites và danh mục sticker riêng giữ nguyên theo yêu cầu.

## Bạn cần bổ sung gì?

### 1. Token, JSON và artwork thực

Publish các key ở bảng trên và đảm bảo token có quyền đọc repo tương ứng. Không có JSON/frame thực thì không thể xác nhận hình ảnh và action của từng pet. Frame tải từng file theo danh sách nguồn, không yêu cầu zip pack.

### 2. Artwork Battery Troll (tùy chọn)

Logic Troll đã có; phần bạn cần bổ sung là catalog/ảnh riêng nếu không muốn dùng ảnh battery sẵn có. Key mới `data_battery_troll` dùng schema ImageModel như battery nguồn:

```json
[
  {
    "id": "troll_001",
    "name": "My troll battery",
    "src": "troll/my_battery.png",
    "is_lock": false
  }
]
```

`src` tương đối được ghép repo emoji; hoặc đặt URL HTTPS đầy đủ của ảnh. Dán array vào Firebase key `data_battery_troll`, hoặc copy `data_battery_troll.json` vào `source_data`. Khi catalog riêng trống, picker dùng `data_battery_v3`. Không có ảnh vẫn có thể hiển thị số pin giả.

### 3. Sticker data

Hai repo nguồn chưa cung cấp schema sticker tương ứng. Cần thêm `stickers` trong catalog tổng tại URL của `emoji_battery_content_url`, ví dụ:

```json
{
  "schema_version": 2,
  "themes": [],
  "emojis": [],
  "stickers": [
    {"id": "sticker_001", "name": "My sticker", "category": "Cute", "image_url": "https://your-host/sticker.png"}
  ]
}
```

Bạn cũng có thể import ảnh trên thiết bị ngay khi chưa có catalog. Search/Favorites không được mở rộng trong lần này.

### 4. Charging artwork

`data_animation` của app emoji là media status bar, có thể nhỏ. Code phát được media đó nhưng muốn trải nghiệm sạc toàn màn hình đẹp cần ảnh/GIF/video đủ độ phân giải. Với video: dùng `src` kết thúc `.mp4`/`.webm`, hoặc thêm `media_type: "video"`; `thumbnail` là ảnh preview. Không hỗ trợ Lottie JSON bằng player này. Wallpaper cần `imagePath` full, `imagePathThumb` preview và `category` đúng nhóm.

## Giới hạn hệ điều hành và kiểm tra còn cần làm

- Overlay là lớp hiển thị riêng, không thay status bar/hardware notch thật của Android và không sửa số pin hệ thống.
- Các action hệ thống cần người dùng bật Accessibility. Service không đọc cây nội dung màn hình, không tự bật quyền. Action có thể không khả dụng trên một số thiết bị. Tham khảo [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).
- Màn sạc cần quyền overlay, monitor đã được bật và tiến trình service còn chạy. Không triển khai tự khởi động sau force-stop/reboot. Màn khóa và cơ chế tiết kiệm pin của từng hãng cần kiểm tra trên máy thật.
- Android có thể chặn touch xuyên qua vùng nhiều overlay chồng nhau; cần kiểm tra bố trí sticker trên thiết bị. Window chỉ chiếm vùng artwork, không tạo cửa sổ trong suốt toàn màn hình cho sticker.
- Chưa kiểm chứng codec/media thực, timing animation, xử lý xoay màn hình, UI runtime hoặc permission flow bằng build/device test ở lần sửa này. Cần kiểm tra khi bạn cho phép build và đã publish token/JSON.
- Premium/IAP, metadata khóa/giá, coin/mua đồ/nuôi pet không được xử lý trong phạm vi này. Không copy ads config, webhook hoặc notification logic từ app nguồn.
