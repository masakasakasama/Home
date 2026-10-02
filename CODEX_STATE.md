# CODEX_STATE

Status: blocked
Goal: 最新の20アプリ構成と自己更新ランチャーの整合性を維持する。

## Done
- READMEの18件表記を20件へ更新。Daily News/MFを追加、REPSとSENの現行起動先を反映し署名ファイル名を修正。
- 最新mainのREADME、Config/AppCatalog、更新検証テスト、CIを確認。

## Current
- SDK 34/35/36とJDK 17をクラウドworkspaceへ導入し、ローカルAPK/単体テストを検証。アプリruntimeは変更なし。

## Next
- GalaxyでREPSの直接起動、SEN Web遷移、HomeのAPK更新を確認。結果が得られるまで実機合格としない。
- GitHub ReleaseClientの検証テストをCI release build前にも実行する構成を検討。

## Blockers
- Galaxy実機なし。サイドロード/PackageManager/署名権限はPCビルドだけでは検証できない。

## Verification
- ANDROID_HOME=/workspace/android-sdk JAVA_HOME=/workspace/jdk17 ./gradlew :app:testDebugUnitTest :app:assembleDebug: passed
- JUnit 2/2 passed、SDK/JDKのproxy CAを保持してビルド
- Config DEFAULTS=20件、READMEアプリ表=20件、git diff --check passed

Updated at: 2026-10-02T10:51:40.578855+00:00
