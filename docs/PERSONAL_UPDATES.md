# 個人用Homeの更新情報

## 利用方法

Home（`com.masakasakasama.home`）のトップにある「更新情報　Gmail・Brain」を開く

### Gmailの更新

「通知アクセス設定」から「Home Gmail更新の件数」を許可する。アクセス権限は利用者による明示的な手動承認が必要。許可後にAndroidのGmailアプリが投稿した通知のみを数え、今日の通知件数と最終通知時刻を端末内に保持

重要な制約:
- Gmail本文・件名・差出人・連絡先は保存も送信もしない
- 過去のGmail受信履歴、ブラウザー版Gmail、既読/未読状態は取得しない
- Gmailの通知が無効・まとめられている場合はメール通数と通知件数は一致しない
- OSの通知アクセス権限は広いが、このアプリの実装はGmailパッケージだけをフィルタし、内容を保存しない
- Gmailの通知アクセスは端末の設定からいつでも取り消せる

### Brainのニュース

Homeの「Brain連携の認証設定」に既存のTatsu Home Backendのowner tokenを一度入力し「保存して取得」をタップ。トークンはこのAndroidアプリのKeystoreで暗号化して保存する。保存後はHomeにだけ重要ニュースのスコア・ダイジェスト・元記事URLを表示

公開ニュース専用のデータ転送路 `https://ai-assistant-handmade.vercel.app/api/news` を読み取り利用。これは新しいクラウドサービスではなく既存Backendの認証済みAPI。家族共有のTatsu Home側のWeb/Android UI・通知は廃止し、ニュースを表示しない。公開ニュースのみ共有Backendを通過し、Gmail関連データは通過しない

PC上のBrainには、従来どおり `OPENAI_API_KEY` と、送信認証用の `TATSU_HOME_AUTH_TOKEN`（既存Backendのowner token）をWindowsユーザー環境変数として設定する。Brainの最新コードに更新し、`TatsuDotsLuna` のスケジュールタスクを使う

初回のRSS取り込みはベースラインのみで、AI分析の新規バッチは作らない。RSSに新着があった場合のみ分析・送信されるため、一覧がすぐ空でも必ずしも障害ではない

### 実装範囲

- Homeの更新画面ではアプリ起動・更新操作時にBrainニュースを取得
- GmailはOS通知サービスでカウント（許可・Gmailアプリ通知が必要）
- 新規ニュースのHome自身によるバックグラウンド自動通知は未導入。従来のBrain監視は1時間間隔
- 自動更新は従来のHome GitHub Release配布を利用
- ネットワーク・実機に依存する動作はGalaxyでの手動受入試験が別途必要
