# Chuẩn bị gọi ads trong EmojiBattery

Đối chiếu working tree IPTV ngày 20/11/2026, không chỉnh sửa repo nguồn.

## Kết quả đối chiếu

Trước lần cập nhật này, mọi file ngoài build/cache của `monetization` đều trùng IPTV, bao gồm Gradle, resources và unit tests. Đã có session resolver, opt-in 2 floor, shared in-flight requests, retry, SplashAdGate, resume opportunity và App-open cache.

`codeBase` chỉ khác bốn file và giữ bản EmojiBattery:

- `BaseFragment.kt`: resolve ViewModel qua nhiều tầng kế thừa.
- `BindingReflex.kt`: tìm ViewBinding qua chuỗi superclass.
- `BaseFirebaseRemoteConfig.kt`: fallback khi Firebase chưa khởi tạo.
- `AppRemoteConfig.kt`: không dùng token mặc định của app tham chiếu. Các hàm parse ads config đã tương đương IPTV.

## Những việc phải nối trước request đầu tiên

1. Application hiện chỉ gọi `AdsManager.initialize(this, PremiumManager.INSTANCE)`. Tạo constants riêng kế thừa `AbstractAdsConstants`, gọi `configure(...)` một lần sau initialize. Chụp `isFirstSession` từ trạng thái onboarding hiện có; không đổi cohort khi tới Home. `ADMOB_APP_ID` là app ID; các ID còn lại phải khớp placement trong config.
2. Khai báo AdMob application ID của EmojiBattery trong manifest và tạo `app/src/main/assets/ads_config.json` riêng. Hiện chưa có file này. Không sao chép production units, credentials hay layout names của IPTV.
3. Tại launch Activity, gọi `gatherConsent`, đợi SDK ready và kiểm tra `canRequestAds` trước preload. Sau fetch, áp config bằng `updateRemoteAdsConfig(AppRemoteConfig.getRemoteAdsConfig())` và các switch/cooldown sản phẩm cần. Không coi initialize là SDK đã sẵn sàng.
4. Nối premium entitlement thực tế. Load/show phải bỏ qua premium. Chọn preload theo route của EmojiBattery; không tự dùng danh sách màn IPTV.
5. Splash cần deadline và continuation chỉ chạy một lần; dùng `SplashAdGate` nếu phải đợi nhiều placement. `BaseSplashFragment` hiện đợi mạng để fetch, nên riêng loading_time chưa bảo đảm offline launch hữu hạn.
6. Chỉ gọi `markSplashCompleted()` khi launch route đã hoàn tất. Chọn resume policy qua `configs.use_inter_splash_on_resume`, preload bằng `preloadResumeAd(activity)`. Giữ `is_show_ad_open` làm switch tổng.

Chưa nối các bước này vào UI; đây là chuẩn bị API/module trước khi triển khai placement cụ thể.

## Các API nên dùng

| Nhu cầu | API |
|---|---|
| Preload inter gắn với view Fragment | `Fragment.loadInterstitialAdForView(key)` (mới) |
| Show inter đã preload, guard vòng đời | `Fragment.showInterstitialAdGuarded(key, shouldShow, onAdClosed)` (mới) |
| Preload common inter | `AdsManager.preloadCommonInterstitialAd(activity)` |
| Show common inter | `AdsManager.showCommonInterstitialAd(...)` |
| Preload native màn kế tiếp | `preloadNativeManagement.load(...)` / `loadWithWaterfall(...)` |
| Bind native trong BaseFragment | `showPreloadedNativeOrLoad(...)` / `loadSingleNative(...)` |
| Banner | `preloadBannerManagement.loadAdaptiveBanner(...)`, `showBanner(...)` |
| Rewarded | `preloadRewardedManagement.load(...)`, `show(...)` |
| Debug SDK/mediation | `AdsManager.openAdInspector()` sau SDK ready, debug app |

Import helpers từ `tpt.dev.monetization.ads.preload`. Gọi preload từ `onViewCreated` trở đi. Owner giới hạn retry; request ban đầu và callback load vẫn có thể hoàn tất sau khi view bị hủy, nên caller phải guard UI trong callback. Callback load inter chỉ báo terminal; dùng `isLoaded(key)` để biết có fill.

```kotlin
// Khi vào màn, sau consent/config/SDK gates:
loadInterstitialAdForView(placementKey)

// Tại cơ hội chuyển route. opportunityStillOpen do màn quản lý.
showInterstitialAdGuarded(
    adKey = placementKey,
    shouldShow = { opportunityStillOpen },
    onAdClosed = {
        // Callback có thể đến khi màn đã rời: kiểm tra owner trước navigation.
        if (viewLifecycleOwnerLiveData.value?.lifecycle?.currentState
                ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true &&
            opportunityStillOpen) {
            opportunityStillOpen = false
            navigateNext()
        }
    }
)
```

Ví dụ trên dùng các biến/hàm do màn định nghĩa. Helper giữ owner của view tại thời điểm gọi, kiểm tra RESUMED và Activity trước show, kể cả sau prepare delay. Cache miss tiếp tục callback; không load rồi show muộn. API guarded không tự refill: preload riêng cho cơ hội sau. Helper không tự khóa double tap hay đảm bảo navigation once; màn phải đóng opportunity khi rời route và quản lý continuation.

Giữ cùng placement key giữa preload/show. `loadWithWaterfall(adKeys)` rải load giữa các placement; thứ tự floor nằm ở `ordered_priority_ads`. Native dùng wrapper để quản lý ownership; không giữ raw ad qua nhiều view. Reward chỉ cấp trong callback earn, không cấp khi đóng ad.

## Xác minh thiết bị còn cần

Kiểm tra local ngày 20/11/2026: `:monetization:testDebugUnitTest` pass 36 tests / 7 suites (0 failure/error/skipped), `:codeBase:compileDebugKotlin` và `:app:assembleDebug` thành công. `git diff --check` không có lỗi whitespace. Đây là tests hiện có của module; chưa có instrumentation test riêng cho hai helper Fragment mới.

Kiểm tra first/returning launch, offline/no-fill, premium, rời/rotate view trong lúc load, double tap, background trước prepare delay, resume cache hit/miss và reward earn/close. Build/unit tests không chứng minh fill, impression hay mediation trên thiết bị. Chưa thay đổi Firebase/AdMob console.
