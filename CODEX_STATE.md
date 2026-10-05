# CODEX_STATE

Status: blocked
Goal: 最新の20アプリ構成と自己更新ランチャーの整合性を維持する。

## Done
- READMEの18件表記を20件へ更新。Daily News/MFを追加、REPSとSENの現行起動先を反映し署名ファイル名を修正。
- 最新mainのREADME、Config/AppCatalog、更新検証テスト、CIを確認。

- Release build前に :app:testReleaseUnitTest を必須実行するCI stepを追加。失敗時は同じjobのAPK build/公開へ進まない（continue-on-error等は追加なし）。

## Current
- SDK 34/35/36とJDK 17をクラウドworkspaceへ導入し、ローカルAPK/単体テストを検証。アプリruntimeは変更なし。

## Next
- GalaxyでREPSの直接起動、SEN Web遷移、HomeのAPK更新を確認。結果が得られるまで実機合格としない。
- ReleaseClientのRelease unit testをCI build前に実行する構成は実装・ローカル検証済み。今回commitのActions/Release結果は、ローカル成功と区別して確認する。

## Blockers
- Galaxy実機なし。サイドロード/PackageManager/署名権限はPCビルドだけでは検証できない。

## Verification
- ANDROID_HOME=/workspace/android-sdk JAVA_HOME=/workspace/jdk17 ./gradlew :app:testReleaseUnitTest --no-daemon: BUILD SUCCESSFUL; JUnit2/2, failures0/errors0
- Release workflowのテストstepはAPK build/公開前、通常失敗を許容しない。同じversion環境変数を保持
- git diff --check passed; app runtime/署名/版数方式/承認設定は変更なし。今回CI完了・実機受入は未確認
- ANDROID_HOME=/workspace/android-sdk JAVA_HOME=/workspace/jdk17 ./gradlew :app:testDebugUnitTest :app:assembleDebug: passed
- JUnit 2/2 passed、SDK/JDKのproxy CAを保持してビルド
- Config DEFAULTS=20件、READMEアプリ表=20件、git diff --check passed

Updated at: 2026-10-05T03:33:59.308330+00:00
