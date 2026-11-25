from pathlib import Path
p=Path('app/src/main/java/com/hd/emojibattery/ads/BatteryAds.kt')
s=p.read_text(); s=s.replace('class BatteryAds(private val activity: MainActivity)', 'class BatteryAds(private val activity: androidx.appcompat.app.AppCompatActivity)'); s=s.replace('activity.openScreen(', '(activity as MainActivity).openScreen('); p.write_text(s)
p=Path('app/src/main/java/com/hd/emojibattery/ads/UnlockItemSheet.kt'); s=p.read_text().replace('private val activity: MainActivity', 'private val activity: androidx.appcompat.app.AppCompatActivity'); p.write_text(s)
p=Path('app/src/main/java/com/hd/emojibattery/ui/onboarding/LanguagePickerFragment.kt'); s=p.read_text().replace('import com.hd.emojibattery.MainActivity',''); s=s.replace('(requireActivity() as MainActivity)', '(requireActivity() as LanguageActivity)'); s=s.replace('if (parentFragmentManager.backStackEntryCount == 0)', 'if ((requireActivity() as LanguageActivity).fromOnboarding)'); a=s.index('            // Update resources'); b=s.index('\n        }',a); s=s[:a]+'''            binding.languageContinue.isEnabled = false
            (requireActivity() as LanguageActivity).completeSelection()'''+s[b:]; p.write_text(s)
p=Path('app/src/main/java/com/hd/emojibattery/MainActivity.kt'); s=p.read_text(); anchor='    private val overlayPermission ='; i=s.index(anchor); s=s[:i]+'''    private val languagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (result.data?.getBooleanExtra("go_to_intro", false) == true) {
                openScreenDirect(ScreenIds.INTRO_1, false)
            } else {
                val current = supportFragmentManager.findFragmentById(R.id.main_container)
                val screen = current?.arguments?.getString("screen_id") ?: ScreenIds.SETTINGS
                ScreenRouter.open(this, R.id.main_container, screen, Bundle(current?.arguments ?: Bundle()), false)
            }
        } else if (result.data?.getBooleanExtra("exit_onboarding", false) == true) {
            finish()
        }
    }

    fun launchLanguage() {
        languagePicker.launch(Intent(this, com.hd.emojibattery.ui.onboarding.LanguageActivity::class.java)
            .putExtra("from_onboarding", !getSharedPreferences("emoji_battery", MODE_PRIVATE).getBoolean("intro_complete", false)))
    }

'''+s[i:]; p.write_text(s)
p=Path('app/src/main/java/com/hd/emojibattery/ui/navigation/ScreenRouter.kt'); s=p.read_text(); anchor='        if (!canNavigate(activity)) return\n        if (activity.supportFragmentManager.findFragmentById(container)?.tag'; s=s.replace(anchor,'''        if (!canNavigate(activity)) return
        if (screen == ScreenIds.LANGUAGE && activity is com.hd.emojibattery.MainActivity) {
            blockNavigation(activity)
            activity.launchLanguage()
            return
        }
        if (activity.supportFragmentManager.findFragmentById(container)?.tag'''); p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml'); s=p.read_text().replace('        <activity android:name=".ui.statusbar.ImageCropActivity"', '        <activity android:name=".ui.onboarding.LanguageActivity" android:exported="false" android:configChanges="locale|layoutDirection" />\n        <activity android:name=".ui.statusbar.ImageCropActivity"'); p.write_text(s)
