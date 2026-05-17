# Home

自作アプリ群へのランチャー（Android）。Play ストアではなく GitHub Release から
APK を配布し、アプリ内で自動的に更新を確認します。

## できること

- ホーム画面に 6 つの自作アプリをタイル表示
  - フィットネス / 英語ニュース / 株 / 語学学習 / 割り勘 / タスク管理
- タイルをタップすると:
  - インストール済み（`packageName` 設定済み）なら、そのアプリを起動
  - 未インストールなら、その repo の最新 GitHub Release から APK を取得して
    インストール（Home が全アプリのインストール／更新ハブになる）
- 起動時に `masakasakasama/Home` の最新 Release を確認し、新しければ
  画面上部に更新バナーを表示してワンタップ更新

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

## サブアプリを起動できるようにする

各サブアプリの applicationId が分かったら
`app/src/main/java/com/masakasakasama/home/data/AppCatalog.kt` の該当行に
`packageName = "..."` を追加してください。設定するとインストール済みの場合に
そのアプリを直接起動します（未設定の間は常に APK 取得フローになります）。

各サブアプリ側にも、APK を asset として添付する GitHub Release を作る
ワークフロー（このリポジトリの `release.yml` と同様）を用意すると、
Home から直接インストール／更新できます。

## 署名について

`home-release.jks`（パスワードはリポジトリ内固定）を意図的にコミットしています。
個人のサイドロード用ランチャーで、CI ビルド間で署名を一定に保つことで
アプリの自己更新（上書きインストール）を成立させるためです。公開配布する
場合は署名鍵を Secrets に移してください。
