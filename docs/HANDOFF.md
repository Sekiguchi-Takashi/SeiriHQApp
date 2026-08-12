# HANDOFF — SeiriHQApp

## 現在地

v1.2。プロンプト交通整理と素材交通整理を1アプリに統合した最小構成に、
パスコード＋指紋のロック、ファイル権限による原本削除、ゴミ箱を追加した。

## 構成

```text
SeiriHQApp/
├── app/
│   ├── build.gradle.kts
│   ├── debug.keystore              固定（上書きインストール用）
│   └── src/main/
│       ├── AndroidManifest.xml     メディア読み取り・USE_BIOMETRIC
│       ├── java/com/appathy/seirihq/
│       │   ├── MainActivity.kt     3タブのシェル
│       │   ├── data/Db.kt          スキーマ + Repository
│       │   ├── data/Store.kt       Compose状態 + 業務ロジック
│       │   ├── ui/PromptTab.kt     TOP / 一覧 / 編集
│       │   ├── ui/MediaTab.kt      Inbox / 詳細
│       │   └── ui/SettingsTab.kt   プロジェクト管理
│       └── res/values/
├── docs/SPEC.md                    統合仕様書
├── .github/workflows/build.yml     assembleDebug
└── deploy.sh
```

## 実装上の決めごと

- Roomは使わない。SQLiteOpenHelper直。KSP不使用
- Gradle Wrapperのjarを同梱していないため、CIは `gradle/actions/setup-gradle` で Gradle 8.9 を用意し `gradle assembleDebug` を実行する
- 素材取り込みは SAF（`OpenMultipleDocuments`）。メディア権限を要求しない
- 動画のサムネイルは未対応（アイコン表示）。対応する場合は `coil-video` と独自 ImageLoader が必要
- ナビゲーションライブラリは使わず、タブ内の `sealed class` ルート + `BackHandler`
- 指紋認証に androidx.biometric を使うため、MainActivity は `FragmentActivity`
- パスコードは PBKDF2（20000回・ソルト付き）でハッシュ化し state テーブルへ保存
- 再ロックは ON_STOP / ON_START と30秒の猶予で判定する。SAFピッカーやシステム削除画面から戻るたびに再入力させないため
- Android 10 は原本削除に非対応（RecoverableSecurityException の処理を入れていない）
- ゴミ箱への複製は Dispatchers.IO。複製成功後にだけ原本を削除し、キャンセル時は複製を消す
- ゴミ箱フォルダは OpenDocumentTree で選び、read/write を永続化する
- アプリ内ゴミ箱の共有は FileProvider（authorities は ${applicationId}.fileprovider）

## 次にやること（優先順）

1. プロジェクト詳細画面（素材数・未使用数・素材一覧）
2. タグの正規化（tag / media_tag テーブル）と3種別タグ
3. スワイプ交通整理
4. `media.source_prompt_id` を追加し、プロンプト→生成素材の導線を作る
5. AI（Phase 2）は接続方式を決めてから着手
6. ゴミ箱の自動削除を WorkManager で定期実行にする（現在は起動時のみ）
7. クラウド保存先が DocumentsProvider を出さない場合の自動アップロード手段を検討
