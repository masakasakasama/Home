# Privateリポジトリ移行：公開APK配布との分離

## 前提と制約

現行の `Home` は公開GitHub Release `/releases/latest` を認証なしで追跡し、`Home-1.0.N.apk` を直接ダウンロードする。ソースをPrivateにするだけで、この仕組みは壊れる。この変更で、更新クライアントは **公開配布専用レポジトリ `masakasakasama/Home-Downloads` を優先し、レガシー `Home` の公開Releaseをフォールバック** として扱えるようになる。

**公開されている `shared-debug.jks` とパスワードは既に漏洩済み。Private化しても安全にならない。** Stockアプリ側が署名保護された `READ_WATCHLIST` をHomeへ許すため旧鍵との依存がある。新鍵に署名を変えて同じpackage IDへ無計画に上書きしないこと。

## Private化する前の準備（アカウントオーナーがGitHub UIで実行）

1. GitHub上で **Public** の空の配布専用repoを新設: `masakasakasama/Home-Downloads`。ここにソース、keystore、token、個人情報を一切置かない。公開対象はAPKのRelease assetのみ
2. GitHubのSettingsで **fine-grained personal access token** を発行。対象リポジトリは `Home-Downloads` **のみ**、権限は `Contents: Read and write` のみ、期限を設定
3. `Home` のSettings → Secrets and variables → Actionsで、Repository secret `HOME_DISTRIBUTION_TOKEN` に上記tokenを保存。変数 `HOME_DISTRIBUTION_REPO` に `masakasakasama/Home-Downloads` を設定
4. Home mainへの変更でCIが走り、新版APKが `Home` Releaseと `Home-Downloads` Releaseの**両方**に配布されるか検証。Galaxyで配布専用repoからの更新確認・インストールを試す
5. Galaxyで切替済みバージョンが実際に起動し、更新先が `Home-Downloads` になったことを確認する
6. **旧署名鍵の安全な移行を別途完了**すること。推奨は新しい固有package ID＋新鍵での並行移行（必要な設定のバックアップ/インポートを設計する）。旧鍵で署名されたアプリの上書き更新は新鍵では通常できない。新アプリがStockの署名保護Permissionを引き継ぐこともできないため、依存の再設計が必要
7. 移行確認後に `Home` のSettings → General → Danger Zone → Change repository visibility → **Private**。移行中は誤ってPrivateにしない

## 署名と配布の安全性

- このPRは「公開配布チャンネルの橋渡し」であって、公開済み旧署名鍵問題の修正ではない
- 旧鍵で作成された古いAPKの署名は信頼できない。誰でも同じ鍵で署名可能なので、出所不明のAPKをインストールしないこと
- HomeアプリのGitHubソース更新と公開配布を分離しても、配布repoへの書き込みtokenが漏れれば悪意ある更新APKを置ける。repo限定fine-grained tokenを使い、期限と監査を設ける
- GitHub Actionsに依存するPrivateリポジトリのCIはプランの利用枠と課金に留意
- 既存のHome v1.0.60以下にはまだ配布先切替がない。自動更新の継続には切替対応版を**先に**正常インストールする必要がある
- この変更のみではHomeをPrivateにしてはいけない。配布repoの存在・token・Galaxyでの更新動作確認が必要
