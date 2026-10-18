# Home

自作アプリ群へのランチャー（Android）。Play ストアではなく GitHub Release から
APK を配布し、アプリ内で自動的に更新を確認します。

## できること

全20件の自作アプリを、情報カードと2列のランチャーで表示します。Androidアプリはインストール済みなら直接起動し、
未インストールなら配布ページを開きます。

| アプリ | 種別 | タップ時の挙動 |
|---|---|---|
| 株 | Android | `com.example.stockwidget` を直接起動 |
| タスク管理 | Web | https://masakasakasama.github.io/Task_management/ |
| フィットネス | Android | `com.masakasakasama.reps` を直接起動（未インストール時はFitness Releaseへ） |
| 英語ニュース | Web | https://english-news-app-eight.vercel.app/ |
| Daily News | Web | https://raw.githack.com/masakasakasama/Daily_check/main/index.html |
| 語学学習 | Web | https://masakasakasama.github.io/Language_learning/ |
| 割り勘 | Web | https://masakasakasama.github.io/warikan/ |
| 婚姻手続き | Web | https://masakasakasama.github.io/Marriage_procedure/ |
| 料理 | Web | https://masakasakasama.github.io/Cooking/ |
| カレンダー | Web | https://masakasakasama.github.io/Calender/ |
| 旅行計画 | Web | https://masakasakasama.github.io/Trip_Plan/ |
| Baby家計簿 | Web | https://masakasakasama.github.io/household_budget_management_forbaby/ |
| MF Dashboard | Web | https://masakasakasama.github.io/mf-dashboard/ |
| CPRE学習 | Web | https://cpre-english-study-masak.masakasakasama.chatgpt.site/（本人限定・認証あり） |
| CAPM学習 | Web | https://capm-baby.masakasakasama.chatgpt.site/ |
| 天気 | Android | `com.example.weather` を直接起動 |
| Galaxy 時計 | Android | `com.galaxyalarm` を直接起動 |
| Web Search | Android | `com.tatsuya.websearch` を直接起動 |
| AAOS Study | Android | `com.example.aaosstudy` を直接起動 |
| SEN | Web | https://140-245-85-166.sslip.io/operator |

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

## セキュリティ上の注意（移行中）

**旧署名鍵 `shared-debug.jks` とパスワードが過去から公開されている。漏洩済みとして扱う。** 単にリポジトリをPrivate化しても過去の流出は取り消せない。出所不明のHome APKはインストールしない。

現行HomeはStockアプリの署名保護されたProviderへアクセスするため旧鍵に依存しており、新鍵への切替はHome・Stock双方の移行設計が必要。異なる署名のAPKを既存アプリへそのまま上書きすることはできない。

まず旧インストールを壊さず公開APK配布先を分離するブリッジを導入した。更新確認は `Home-Downloads` （公開配布専用、存在・設定後）と従来の `Home` のリリースから新しい版を選択する。ユーザーの秘密のGitHubトークンを端末アプリに埋め込まない。

Private移行と署名鍵切替の必須条件は [Private移行手順](docs/PRIVATE_MIGRATION.md) を参照。
