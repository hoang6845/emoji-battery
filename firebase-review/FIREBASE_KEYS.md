# Firebase sau khi đổi nguồn data

Code đã nối nguồn mới. Các file JSON ở `firebase-review/` chỉ để xem/copy, không được đóng vào APK. Chưa publish Firebase hoặc GitHub.

## Key mới cần tạo

| Key | Copy toàn bộ array từ file | Số item |
| --- | --- | --- |
| `data_sticker` | `data_sticker.json` | 317 |
| `data_charging_animation` | `data_charging_animation.json` | 74 |

## Key đã có, cập nhật value mới

| Key | Copy toàn bộ array từ file | Số item |
| --- | --- | --- |
| `data_animation` | `data_animation.json` — chỉ status bar | 27 |
| `data_wallpaper_pet_on_screen` | `data_wallpaper_pet_on_screen.json` | 58 |
| `data_emotion` | `data_emotion.json` | 24 |
| `data_background` | `data_background.json` | 33 |
| `data_battery_v3` | `data_battery_v3.json` ? Home + Battery trong Customize Status Bar | 120 |
| `data_battery_troll` | `data_battery_troll.json` ? Battery Troll | 100 |

Các parameter trên dùng kiểu **String**, value là JSON array đầy đủ. `firebase-values.review.json` là bản gộp để duyệt, không phải template import Firebase. Publish đồng thời 8 key để app chuyển đủ các bộ data. Có thể dùng URL JSON HTTPS nếu sau này tự host các file; hiện các JSON này chưa được push lên repo.

JSON giữ path tương đối. App ghép base cố định `https://raw.githubusercontent.com/hoang6845/data_emoji_battery/main/` rồi encode khoảng trắng/Unicode. Không cần access key cho 6 nguồn này. Sticker mở bằng `free: true`, các bộ còn lại bằng `is_lock: false`. Category lấy từ từng item, không dùng category pet cho wallpaper. Đã loại 4 GIF quá giới hạn thumbnail 8 MiB.

## Những key giữ nguyên value và nguồn cũ

| Key | Chức năng |
| --- | --- |
| `data_theme_v3` | Theme c?: gi? value/ngu?n ?? t??ng th?ch; Home ?? chuy?n sang `data_battery_v3` |
| `theme_type_v3` | Category theme c?: gi? nguy?n, kh?ng c?n ?i?u khi?n category Home |
| `data_emoji_v3` | Battery Emoji / emoji status bar; không còn dùng làm sticker |
| `data_notch` | Notch |
| `data_pet` | Pet idle |
| `data_pet_moving_v3` | Pet moving |
| `pet_type` | Category pet moving |
| `data_character_pet_on_screen` | Character pet từ nguồn pet |
| `data_category_pet_on_screen` | Category character pet, không thay bằng category wallpaper |
| `emoji_access_key_remote_data` | Token nguồn emoji cũ |
| `emoji_access_key_remote_data_backup` | Token dự phòng nguồn emoji cũ |
| `pet_access_key_remote_data` | Token nguồn pet cũ |
| `emoji_battery_content_url` | URL catalog tổng nếu đang cấu hình; không cần đổi cho đợt này |

Các key quảng cáo và các cấu hình không nằm trong 8 key thay data cũng giữ nguyên. Không xóa token cũ: các tính năng chưa thay vẫn có thể cần chúng. Chế độ chọn nguồn pet trong `Constants.PET_DATA_SOURCE` giữ nguyên.

## Cache và triển khai

- Các collection tách riêng: `stickers`, `charging_animations`, `animations` (status bar). Preview/apply/favorite charging dùng `charging_animations`; status bar dùng `animations`.
- Cache JSON nguồn mới dùng namespace riêng, không ghép path trong cache nguồn cũ sang repo mới. Cache catalog tổng/selection đã áp dụng vẫn được giữ như trước; người dùng có thể chọn và Apply item mới để thay selection cũ.
- Không có bundled fallback mới trong APK theo yêu cầu. Nếu chưa cấu hình Firebase, danh sách mới có thể trống; dữ liệu đã cache trước đó vẫn có thể xuất hiện trong các collection cũ cho đến khi fetch thành công.
- Fetch Firebase hiện đặt khoảng cách tối thiểu 1 giờ. Khi kiểm tra, dùng lần cài sạch hoặc chờ cửa sổ fetch; mở lại app không đảm bảo fetch ngay.
- JSON lỗi/tải lỗi giữ last-known-good; array `[]` chủ động xóa danh sách đó. Key mới không tự được tạo trên Firebase chỉ vì code đã khai báo.

## Kiểm chứng code

- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`: thành công.
- Sau bổ sung battery/troll: 16 unit test pass, 0 failure/error. Hai JSON mới đã kiểm tra đủ 220 file local, đúng field, ID không trùng, path tương đối, mở toàn bộ và không quá 8 MiB.
- Unit test mới kiểm tra encode URL, base nguồn cũ, sticker độc lập với emoji, hai bộ animation tách riêng, xóa riêng collection charging, wallpaper category không ghi đè category pet.
- Đã kiểm tra nội dung ZIP APK: không có thư mục review hoặc 8 JSON catalog mới.
- Chưa publish Firebase và chưa chạy thử tải/apply trên thiết bị với config mới.
