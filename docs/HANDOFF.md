# HANDOFF — SeiriHQApp

## 現在地

v1.0（Phase 0）。プロンプト交通整理と素材交通整理を1アプリに統合した最小構成。

## 構成

```text
SeiriHQApp/
├── app/
│   ├── build.gradle.kts
│   ├── debug.keystore              固定（上書きインストール用）
│   └── src/main/
│       ├── AndroidManifest.xml     権限なし
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

## 次にやること（優先順）

1. プロジェクト詳細画面（素材数・未使用数・素材一覧）
2. タグの正規化（tag / media_tag テーブル）と3種別タグ
3. スワイプ交通整理
4. `media.source_prompt_id` を追加し、プロンプト→生成素材の導線を作る
5. AI（Phase 2）は接続方式を決めてから着手
