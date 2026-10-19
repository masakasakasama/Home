# 個人用Homeの更新情報

## 利用方法

Home（`com.masakasakasama.home`）のトップにある「更新情報　Gmail・Brain」を開く

### Gmailの更新

個人画面の「個人用Gmailに接続・更新」からGoogle OAuthで認可し、未読件数と取得時刻を端末内に保存します。`gmail.metadata` の読み取り専用権限で `users.labels.get(UNREAD)` の `messagesUnread` のみ取得します。本文・件名・差出人は取得しません。GmailデータとOAuthトークンは共有Tatsu Home/Brain Backendへ送信しません。OAuthトークンはアプリの保存領域やバックアップへ書き込みません。

OS通知アクセス権限やNotificationListenerServiceは使いません。旧通知件数とハッシュはバックアップで保持されますが、新しい未読件数とは別のデータです。ネットワーク・認可失敗時は保存済みの未読件数を残します。

有効化にはGoogle Cloud ConsoleでGmail APIと同意画面を設定し、Android OAuthクライアントに `com.masakasakasama.home` と新署名のSHA-1 `71:3B:19:01:82:B7:A2:8A:C9:2E:5D:B9:84:08:E8:E0:9A:5A:76:E7` を登録してください。個人用アカウントをテストユーザー/適切な公開範囲に設定し、実機で認可・取消・再認可・オフライン表示を確認する必要があります（未実施）。

### Brainのニュース

Homeの「Brain連携の認証設定」に既存のTatsu Home Backendのowner tokenを一度入力し「保存して取得」をタップ。トークンはこのAndroidアプリのKeystoreで暗号化して保存する。保存後はHomeにだけ重要ニュースのスコア・ダイジェスト・元記事URLを表示

公開ニュース専用のデータ転送路 `https://ai-assistant-handmade.vercel.app/api/news` を読み取り利用。これは新しいクラウドサービスではなく既存Backendの認証済みAPI。家族共有のTatsu Home側のWeb/Android UI・通知は廃止し、ニュースを表示しない。公開ニュースのみ共有Backendを通過し、Gmail関連データは通過しない

PC上のBrainには、従来どおり `OPENAI_API_KEY` と、送信認証用の `TATSU_HOME_AUTH_TOKEN`（既存Backendのowner token）をWindowsユーザー環境変数として設定する。Brainの最新コードに更新し、`TatsuDotsLuna` のスケジュールタスクを使う

初回のRSS取り込みはベースラインのみで、AI分析の新規バッチは作らない。RSSに新着があった場合のみ分析・送信されるため、一覧がすぐ空でも必ずしも障害ではない

### 実装範囲

- Homeの更新画面ではアプリ起動・更新操作時にBrainニュースを取得
- Gmailは個人画面のOAuth認可・更新操作で未読件数を取得（OS通知サービス不要）
- 新規ニュースのHome自身によるバックグラウンド自動通知は未導入。従来のBrain監視は1時間間隔
- 自動更新は従来のHome GitHub Release配布を利用
- ネットワーク・実機に依存する動作はGalaxyでの手動受入試験が別途必要
