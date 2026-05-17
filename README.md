# Home

自作アプリ群へのランチャー（Android）。Play ストアではなく GitHub Release から
APK を配布し、アプリ内で自動的に更新を確認します。

## できること

ホーム画面に 6 つの自作アプリをタイル表示し、タップで開きます。

| アプリ | 種別 | タップ時の挙動 |
|---|---|---|
| フィットネス | Web | https://masakasakasama.github.io/Fitness/ をブラウザで開く |
| 英語ニュース | Web | https://masakasakasama.github.io/english-news-app/ |
| 株 | インストール済みアプリ | `com.masakasakasama.stock` を直接起動 |
| 語学学習 | Web | https://masakasakasama.github.io/Language_learning/ |
| 割り勘 | Web | https://masakasakasama.github.io/warikan/ |
| タスク管理 | Web | https://masakasakasama.github.io/Task_management/ |

さらに起動時に `masakasakasama/Home` の最新 Release を確認し、新しければ
画面上部に更新バナーを表示してワンタップ更新します。

## インストール手順（初回のみ手動）

1. このリポジトリの **Releases** ページから最新の `Home-x.y.z.apk` を
   スマホでダウンロード
2. インストール時に「不明なアプリのインストール」を許可
3. 以降のバージョンアップはアプリ内バナーから自動

## リリースの出し方

1. `app/build.gradle.kts` の `versionCode`（整数）と `versionName` を上げる
   - 更新判定は `versionCode` で行います。必ず増やしてください
2. `main` に push
3. GitHub Actions が release APK をビルドし、`v<versionCode>` タグで
   Release を自動発行（`.github/workflows/release.yml`）

## アプリの追加・変更

`app/src/main/java/com/masakasakasama/home/data/AppCatalog.kt` を編集します。

- Web アプリ: `Target.Web("https://...")`
- インストール済みアプリ: `Target.InstalledApp("applicationId")`
  - 新しくインストール済みアプリを追加する場合は、
    `AndroidManifest.xml` の `<queries>` にもその applicationId を
    `<package android:name="..."/>` で追加してください（Android 11+ の
    パッケージ可視性のため）

## 署名について

`home-release.jks`（パスワードはリポジトリ内固定）を意図的にコミットしています。
個人のサイドロード用ランチャーで、CI ビルド間で署名を一定に保つことで
アプリの自己更新（上書きインストール）を成立させるためです。公開配布する
場合は署名鍵を Secrets に移してください。
