# Home

自作アプリ群へのランチャー（Android）。Play ストアではなく GitHub Release から
APK を配布し、アプリ内で自動的に更新を確認します。

## できること

全18件のうち株・ニュース・運動を情報カードとして表示し、残りを
2列のランチャーで表示します。Androidアプリはインストール済みなら直接起動し、
未インストールなら配布ページを開きます。

| アプリ | 種別 | タップ時の挙動 |
|---|---|---|
| 株 | Android | `com.example.stockwidget` を直接起動 |
| タスク管理 | Web | https://masakasakasama.github.io/Task_management/ |
| フィットネス | Web | https://masakasakasama.github.io/Fitness/ |
| 英語ニュース | Web | https://english-news-app-eight.vercel.app/ |
| 語学学習 | Web | https://masakasakasama.github.io/Language_learning/ |
| 割り勘 | Web | https://masakasakasama.github.io/warikan/ |
| 婚姻手続き | Web | https://masakasakasama.github.io/Marriage_procedure/ |
| 料理 | Web | https://masakasakasama.github.io/Cooking/ |
| カレンダー | Web | https://masakasakasama.github.io/Calender/ |
| 旅行計画 | Web | https://masakasakasama.github.io/Trip_Plan/ |
| Baby家計簿 | Web | https://masakasakasama.github.io/household_budget_management_forbaby/ |
| CPRE学習 | Web | https://cpre-english-study-masak.masakasakasama.chatgpt.site/（本人限定・認証あり） |
| CAPM学習 | Web | https://capm-baby.masakasakasama.chatgpt.site/ |
| 天気 | Android | `com.example.weather` を直接起動 |
| Galaxy 時計 | Android | `com.galaxyalarm` を直接起動 |
| Web Search | Android | `com.tatsuya.websearch` を直接起動 |
| AAOS Study | Android | `com.example.aaosstudy` を直接起動 |
| SEN | Android | `com.masakasakasama.sen` を直接起動 |

さらに起動時に `masakasakasama/Home` の最新 Release を確認し、新しければ
画面上部に更新カードを表示します。GitHub REST APIは使わず、公開Releaseの
リダイレクトから最新版を判定するため、API rate limitの影響を受けません。
更新カードをタップするとダウンロードからOSのインストール確認まで進みます。

## インストール手順（初回のみ手動）

1. このリポジトリの **Releases** ページから最新の `Home-x.y.z.apk` を
   スマホでダウンロード
2. インストール時に「不明なアプリのインストール」を許可
3. 以降のバージョンアップはアプリ内バナーから自動

## リリースの出し方

`main` に push するだけです。バージョンは手で書く必要はありません。

- GitHub Actions が commit 数からバージョンを自動採番
  （`versionCode = git rev-list --count HEAD`、`versionName = 1.0.<同値>`）
- release APK をビルドし、`v<versionCode>` タグで Release を自動発行
- アプリは起動時に最新 Release を確認し、番号が大きければ更新バナーを表示

## アプリの追加・変更

`app/src/main/java/com/masakasakasama/home/data/Config.kt` の `DEFAULTS` を編集します。

- Web アプリ: `Tile(..., TileKind.WEB, url = "https://...")`
- インストール済みアプリ: `Tile(..., TileKind.APP, pkg = "applicationId")`
  - 新しくインストール済みアプリを追加する場合は、
    `AndroidManifest.xml` の `<queries>` にもその applicationId を
    `<package android:name="..."/>` で追加してください（Android 11+ の
    パッケージ可視性のため）

## 署名について

`home-release.jks`（パスワードはリポジトリ内固定）を意図的にコミットしています。
個人のサイドロード用ランチャーで、CI ビルド間で署名を一定に保つことで
アプリの自己更新（上書きインストール）を成立させるためです。公開配布する
場合は署名鍵を Secrets に移してください。
