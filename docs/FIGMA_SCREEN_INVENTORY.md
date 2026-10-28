# Figma screen inventory

Read from Figma MCP (`Figma practice`, Design - UI Kit page). Node bounds are Figma pixels. The page navigation endpoint timed out once, but `get_nodes_info` returned the node metadata for the complete provided ID set.

The destination column names the shared feature family. At runtime, `ScreenRouter` creates the dedicated frame Fragment for that ID; every frame Fragment has a distinct ViewModel extending its feature ViewModel and ultimately `BaseViewModel`. Repeated UI patterns share XML layouts and XML-backed RecyclerView items.

| Figma ID | Figma layer name | Kind / bounds | App destination |
| --- | --- | --- | --- |
| `870:548` | Home | Screen 412×916 | HomeFragment / Home |
| `870:699` | Pet on Screen | Screen 412×916 | PetFragment / Pet catalogue |
| `870:921` | Pet on Screen | Screen 412×916 | PetFragment / Pet selected state |
| `870:1172` | Pet on Screen | Screen 412×916 | PetFragment / Pet state |
| `870:1379` | Customize_Gesture | Screen 412×916 | CustomizeFragment / Gesture settings |
| `870:1478` | Customize_Gesture | Screen 412×916 | CustomizeFragment / Gesture settings state |
| `870:1536` | Customize_Notch Setup | Screen 412×916 | CustomizeFragment / Notch setup |
| `870:1704` | Customize_Exit Notch Setup | Screen 412×916 | CustomizeFragment / Notch exit confirmation |
| `870:1886` | Pet_View More | Screen 412×916 | PetFragment / Pet catalogue expanded |
| `870:2001` | Pet_View More | Screen 412×916 | PetFragment / Pet selected state |
| `870:2117` | Pet_Applying | Screen 412×916 | PetFragment / Applying result |
| `870:2244` | Pet_Rating | Screen 412×916 | PetFragment / Rating |
| `870:2376` | Pet_Sort Down | Screen 412×916 | PetFragment / Sort options |
| `870:2544` | Pet_Exit | Screen 412×916 | PetFragment / Exit confirmation |
| `870:2673` | Customize_Gesture_Select Action | Screen 412×916 | CustomizeFragment / Gesture action picker |
| `870:2870` | Customize_Gesture • Select Action | Screen 412×916 | CustomizeFragment / Selected action state |
| `870:2983` | Text | Text layer 290×21 | Not a screen; excluded from routing |
| `870:2984` | Battery Charging _Wall Paper View more | Screen 412×916 | ChargingFragment / Wallpaper catalogue expanded |
| `870:3034` | Battery Charging _Animation View more | Screen 412×916 | ChargingFragment / Animation catalogue expanded |
| `870:3106` | Battery Charging _Animation | Screen 412×916 | ChargingFragment / Animation selector |
| `870:3188` | Battery Charging _Wall Paper | Screen 412×916 | ChargingFragment / Wallpaper selector |
| `870:3271` | Battery Charging _Apply sucess | Screen 412×916 | ChargingFragment / Apply success |
| `870:3362` | Add Sticker | Screen 412×916 | StickerFragment / Add stickers |
| `870:3453` | Add Sticker | Screen 412×916 | StickerFragment / Add sticker selection state |
| `870:3551` | Add Sticker_Edit | Screen 412×916 | StickerFragment / Sticker editor |
| `870:3717` | Favourite | Screen 412×916 | StickerFragment / Favorites |
| `870:3741` | Search | Screen 412×916 | StickerFragment / Search |
| `870:3821` | Favourite | Screen 412×916 | StickerFragment / Favorites state |
| `870:3866` | How to use | Screen 412×916 | SettingsFragment / Help |
| `870:3916` | Favourite_Delete | Screen 412×916 | StickerFragment / Favorite deletion confirmation |
| `870:4044` | Setting | Screen 412×916 | SettingsFragment / Settings |
| `870:4105` | Setting_Restricted Apps | Screen 412×916 | SettingsFragment / Restricted apps |
| `870:4200` | Setting_Feedback Reason | Screen 412×916 | SettingsFragment / Feedback reason |
| `870:4308` | Customize | Screen 412×916 | CustomizeFragment / Customize dashboard |
| `870:4387` | Battery Charging _Preview | Screen 412×922 | ChargingFragment / Charging preview |
| `870:4477` | Customize_Battery Troll | Screen 412×916 | CustomizeFragment / Battery Troll |
| `870:4522` | Customize_Battery Troll Setup | Screen 412×916 | CustomizeFragment / Troll setup |
| `870:4615` | Customize_Status Bar | Screen 412×916 | StatusBarFragment / Status Bar editor |
| `870:5046` | Customize_Status Bar_Scroll | Screen 412×916 | StatusBarFragment / Scrolled Status Bar editor state |
| `870:5477` | Customize_Status Bar_Scroll | Screen 412×916 | StatusBarFragment / Scrolled Status Bar editor state |
| `870:5908` | Customize_Status Bar_Scroll | Screen 412×916 | StatusBarFragment / Scrolled Status Bar editor state |
| `870:6344` | Customize_Status Bar_Scroll | Screen 412×916 | StatusBarFragment / Scrolled Status Bar editor state |
| `870:6780` | Customize_Status Bar_Scroll | Screen 412×916 | StatusBarFragment / Status Bar settings state |
| `870:6856` | Customize_Status Bar_Scroll | Screen 412×916 | StatusBarFragment / Scrolled Status Bar editor state |
| `870:7292` | Frame 48098009 | Nested content frame 375×3250 | StatusBarFragment / Long editor content, not a destination |
| `870:7701` | Frame 48098010 | Nested content frame 375×3250 | StatusBarFragment / Long editor content, not a destination |
| `870:8110` | Frame 48097980 | Nested content frame 375×916 | StatusBarFragment / Editor content, not a destination |
| `870:8531` | Frame 48097890 | Nested content frame 375×878 | CustomizeFragment / Troll setup content, not a destination |
| `870:8594` | Customize_Battery Troll Setup | Screen 412×916 | CustomizeFragment / Troll setup state |
| `870:8666` | Customize_Edit Percentage | Screen 412×916 | CustomizeFragment / Edit fake percentage |
| `870:8751` | Customize_Exit Battery Troll Setup | Screen 412×916 | CustomizeFragment / Troll setup exit confirmation |
| `870:8833` | Sticker_View More | Screen 412×916 | StickerFragment / Sticker catalogue expanded |
| `870:8952` | Battey Preview | Screen 412×916 | ChargingFragment / Battery preview |
| `870:9237` | Sticker_Exit | Screen 412×916 | StickerFragment / Exit confirmation |
| `870:9370` | Customize_Status Bar_View more emoji | Screen 412×916 | StatusBarFragment / Emoji selector expanded |
| `870:9488` | Customize_Status Bar_View more battery | Screen 412×916 | StatusBarFragment / Battery selector |
| `870:9606` | Customize_Status Bar_View more battery | Screen 412×916 | StatusBarFragment / Emotion selector |
| `870:9695` | Customize_Status Bar_View more battery | Screen 412×916 | StatusBarFragment / Animation selector |
| `870:9734` | Customize_Status Bar_View more battery | Screen 412×916 | StatusBarFragment / Background selector |
| `870:8971` | Welcome Back | Screen 412×916 | WelcomeBackFragment / Welcome back |
| `870:8974` | Splash | Screen 412×916 | SplashFragment / Initial splash |
| `870:8979` | Splash | Screen 412×916 | SplashFragment / Splash loading state |
| `870:8984` | Splash | Screen 412×916 | SplashFragment / Splash ready state |
| `870:8989` | Paywall | Screen 412×916 | PaywallFragment / Premium offer |
| `870:9052` | Intro 3 | Screen 412×916 | IntroFragment / Intro page 3 |
| `870:9073` | Intro 1 | Screen 412×916 | IntroFragment / Intro page 1 |
| `870:9087` | Intro 2 | Screen 412×916 | IntroFragment / Intro page 2 |
| `870:9108` | Intro 5 | Screen 412×916 | IntroFragment / Final intro page |
| `870:9122` | Language | Screen 412×1512 | SettingsFragment / Language |

## Feature flows

- Home → Search, Favorites, Add Sticker, Pet, Customize, Charging preview, Settings.
- Pet → More/details → select position and movement → grant overlay permission → apply → result/rating. `PetOverlayService` is user initiated and draggable.
- Customize → Gesture → choose action; Notch setup; Battery Troll → setup → edit fake percentage; Status Bar → emoji/battery/emotion/animation/background pickers and date settings.
- Charging preview → animation/wallpaper selector → apply success. The charging monitor observes battery/power state and starts the configured charging experience.
- Stickers → search/detail/favorite; select up to five → editor → apply. Favorites and deletion are stored locally.
- Settings → restricted apps, feedback reason/sent, help, language and Premium placeholder.

The provided ID list covers these flows but omits some secondary destinations above; those secondary IDs use app-local route constants. Local reference exports are in `docs/figma-reference/870-<node-id>.png`. The intro and paywall art exports used by the app are in `app/src/main/res/drawable/`.
