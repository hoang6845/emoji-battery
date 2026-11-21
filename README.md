## Danh s?ch key qu?ng c?o

Ngu?n c?u h?nh: [ads_config.json](app/src/main/assets/ads_config.json). Mapping s? d?ng: [BatteryAdPlacements.kt](app/src/main/java/com/hd/emojibattery/ads/BatteryAdPlacements.kt), [BatteryAds.kt](app/src/main/java/com/hd/emojibattery/ads/BatteryAds.kt) v? [UnlockItemSheet.kt](app/src/main/java/com/hd/emojibattery/ads/UnlockItemSheet.kt).

### Placement key ?ang ???c g?i trong code

| Key | Lo?i | V? tr? / m?c ??ch |
| --- | --- | --- |
| `emoji_native_language` | Native | Language |
| `emoji_native_intro1` | Native | Intro 1 |
| `emoji_native_intro2` | Native | Intro 2 |
| `emoji_native_intro3` | Native | Intro 3 |
| `emoji_native_collab_home` | Native | Home, Theme Category |
| `emoji_native_collab_pet_on_screen` | Native | Nh?m m?n Pet (tr? m?n c? banner) |
| `emoji_native_all` | Native | Settings |
| `emoji_native_collab_custom` | Native | Customize |
| `emoji_native_collab_notch` | Native | Notch, Notch Exit |
| `emoji_native_collab_battery_troll` | Native | Battery Troll, Battery Troll Setup, Troll Setup 2, Troll Percent |
| `emoji_native_collab_emoji_sticker` | Native | Nh?m m?n Sticker |
| `emoji_native_list_data_custom` | Native | Nh?m m?n Status Bar |
| `emoji_native_preview` | Native | Battery Style Preview, Battery Preview, Charge Preview |
| `emoji_native_pop_up_unlock` | Native | Bottom sheet m? kh?a item |
| `emoji_full_native` | Native to?n m?n h?nh | Intro 5 |
| `emoji_inter_splash` | Interstitial | Splash |
| `emoji_inter_in_app` | Interstitial | Chuy?n m?n theo transitionNeedsInter() |
| `emoji_banner_all` | Banner | Search, Favorites |
| `emoji_banner_custom` | Banner | Date Settings |
| `emoji_banner_guide` | Banner | How to use |
| `emoji_banner_pet_on_screen` | Banner | Pet More, Pet Detail |
| `emoji_reward` | Rewarded | Xem video m? kh?a item trong phi?n |
| `emoji_open` | App Open | Resume app theo ?i?u ki?n c?a AdsManager |

Banner ???c ?u ti?n hi?n th? n?u m?t m?n c? c? mapping banner v? native. C?c key tr?n c? ???ng g?i trong code; vi?c hi?n th? th?c t? c?n ph? thu?c c?u h?nh, consent, Premium v? tr?ng th?i t?i qu?ng c?o.

### Key c? c?u h?nh nh?ng ch?a th?y ???ng g?i trong app

| Key | Lo?i |
| --- | --- |
| `emoji_native_emoji_sticker` | Native |
| `emoji_native_preview_data` | Native |
| `emoji_native_pop_up` | Native |
| `emoji_banner_list_battery_troll` | Banner |
| `emoji_banner_pop_up` | Banner |
| `emoji_banner_preview` | Banner |

### Ad item / ad unit key theo floor

M?i placement b?n d??i khai b?o c?c key t??ng ?ng trong `ad_items` v? `ad_units`. `ordered_priority_ads` quy ??nh th? t? ?u ti?n:

| Placement | Key theo th? t? c?u h?nh |
| --- | --- |
| `emoji_native_language` | `emoji_native_language_high` ? `emoji_native_language_medium` ? `emoji_native_language_all` |
| `emoji_native_intro1` | `emoji_native_intro1_high` ? `emoji_native_intro1_medium` ? `emoji_native_intro1_all` |
| `emoji_native_intro2` | `emoji_native_intro2_high` ? `emoji_native_intro2_medium` ? `emoji_native_intro2_all` |
| `emoji_native_intro3` | `emoji_native_intro3_high` ? `emoji_native_intro3_medium` ? `emoji_native_intro3_all` |
| `emoji_native_collab_home` | `emoji_native_collab_home_high` ? `emoji_native_collab_home_medium` ? `emoji_native_collab_home_all` |
| `emoji_native_collab_pet_on_screen` | `emoji_native_collab_pet_on_screen_high` ? `emoji_native_collab_pet_on_screen_medium` ? `emoji_native_collab_pet_on_screen_all` |
| `emoji_native_all` | `emoji_native_all_high` ? `emoji_native_all_medium` ? `emoji_native_all_all` |
| `emoji_native_collab_custom` | `emoji_native_collab_custom_high` ? `emoji_native_collab_custom_medium` ? `emoji_native_collab_custom_all` |
| `emoji_native_collab_notch` | `emoji_native_collab_notch_high` ? `emoji_native_collab_notch_medium` ? `emoji_native_collab_notch_all` |
| `emoji_native_collab_battery_troll` | `emoji_native_collab_battery_troll_high` ? `emoji_native_collab_battery_troll_medium` ? `emoji_native_collab_battery_troll_all` |
| `emoji_native_collab_emoji_sticker` | `emoji_native_collab_emoji_sticker_high` ? `emoji_native_collab_emoji_sticker_medium` ? `emoji_native_collab_emoji_sticker_all` |
| `emoji_native_emoji_sticker` | `emoji_native_emoji_sticker_high` ? `emoji_native_emoji_sticker_medium` ? `emoji_native_emoji_sticker_all` |
| `emoji_native_list_data_custom` | `emoji_native_list_data_custom_high` ? `emoji_native_list_data_custom_medium` ? `emoji_native_list_data_custom_all` |
| `emoji_native_preview` | `emoji_native_preview_high` ? `emoji_native_preview_medium` ? `emoji_native_preview_all` |
| `emoji_native_preview_data` | `emoji_native_preview_data_high` ? `emoji_native_preview_data_medium` ? `emoji_native_preview_data_all` |
| `emoji_native_pop_up` | `emoji_native_pop_up_high` ? `emoji_native_pop_up_medium` ? `emoji_native_pop_up_all` |
| `emoji_native_pop_up_unlock` | `emoji_native_pop_up_unlock_high` ? `emoji_native_pop_up_unlock_medium` ? `emoji_native_pop_up_unlock_all` |
| `emoji_full_native` | `emoji_full_native_high` ? `emoji_full_native_medium` ? `emoji_full_native_all` |
| `emoji_inter_splash` | `emoji_inter_splash_high` ? `emoji_inter_splash_medium` ? `emoji_inter_splash_all` |
| `emoji_inter_in_app` | `emoji_inter_in_app_high` ? `emoji_inter_in_app_medium` ? `emoji_inter_in_app_all` |
| `emoji_banner_all` | `emoji_banner_all_high` ? `emoji_banner_all_medium` ? `emoji_banner_all_all` |
| `emoji_banner_custom` | `emoji_banner_custom_high` ? `emoji_banner_custom_medium` ? `emoji_banner_custom_all` |
| `emoji_banner_guide` | `emoji_banner_guide_high` ? `emoji_banner_guide_medium` ? `emoji_banner_guide_all` |
| `emoji_banner_list_battery_troll` | `emoji_banner_list_battery_troll_high` ? `emoji_banner_list_battery_troll_medium` ? `emoji_banner_list_battery_troll_all` |
| `emoji_banner_pet_on_screen` | `emoji_banner_pet_on_screen_high` ? `emoji_banner_pet_on_screen_medium` ? `emoji_banner_pet_on_screen_all` |
| `emoji_banner_pop_up` | `emoji_banner_pop_up_high` ? `emoji_banner_pop_up_medium` ? `emoji_banner_pop_up_all` |
| `emoji_banner_preview` | `emoji_banner_preview_high` ? `emoji_banner_preview_medium` ? `emoji_banner_preview_all` |
| `emoji_reward` | `emoji_reward_high` ? `emoji_reward_medium` ? `emoji_reward_all` |
| `emoji_open` | `emoji_open_high` ? `emoji_open_medium` ? `emoji_open_all` |

C?c placement trong `configs.two_floor_placements` d?ng 2 floor (`high ? all`): `emoji_native_pop_up`, `emoji_native_pop_up_unlock`, `emoji_banner_pop_up`; key `_medium` v?n c? trong JSON nh?ng b? l?c khi t?o danh s?ch request. C?c placement c?n l?i khai b?o 3 floor (`high ? medium ? all`).

AdMob App ID ???c khai b?o ri?ng t?i `BatteryAdPlacements.ADMOB_APP_ID`: `ca-app-pub-3940256099942544~3347511713` (ID demo). Placement key v? ad unit key l? t?n tra c?u c?u h?nh; AdMob unit ID th?c t? n?m trong `ad_units`.

## Rà soát flow nút bấm — 2026-11-20

### Sửa mở đúng app khi xin quyền / Apply status bar

- Application ID của project là `com.goldflow.emoji.battery.icon.statusbar.sticker.customize`; service class là `com.hd.emojibattery.GestureAccessibilityService`. Đây là app đang cài từ project này, không phải package của app tham khảo.
- Lỗi source: deep link Accessibility gửi `Intent.EXTRA_COMPONENT_NAME` dưới dạng `ComponentName` Parcelable. Android Settings [đọc tham số này bằng `getStringExtra` rồi `unflattenFromString`](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/master/src/com/android/settings/accessibility/AccessibilityDetailsSettingsFragment.java), nên tham số cũ không chọn được đúng service.
- Thêm `AppSettings`: dùng component dạng chuỗi với **runtime package của chính app**; tất cả nút xin quyền/Go to Settings/Restricted Apps/Battery Troll dùng cùng đích. Luồng Apply giữ Activity Result để tiếp tục sau khi người dùng cấp quyền. Chỉ fallback về danh sách Accessibility có đánh dấu service của app khi máy không cho mở trang chi tiết.
- Không đổi application ID, không chuyển sang package của app gốc. Không chạy build/test/device cho bản sửa này.

Đã rà 77 route màn hình trong `ScreenRouter` (78 hằng `ScreenIds`, trong đó `TEXT_NODE` là layer chữ, không phải màn hình). Các frame trạng thái dùng chung handler được rà theo nhóm chức năng. Bổ sung thêm Activity Crop ảnh nền, được gọi qua Activity Result.

Nguồn đối chiếu là code Java decompile tại `C:\Users\Admin\Downloads\sources\com\battery\emoji`, đặc biệt `HomeActivity`, `BatteryPermissionHelper`, `PreviewActivity`, `StatusBarCustomActivity`, các màn chọn icon, `NotchSetupActivity`, `BatteryTrollActivity`, `EmojiStickerActivity`, `PetOnScreenFragment`, `ChoosePetActivity`, `SearchDataActivity` và `LibraryTypeFragment`. Thiết kế How to use và Sticker dùng các node Figma `919:3528`, `919:3210`, `919:3213` đã đọc bằng Figma MCP Go. Đây là đối chiếu source; chưa chạy app gốc để chứng minh hành vi trên thiết bị.

### Các thay đổi trong đợt review này

| Màn hình / nút | Trước khi review | Đã sửa |
| --- | --- | --- |
| Navigation | Tab thứ ba mở Customize; Back từ một số tab hiện Exit ngay. | Bốn tab là Home / Pet / Gesture / Setting. Back từ tab khác về Home; Exit ở Home. Dashboard Customize vẫn truy cập được qua Settings → Customize để giữ các tính năng bổ sung của project. |
| Home — Enable Emoji Battery | Switch điều khiển charging monitor, không điều khiển status bar. | Bật/tắt status bar và notch như `BatteryPermissionHelper` của app gốc. Không tắt pet/sticker, không xóa cấu hình gesture; Charging vẫn có luồng Apply riêng. |
| Home — Battery DIY | Mở thêm dashboard Customize. | Mở trực tiếp Custom Status Bar. |
| Home — chọn battery | Vào editor ngay, thiếu bước Preview. | Mở Preview đúng mẫu; xem mẫu trước/sau trong cùng category, favorite đúng ID; Get mới mở editor. Preview riêng này không dùng nhầm flow Charging Preview. |
| Apply status bar | Lưu xong chỉ hiện toast và giữ nguyên editor. | Lưu thành công rồi trở lại màn gọi, theo flow `setResult/finish` của source; lỗi khởi chạy overlay không báo thành công. |
| Back trong editor / picker | Luôn hỏi Discard, kể cả chưa sửa gì. | So sánh bản nháp với cấu hình đã lưu; chỉ hỏi khi có thay đổi. Done ở picker giữ dữ liệu trong phiên editor; Back bỏ phần chọn chưa xác nhận. |
| Notch — Save / Reset / slider | Save đẩy về Customize; Reset đặt 15/10; cả bốn slider đều giới hạn 25. | Save vẫn ở màn Notch; Reset X/Y về 0. X: -50…50dp, Y: ± chiều cao status bar, height: 5dp…chiều cao status bar, width: 10…250dp. Giá trị mặc định width/height là 120/16dp. Chưa bật Emoji Battery thì lưu cấu hình và nhắc bật để hiển thị. |
| Battery Troll — Apply / Back | Apply hoặc thoát tạo thêm màn Customize trong stack. | Apply thành công hoặc xác nhận bỏ sửa trở về màn gọi; bỏ preview tạm, giữ cấu hình đang áp dụng khi hủy. |
| Sticker — Apply / Exit | Apply đưa về Home dù mở từ Search, Library hoặc editor khác. | Apply toàn bộ snapshot rồi trở về màn gọi. Exit cũng tôn trọng màn gọi và trạng thái dirty. |
| Sticker — chọn / adjustment | Dữ liệu dựa vào ID hình có thể nhập nhằng khi có nhiều bản cùng hình. | Mỗi bản có `instance_id`; chọn, chỉnh, xóa đúng bản đó. Giới hạn 5 bản, chỉnh kích thước 60–200dp, bước ±10px, xoay ±5°, di chuyển ±10px; sửa không ghi vào cấu hình đang chạy cho đến Apply chính. |
| Pet — bấm slot | Bấm toàn bộ slot đang có pet sẽ xóa pet. | Bấm thân slot mở đổi pet; chỉ nút remove mới xóa. Picker biết chính xác slot cần thay. |
| Pet — lưu slot | `StringSet` làm mất thứ tự và không cho dùng cùng một pet ở nhiều slot. | Lưu sáu slot có thứ tự bằng `pet_slot_ids`; giữ ô trống khi xóa, hỗ trợ nhiều slot cùng hình. Đọc dữ liệu cũ từ `selected_pets`/`applied_pets`; vẫn cập nhật khóa cũ để tương thích. Download/start thất bại khôi phục dữ liệu trước đó. |
| Pet — chọn / Enable / rời tab | Có thêm bước Apply ở trang Pet; Exit có thể tắt pet ngoài ý muốn. | Chọn/xác nhận rồi tải và chạy ngay. Enable độc lập; tắt giữ nguyên slot. Rời tab khi pet chưa bật/không có pet có xác nhận như source; nút Exit không tự xóa/tắt pet đang chạy. |
| Search | Chỉ tìm sticker; không có đường chọn battery/pet từ kết quả. | Tìm battery, sticker, pet theo tên/category; favorite đúng collection. Battery → Preview; sticker → editor; pet → tab Pet và xử lý tải/apply. Trạng thái catalog theo cả ba nguồn. |
| My Library | Back trong chế độ sửa thoát luôn màn; pet mở thêm Detail. | Back lần đầu rời chế độ sửa; Back tiếp mới đóng Library. Pet mở chooser với item được chọn sẵn; battery/emoji vào status editor; sticker vào sticker editor. Xóa favorite không xóa item đang áp dụng. |
| Gesture — switch / action sheet | Bật gesture có thể chưa cập nhật touch overlay; các lần bind thêm callback Back hỏi Discard. | Cập nhật gesture sau khi xác nhận quyền, không tự bật status bar đang tắt. Bỏ dialog Discard không tương ứng với dữ liệu đã lưu. Action sheet giữ một instance và đóng về đúng màn gọi. |
| Background photo | Chọn ảnh rồi dùng thẳng, thiếu Crop. | Chọn ảnh → Crop → Reset / Rotate / Apply → trả ảnh về bản nháp status bar. Back/hủy không thay ảnh cũ. Dùng `CropImageView` như source, dependency `com.vanniktech:android-image-cropper:4.6.0`; ảnh kết quả nằm trong bộ nhớ riêng của app. Activity và FileProvider chỉ phục vụ flow nội bộ. |
| Settings — How to use | Màn đã có nhưng nút vào vẫn `gone`; container cố định dễ che các dòng. | Hiện How to use, đổi container sang wrap content; nút Go to Settings mở Accessibility. Giữ artwork Figma đã export từ đợt sửa trước. |
| Settings — Restricted Apps | Một đường click mở Battery Optimization của hệ thống. | Mở đúng danh sách app bị hạn chế của Emoji Battery; Apply mới lưu lựa chọn. |
| Settings — Feedback | Hủy dialog có thể để lại route trung gian; lỗi mở mail vẫn sang màn kết quả. | Hủy trở về màn gọi; không mở màn kết quả khi launch email thất bại; giữ lựa chọn để thử lại. App chỉ mở trình soạn thư, không tự gửi email. |
| Charging — Set Wallpaper / Apply | Nút Set Wallpaper ở tab Animation có thể chạy Apply Charging; có thể báo thành công khi start monitor lỗi. | Set Wallpaper luôn mở chọn Home / Lock / Both. Chỉ báo Apply thành công sau khi yêu cầu start monitor được chấp nhận; không tạo lại success sheet mỗi lần refresh. Preview hiển thị item đã chọn. |
| Paywall | Trial switch có thể mất trạng thái khi tạo lại view. | Khôi phục lựa chọn switch từ ViewModel; giữ nguyên các gói và hành vi CTA hiện có. |

### Những thay đổi nền được giữ từ lượt trước

- Status bar dùng phiên bản nháp chung cho editor và các picker; preview đọc đúng draft, Apply mới ghi vào cấu hình đang chạy.
- Sticker lưu danh sách, vị trí, kích thước và góc xoay riêng cho từng bản; tải/đọc media xong mới Apply. Hủy tải không báo thành công.
- Quyền overlay/Accessibility được kiểm tra bằng trạng thái thật; khi quay lại Settings, hành động đang chờ được tiếp tục nếu màn gọi còn hợp lệ.
- Scroll danh sách dọc đi cùng trang; carousel ngang vẫn cuộn riêng. Khoảng trống cuối trang tính theo chiều cao footer thực tế.
- Splash, Language, Intro, Welcome Back, các nút legal và các route trạng thái được rà trong đợt này; giữ funnel, timing và SDK quảng cáo hiện có, không thêm hoặc đổi gói mua hàng.

### Phạm vi xác minh và phần còn khác app gốc

- Chỉ sửa source/XML/manifest/dependency và tài liệu. **Không chạy Gradle, build, unit test, instrumentation, cài APK hay thao tác thiết bị**, theo yêu cầu của người dùng.
- Kiểm tra tĩnh: XML đã chỉnh parse được, không trùng ID trong từng layout; tham chiếu `R.id` nội bộ từ các file Kotlin đã sửa có khai báo; các route khai báo được mapping, trừ `TEXT_NODE` có chủ ý. Kiểm tra diff không có lỗi whitespace khi tôn trọng CRLF của repository. Các kiểm tra này không chứng minh code đã compile hoặc UI đã khớp trên máy thật.
- Crop dùng cùng engine với source; layout XML native mới chưa được so ảnh/pixel trên thiết bị. Dependency/API được đọc từ source artifact 4.6.0 có sẵn trong Gradle cache; chưa resolve/build lại project.
- Premium/Billing/restore/unlock trả phí của project vẫn chưa được cấu hình đầy đủ. Không copy product ID/SDK key từ app gốc, không coi UI preview là entitlement thật. Các shortcut premium vốn đang ẩn được giữ nguyên.
- Project vẫn giữ các tính năng thêm như Charging, wallpaper và dashboard Customize; app gốc được cung cấp không có Activity Charging tương đương trong nhóm `com.battery.emoji.ui`, nên không tuyên bố các màn bổ sung này giống 100% source. Các trang onboarding vẫn theo Figma/funnel hiện có của project.

## Remote data and Apply runtime

See [REMOTE_DATA_README](docs/REMOTE_DATA_README.md) for the current Firebase keys, JSON filenames, implemented Apply flows and remaining data/device validation. This supersedes the historical catalog and outstanding-feature notes below. The latest runtime changes have not been built or tested, as requested.

# Emoji Battery Android

UI preview update: see [UI review, test-mode instructions and emulator comparison](docs/UI_REVIEW.md). The comparison gallery contains the current Figma exports and Pixel 8a captures. The older implementation notes below describe the initial baseline.

Android app for the Emoji Battery Figma flow. It uses one `MainActivity`, one `EmojiBatteryApplication`, AndroidX Fragments, XML layouts, the shared `codeBase` base classes, and the separate `monetization` module. Figma IDs and route mapping are listed in [the screen inventory](docs/FIGMA_SCREEN_INVENTORY.md).

## Architecture

- `activity_main.xml` hosts the Fragment container and includes `app_bottom_nav.xml`; the Activity connects tab actions and system permission flows.
- Splash, intro, welcome-back and paywall inflate XML layouts and inherit `BaseSplashFragment` or `BaseFragment` with a `BaseViewModel`. Their illustrations are exported Figma image layers in `app/src/main/res/drawable/`.
- Home, Pet, Customize, Status Bar, Charging, Sticker and Settings are Kotlin Fragments with Kotlin ViewModels inheriting `BaseViewModel`. Each supplied Figma frame ID routes to a concrete Fragment and frame-specific ViewModel in `ui/routes/FrameFragments.kt` and `FrameViewModels.kt`. Related states share XML feature layouts and switch the relevant XML sections by route ID; screen structures are declared in XML, with native repeated controls and adapters for palettes and choices.
- `codeBase` contains the shared lifecycle classes. ViewModel and ViewBinding reflection now resolve types through an intermediate feature base class. `monetization` remains a separate Android library module.
- `ScreenRouter` maps supplied Figma frame IDs to concrete frame Fragments. Each frame Fragment has its own ViewModel; feature families reuse XML sections and adapters for repeated catalogue controls.
- `AppContentRepository` serves bundled/cached JSON and can refresh from a GitHub raw URL supplied through Firebase Remote Config. `RemoteImageLoader` fetches HTTPS image URLs.
- `BatteryMonitorService`, `PetOverlayService` and `StatusBarOverlayService` implement charging notification/monitoring and the two user-triggered overlay services.
- App source code is Kotlin; screen and overlay views are defined in XML. English UI resources start in `app/src/main/res/values/strings.xml`.

## Figma coverage

The supplied Figma IDs were read with Figma MCP. The inventory records layer names, dimensions and current route mapping. The supplied frames have PNG reference exports in `docs/figma-reference/`, named `870-<node-id>.png`; additional nested content references are included. `870:2983` is a text layer, not a screen. The long 870:7292, 870:7701, 870:8110 and 870:8531 frames are also exported for reference. The charging preview background was exported from its Figma image layer as `charging_scene.png`.

The PNGs are Figma references. Screen layouts use exported art layers where available; `charging_scene.png`, intro art and the home banner are used by the UI. Home, pet selection and permission, gesture setup, charging preview, battery preview, paywall and onboarding have XML layouts shaped from their references. The updated comparison gallery records actual emulator captures and marks any image not verified against the latest APK. See docs/UI_REVIEW.md for validation and remaining limits.

## GitHub content workflow

The bundled fallback is `app/src/main/assets/app_content.json`. Host the same JSON schema and artwork at a public HTTPS URL, then set Firebase Remote Config string `emoji_battery_content_url` to the raw JSON URL (for example, a GitHub raw URL). Publish JSON and image assets first, then publish Remote Config. When Firebase is not initialized, the app uses the bundled/cached content. Valid remote content is cached; invalid JSON, unsupported schema or network failure leaves the previous content available. Refresh is limited by the Remote Config minimum fetch interval.

Keep item IDs stable because user favorites/selections refer to IDs. Increase `content_version` for catalog updates. Increase `schema_version` only when the app parser is updated to accept the new schema. Use HTTPS for remote media; empty URLs use the local emoji/color fallback.

| JSON field | Displayed in | Important item fields |
| --- | --- | --- |
| `themes[]` | Home theme catalogue, charging theme selection/preview | `id`, `name`, `category`, `emoji`, `color`, `free`, `wallpaper` |
| `stickers[]` | Home gallery, search, add/edit sticker, view more, detail, favorites | `id`, `name`, `category`, `emoji`, `free`, `image_url` |
| `pets[]` | Pet home/view more/detail and floating pet selection | `id`, `name`, `category`, `emoji`, `free`, `animation_url` |
| `emojis[]` | Battery/Status Bar emoji selectors and previews | `id`, `name`, `emoji`, `free` |
| `battery_styles[]` | Status Bar battery style selectors | `id`, `name`, `emoji`, `free` |
| `animations[]` | Charging animation selector | currently string names, e.g. `"Pulse"` |
| `wallpapers[]` | Charging wallpaper selector and preview | `id`, `name`, `color`, `image_url` |
| `links` | Settings legal/support actions | `privacy_policy`, `terms`, `support` |

`schema_version` 1 and 2 are currently accepted. Keep each array present even when it is empty. See `app_content.json` for a valid example. Images for `pets[].animation_url` are currently displayed as still images; the player/animation format is not implemented.

## Implemented and outstanding

Implemented: one MainActivity and one Application; splash routes into onboarding on first launch; four intro pages use Figma illustrations and persist completion; paywall plan selection and close navigation are wired; Figma reference PNGs and art layers are exported; feature Fragments/ViewModels inherit `codeBase`; feature UI is Kotlin with XML layouts; Home/Pet/Sticker/Customize/Charging/Status Bar/Settings interactions and route-specific screens are wired. Charging monitor notification permission, overlay permission flow, pet dragging, bundled JSON, last-known-good cache and Remote Config URL refresh are present.

Still outstanding: refine remaining feature states screen-by-screen from the Figma exports; compare screenshots on an emulator and tune typography/insets; connect Google Play Billing products and restore purchases (the paywall CTA reports that products are not configured); play animated pet/charging media; configure Firebase app credentials and Remote Config; review permission and charging flows on devices. This task intentionally did not build or run tests.

## Build

Build and device review were not run for this task, as requested. The Figma reference exports are ready for visual QA.
