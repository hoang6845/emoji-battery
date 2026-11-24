# Kịch bản ads EmojiBattery

Cập nhật 24/11/2026 theo flow đã xác nhận. Đây là mô tả source, chưa xác minh runtime trên thiết bị. Build đã dừng theo yêu cầu; không khẳng định compile thành công.

## Cấu hình và unit

- Asset `app/src/main/assets/ads_config.json` bật ads, dùng App ID `ca-app-pub-5698123608094343~8940119691` và production unit từ Sheet KEY ADMOB EMOJI.
- Mỗi placement có một candidate/unit. Không lặp cùng ID qua ba tầng high/medium/all. `inter_high_preload_only=false`.
- Native Language: `4940118754`; Language Reload: `5951441382`; Intro 2/OB1: `3756522883`; Full/OB3: `3028271282`; Intro sau full/OB4: `9402107949`.
- Collapse Home: `1130359549`; các collapse detail: `8817277876`; small-all: `9814217669`; list-data-custom: `7514311200`; preview: `7463386497`.
- Popup thường và unlock cùng dùng `3575066190`, giữ hai placement riêng để quản lý cache và bật/tắt.
- Inter Splash: `9897978141`; inter chuyển màn: `8584896470`; rewarded: `3564951190`; App-open: `8625706187`.
- Các số trên là phần sau `/` của publisher `ca-app-pub-5698123608094343`.
- Tắt toàn bộ banner chưa có unit; tắt `emoji_native_intro2`, `emoji_native_emoji_sticker`, `emoji_native_preview_data`. Không tự thay unit các placement thiếu.

## Onboarding

1. Splash: consent xong preload `emoji_native_language`; phiên chưa hoàn tất onboarding đi tiếp, không inter. Returning đã hoàn tất onboarding dùng `emoji_inter_splash`, rồi vào Home.
2. Language: hiển thị native Language đã preload. Chỉ khi vào Language mới bắt đầu load `emoji_native_language_reload`; đồng thời chuẩn bị `emoji_native_intro1` cho Intro 2.
3. Lần chọn ngôn ngữ đầu chuyển slot sang Language Reload; Done đi Intro đầu tiên.
4. Intro đầu tiên không có ads; preload các native còn lại trong config (trừ hai Language). Placement disabled không tạo request qua resolver.
5. Intro 2 hiển thị `emoji_native_intro1` (Native_OB1), layout medium.
6. Route INTRO_3 là trang full native `emoji_full_native` (Native full_OB3). Next luôn tồn tại kể cả offline/no-fill; close của creative cũng đi tới trang sau. Đây là native trong trang toàn màn hình, không phải SDK interstitial.
7. Route INTRO_5 hiển thị `emoji_native_intro3` (Native_OB4), layout medium; Get started vào Home. Không inter trong các bước onboarding hoặc từ onboarding sang Home.

Native onboarding không refill sau consume. Preload chưa có fill không chặn thao tác Done/Next.

## Home, collapse và Apply

- Các tab Home, Pet, Customize, Settings và Theme Category dùng `emoji_native_collab_home`.
- Expanded collapse là overlay ở đáy, che nội dung và bottom navigation của app. Bấm đóng collapse sẽ ẩn hẳn quảng cáo và giải phóng native đang hiển thị, không chuyển sang native small.
- Khoảng cách tối thiểu giữa hai lần mở rộng là 15 giây, tính từ lần expanded thực sự bind. Đổi tab/vào màn mới trước hạn chỉ hiển thị native nhỏ. Không có timer tự thay creative sau mỗi 15 giây. Các màn dùng chung đồng hồ trong Activity.
- Status Bar editor dùng `emoji_native_collab_custom`; Notch, Battery Troll, Sticker, Pet detail dùng key collapse tương ứng. Preview chỉ dùng một `emoji_native_preview` (`ca-app-pub-5698123608094343/7463386497`) neo cuối màn hình, layout `battery_native_medium`; nút Get/Apply giữ trong layout preview gốc, không chuyển vào dock collapse. Các list Status khác tiếp tục dùng `emoji_native_list_data_custom`.
- Dock đáy chứa Apply/Save hiện có; nút không cuộn theo nội dung, nằm trên ads và dịch xuống khi ads thu gọn. Preview Get/Apply có nút dock chuyển tiếp click tới nút nguồn. Dock chừa chỗ cho nội dung ở màn chi tiết.
- Ẩn nút `pet_apply` ở Pet On Screen/detail. Nút Done chọn pet trong luồng chọn slot vẫn giữ chức năng.

## Inter, reward, resume và dialog

- Từ Home/feature trở đi, `openScreen()` và Back qua Activity/`finishFeature()` tạo cơ hội `emoji_inter_in_app`, không còn allowlist đích cũ. Đổi tab qua bottom navigation không inter. Quay lại Language/onboarding không inter.
- Mỗi cơ hội inter tối đa 3 giây load + prepare; consent/premium/UI-test/config vẫn gate. Cache miss join/load một unit, không yêu cầu cùng unit qua nhiều floor. No-fill/timeout đi tiếp, callback muộn không được show. Chặn double tap và chặn inter 2 giây sau earn để không chồng rewarded.
- Unlock chỉ thành công khi rewarded callback earn; chỉ lưu RAM theo item, không cấp premium và không lưu unlock vĩnh viễn.
- App-open chỉ ở feature, sau Splash, không khi permission flow/dialog/ads đang bận. `use_inter_splash_on_resume=false`; các manager giữ cơ chế timeout/resume hiện có.
- Native small cached được nối vào discard, chọn gesture/options, restricted apps, color picker và Accessibility usage. `trackDialog()` chỉ chèn native khi call site chọn `nativeAds=true`; không tự thêm native vào feedback/download/category dialog. No-fill vẫn dùng dialog bình thường; không chờ native để mở dialog. Unlock giữ slot riêng. Dialog thoát/hướng dẫn dùng banner trong source mẫu vẫn không có banner vì chưa được cấp unit.
- Native dialog destroy theo detach cửa sổ; dialog app gate App-open. Các dialog hệ thống không bị chèn ads.
- MainActivity và ImageCropActivity ẩn cả status/navigation bars và áp lại khi lấy focus. Dialog ads/app được nối cũng ẩn system bars; người dùng vuốt có thể hiện thanh hệ thống tạm thời.

## Layout và remote

- Copy layout medium, medium-collapse, full và drawable phụ thuộc từ `C:/origami-world`; chuyển `ad_icon` thành `ad_app_icon`, nút collapse thành `iv_close` để dùng NativeAdCalculator hiện tại. Kích thước sdp/ssp của bản nguồn đổi sang dp/sp tương ứng. Layout nhỏ hiện có giữ nguyên.
- File mới trong module: `battery_native_medium.xml`, `battery_native_collapse.xml`, `battery_native_full.xml`. Collapse dùng callback host để ẩn host và giải phóng native khi đóng.
- Firebase Remote Config key `ads_config` là nguyên JSON. Theo flow IPTV: consent/SDK → fetchAndActivate → updateRemoteAdsConfig → preload. Fetch tối đa 10 giây; ưu tiên remote đã activate hợp lệ → cache hợp lệ → asset. Config fetch được áp ngay trước preload trong phiên hiện tại. Callback sau timeout không thay snapshot đang chạy; giá trị Firebase activate muộn có thể dùng ở launch sau. Minimum fetch interval là 3600 giây như IPTV.
- `configs.collapse_expand_interval_seconds` mặc định 15. Có thể dùng tham số Remote Config độc lập `collapse_expand_interval_seconds`; giá trị remote độc lập ưu tiên hơn JSON. Interval cập nhật sau fetch, giới hạn 0..3600 giây. Không publish console trong lần sửa này.
- Rollback: đặt `configs.enable=false` hoặc disable từng placement trong JSON remote; snapshot ads áp ở launch tiếp theo. Xóa cache/cài lại app dùng asset nếu chưa fetch được remote.

## Test device và kiểm tra

- Native_small_all Home: placement `emoji_native_small_all_home`, unit `ca-app-pub-5698123608094343/9814217669`, layout `battery_native_mini`. Chỉ các main tab có navbar: slot cố định 54dp dưới navbar, skeleton giữ cả khi loading/no-fill; collapse phủ lên slot. Load khi vào tab, hủy callback/cache và destroy creative khi rời màn; vào lại request mới, không refill ngay khi consume. Consent/global/premium và placement enable vẫn áp dụng.
- Cấu hình mới chỉ thêm ở asset local; remote/cached snapshot cần bổ sung cùng placement/item/unit để bật trên các cài đặt đang dùng remote. Disable placement để rollback từ launch kế tiếp.
- Kiểm tra bổ sung Native_small_all Home: JSON mapping và XML hợp lệ; `:app:compileDebugKotlin` thành công. Chưa kiểm tra trên thiết bị: đổi tab/vào lại, collapse bật/tắt/đóng, no-fill/offline, premium và rotation.

- Production IDs được giữ trong mọi build. Debug đăng ký hai AdMob test-device ID đang dùng ở Origami: `035788DA3CE5C276C88A6FD1BECDCF43`, `0A9504B766B45EFAD9C1221785B2709B` qua resource `admob_debug_test_device_ids`, trước SDK init. Release không đăng ký hai ID này. Đây là ID SDK cần đối chiếu Logcat trên thiết bị, không phải ad-unit hay UMP consent ID.
- Đã kiểm tra tĩnh JSON, placement/item/unit, XML mới và `git diff --check`. Không chạy thêm build/test theo yêu cầu. Build trước đó đã bị ngắt; không có kết luận compile.
- Chưa xác minh thiết bị: cold start mới/cũ, language reload trước/sau fill, ba ads onboarding/no-fill, collapse trước/sau 15 giây, đổi tab, scroll/Apply, Back/inter timeout, dialog/premium, resume, offline và rotation.
