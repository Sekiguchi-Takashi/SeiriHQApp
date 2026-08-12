# HANDOFF — SeiriHQApp

## 現在地

v1.6。プロンプト交通整理と素材交通整理を1アプリに統合した最小構成に、
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
- 動画サムネイルは coil-video。`SeiriApp`（Application）で ImageLoaderFactory を実装している
- ダウンロード整理は DocumentsContract で直下を1クエリ取得する（DocumentFile.listFiles は遅い）
- 交通整理のスワイプは detectDragGestures で dx/dy を積算し、大きい方の軸で判定する
- 定期削除は WorkManager（1日1回・KEEP）。Store を介さず Repository を直接使う
- 一括削除は MediaStore.createDeleteRequest に複数URIを渡して確認画面を1回にまとめる
- 生成元プロンプトは media.source_prompt_id（0は未設定）。コピー時の①②を state に記録している
- フォルダ取り込みは FolderCleaner.list を再利用し、画像・動画だけを登録する（直下のみ）
- ZIP出力は CreateDocument("application/zip") で出力先を選ばせ、ZipOutputStream へ直接流す
- ナビゲーションライブラリは使わず、タブ内の `sealed class` ルート + `BackHandler`
- 指紋認証に androidx.biometric を使うため、MainActivity は `FragmentActivity`
- パスコードは PBKDF2（20000回・ソルト付き）でハッシュ化し state テーブルへ保存
- 再ロックは ON_STOP / ON_START と30秒の猶予で判定する。SAFピッカーやシステム削除画面から戻るたびに再入力させないため
- Android 10 は原本削除に非対応（RecoverableSecurityException の処理を入れていない）
- ゴミ箱への複製は Dispatchers.IO。複製成功後にだけ原本を削除し、キャンセル時は複製を消す
- ゴミ箱フォルダは OpenDocumentTree で選び、read/write を永続化する
- アプリ内ゴミ箱の共有は FileProvider（authorities は ${applicationId}.fileprovider）
- Store のプロパティと同名のセッター関数は JVM シグネチャが衝突する。
  `var retentionDays` と `fun setRetentionDays()` は共存できないため、
  更新用の関数は `updateRetentionDays` / `updateAuthOnDelete` と命名している

## 次にやること（優先順）

1. タグの正規化（tag / media_tag テーブル）と3種別タグ
2. プロジェクト単位でプロンプトを絞り込む（プロンプトセットの前段）
3. スワイプ方向を設定で変更できるようにする
4. AI（Phase 2）は接続方式を決めてから着手
