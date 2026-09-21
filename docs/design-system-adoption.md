# Design System adoption

Home は Tatsu Design System の最初の consumer です。

- Design System は build-time snapshot として取り込む
- pin した revision は `.design-system.json` で管理する
- Ocean / Dark を初期テーマにする
- 既存画面を全面改修せず、まず theme と主要 semantic color から移行する
- Design System の main 更新は GitHub Actions が毎日確認し、変更時だけ build/test 後に PR を作る
- PR は自動 merge しない
- 既存の app-specific 色、余白、文字サイズは段階的に semantic token / component へ寄せる
