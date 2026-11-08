# JSON đề xuất — chỉ để duyệt

Thư mục này ở gốc dự án, ngoài mọi Android source set. Không đưa vào APK. Người dùng đã duyệt sửa code; app đã nối nguồn mới. Chưa publish Firebase hay GitHub. Xem [key cần cập nhật và key giữ cũ](FIREBASE_KEYS.md).

## Nguồn đã kiểm tra

- Folder: `C:/Users/ASUS-PR0/Downloads/data_emoji_battery`.
- GitHub: `https://github.com/hoang6845/data_emoji_battery`, nhánh `main`.
- Commit: `3565fe331947901464ae084e329132436441d1fe`. Local HEAD trùng GitHub và working tree nguồn sạch.
- `github-files.json`: danh sách file/size/blob SHA lấy từ GitHub để đối chiếu đường dẫn và nội dung local.
- JSON chỉ chứa đường dẫn tương đối từ gốc repo, ví dụ `animation/Animation_Status Bar/001_bear-clapping-4hjv0nfIf9.gif` hoặc `Data Sticker/Data Sticker (DONE)/anime_4_dong_tac/gif/anime_01.gif`. Giữ khoảng trắng/Unicode đúng tên file, không chứa domain hoặc access key.
- Khi được duyệt sửa code, app sẽ ghép base cố định `https://raw.githubusercontent.com/hoang6845/data_emoji_battery/main/` cho các nguồn mới và encode path đúng một lần khi tạo URL tải. Các nguồn chưa thay vẫn giữ base cũ. Bước này chưa sửa code nên chưa áp dụng việc ghép base mới trong app.

## Model và mapping

| File / key dự kiến | Model | Mục đích |
| --- | --- | --- |
| `data_sticker.json` / `data_sticker` (key đề xuất mới) | `id`, `name`, `category`, `image_url`, `free` như sticker trong catalog cũ | GIF sticker, không thay `data_emoji_v3` |
| `data_wallpaper_pet_on_screen.json` / key cũ cùng tên | `imagePath`, `imagePathThumb`, `category`; thêm `is_lock: false` được adapter hiện tại hỗ trợ | Wallpaper |
| `data_animation.json` / `data_animation` | `id`, `src`, `is_lock` | Animation status bar |
| `data_charging_animation.json` / `data_charging_animation` (key đề xuất mới) | ImageModel cũ + trường `category` mà adapter hiện tại đã đọc được | Animation charging, 8 nhóm theo folder |
| `data_emotion.json` / `data_emotion` | `id`, `src`, `is_lock` | Emotion custom status bar |
| `data_background.json` / `data_background` | `id`, `src`, `is_lock` | Background custom status bar |

Data mới là bộ artwork khác nên ID mới ổn định được sinh từ đường dẫn. Không thể giữ từng ID của data cũ bằng cách ghép theo thứ tự. Giữ tên field ảnh của model cũ, giá trị là đường dẫn tương đối.

Sticker dùng GIF trong `gif/` để giữ animation/nền trong suốt. Không thêm 4 PNG frame thành 4 sticker; không dùng preview JPG có nền màu và không đưa ảnh `qa_*.jpg` vào catalog. Wallpaper không có folder thumbnail riêng nên `imagePathThumb` trỏ cùng ảnh gốc. Emotion, background, status animation không có thư mục category con nên giữ danh sách phẳng như model cũ.

`firebase-values.review.json` là bản gộp để đọc: key → array, **không phải template Firebase để import thay toàn bộ cấu hình**. Mỗi JSON array riêng tương ứng value String của parameter Firebase. Chỉ cập nhật các key đã duyệt; không thay cấu hình Firebase khác. Chưa publish JSON: đường dẫn trong các item trỏ artwork đã có trên GitHub khi ghép với base mới.

## Những phần giữ nguyên

Theme, Battery Emoji (`data_emoji_v3`), Battery styles, Battery Troll, notch, pets và category pet, links, quảng cáo cùng các key/token hiện có không nằm trong bộ thay đổi. Không xóa token của nguồn cũ vì các phần này vẫn dùng nguồn cũ. Không thay `data_category_pet_on_screen` bằng category wallpaper vì key đó đang dùng chung với pet.

## Code đã nối sau khi duyệt

1. Đọc key sticker riêng và bỏ đoạn alias tạm `stickers` → `emojis`.
2. Đọc key charging riêng, giữ `data_animation` cho status bar; nối đúng màn chọn/preview/apply/favorites.
3. Lấy category sticker/wallpaper/charging từ trường `category` theo folder; tách category wallpaper khỏi category pet.
4. Giữ nguyên đường đọc các phần chưa được yêu cầu thay.

Hiện chưa có snapshot Firebase đang activate trên thiết bị, nên các nhận xét về data cũ dựa vào code dự án, tài liệu nguồn và model app nguồn tại `C:/emoji_battery_android`. `app/src/main/assets/app_content.json` hiện là catalog rỗng. Không tuyên bố đã đọc các giá trị Firebase production.

## Kiểm tra

Generator kiểm tra mọi URL artwork có trong GitHub tree, SHA blob local khớp GitHub, ID không trùng, rồi ghi số lượng/category vào `validation.json`. Không cần build APK vì chỉ tạo tài liệu và JSON ngoài app.

## Lựa chọn đã xác nhận và kết quả

- Mở toàn bộ: ImageModel và wallpaper dùng `is_lock: false`, sticker dùng `free: true`. Wallpaper cần cờ này vì adapter hiện tại coi thiếu `is_lock` là khóa; các field ảnh vẫn giữ model cũ.
- Category sticker bỏ hậu tố `_4_dong_tac`, ví dụ `cute_animal_4_dong_tac` → `cute_animal`. Đường dẫn file thật không đổi.
- Loại media vượt 8,388,608 byte (8 MiB), là giới hạn RemoteImageLoader dùng tải hình catalog. MediaAssets cho phép 48 MiB nhưng ảnh vẫn phải qua được màn danh sách nên dùng ngưỡng thấp hơn. Không nén hoặc sửa artwork nguồn.
- Đã loại 4 GIF: 2 charging, 2 status bar. Chi tiết đường dẫn và dung lượng trong `validation.json`; các file này không nằm trong JSON catalog.
- Còn 317 sticker, 58 wallpaper, 74 charging animation, 27 status bar animation, 24 emotion, 33 background. Toàn bộ 533 URL artwork còn lại đã khớp SHA blob trong GitHub tree lấy ở lượt trước.
- Key mới `data_sticker` và `data_charging_animation` vẫn là đề xuất để duyệt, chưa tạo/publish trên Firebase. Việc tách key chỉ tách nguồn danh sách; không chuyển model item sang kiểu mới. `data_animation` cũ dành riêng status bar.

Tạo lại: `python -B firebase-review/generate.py --locked false --strip-sticker-suffix`.

## B? sung battery v? Battery Troll

?? th?m `data_battery_v3.json` (120 item) v? `data_battery_troll.json` (100 item), path t??ng ??i, m? to?n b?. Xem `FIREBASE_KEYS.md` ?? c?p nh?t Firebase v? t?nh tr?ng ch?a push 220 artwork m?i. Khi artwork ch?a publish, ch?y generator v?i `--allow-unpublished` ?? ghi r? c?c path ch?a ki?m ch?ng tr?n GitHub; kh?ng ??nh d?u ch?ng l? ?? x?c minh online.
