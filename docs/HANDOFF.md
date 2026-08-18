# HANDOFF — SeiriHQApp

## 現在地

v1.13。プロンプト交通整理と素材交通整理を1アプリに統合した最小構成に、
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
- build.yml は作らない。CIは release.yml（タグ起動）のみ。
  actions/upload-artifact は使わない（Artifacts枠0.5GBが枯渇して全ビルドが落ちるため）
- 素材取り込みは SAF（`OpenMultipleDocuments`）。メディア権限を要求しない
- 動画サムネイルは coil-video。`SeiriApp`（Application）で ImageLoaderFactory を実装している
- ダウンロード整理は DocumentsContract で直下を1クエリ取得する（DocumentFile.listFiles は遅い）
- 交通整理のスワイプは detectDragGestures で dx/dy を積算し、大きい方の軸で判定する
- 定期削除は WorkManager（1日1回・KEEP）。Store を介さず Repository を直接使う
- 一括削除は MediaStore.createDeleteRequest に複数URIを渡して確認画面を1回にまとめる
- 生成元プロンプトは media.source_prompt_id（0は未設定）。コピー時の①②を state に記録している
- フォルダ取り込みは FolderCleaner.list を再利用し、画像・動画だけを登録する（直下のみ）
- ZIP出力は CreateDocument("application/zip") で出力先を選ばせ、ZipOutputStream へ直接流す
- タグは tag / media_tag に正規化済み。media.tags 列は残っているが読み書きしない
- DB v5 の onUpgrade で旧カンマ区切りタグをユーザータグへ移行する（migrateTags）
- AIタグは confirmed=0 で入れる想定。Phase 2 で生成処理を足す
- tag / media_tag は onOpen でも CREATE TABLE IF NOT EXISTS する（更新経路が乱れても壊れないため）
- Repository.addTag は書き込み後に読み戻して検証し、Result で失敗理由を返す
- media.pinned が常用フラグ。Inboxの既定は pinned のみ表示（state の pinned_only）
- 常用は状態と独立。アーカイブは status を変えるだけでファイルは残す
- prompt_set は①②とプロジェクトの組み合わせ。適用すると activeProjectId も切り替わる
- activeProjectId が立っていると Store.addMedia が取り込み時に自動で linkProject する
- deploy.sh は恒久仕様。push → pull --rebase → タグ発行まで1コマンドで行う。
  次タグは `git tag --list 'v*' | sort -V` の最大値から算出し、`git tag` / `git push origin タグ名` で
  ローカル発行する（APIのheads参照は反映遅延で一つ前のタグに付くため禁止）。
  第2引数に notag を渡すと push のみ。
  pull --rebase が無いと push が rejected になる（CatalogApp の rollout.sh が
  release.yml と ci/appathy.keystore を API 経由で直接コミットするため）
- ファイルを削除する納品では deploy.sh に `rm -f 対象パス` を足す（unzip -o は端末の旧ファイルを消さない）
- .github/workflows/release.yml と ci/appathy.keystore、ci/ ディレクトリは配布ビルドに必要。削除しない
- タグを打つと Actions がビルドして Release を作り、自作アプリストアに更新として現れる
- クリップボードは他プロバイダのURIをそのまま渡せないため、cacheDir/share へ複製して
  FileProvider のURIを ClipData.newUri で渡す（file_paths.xml に cache-path を追加済み）
- 横に並べるボタンは FlowRow を使う。Row のままだと画面幅で潰れる
- アイコンは res/mipmap-xxxhdpi/ic_launcher_source.png を差し替えるだけで変わる。
  adaptive-icon の前景は drawable/ic_launcher_foreground.xml が18dpの余白を付けて表示する
  （minSdk 26 なので mipmap-anydpi-v26 のみで足りる）
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

## ライブラリの実装メモ

- 画像は filesDir/library/<charaId>/<timestamp>_<名前>.jpg。DBは絶対パスを持つ
- 取り込みは inSampleSize で長辺が上限を超えないところまで落としてから createScaledBitmap。
  OutOfMemoryError を捕まえて inSampleSize を倍にして再試行する（32まで）
- 取り込み失敗時は最初のエラー文言をトーストに出す（件数だけだと切り分けできないため）
- Exifの向きは androidx.exifinterface で補正してから保存する
- バックアップは DocumentFile で <tree>/グループ/キャラ/ を作り、同名があれば飛ばす
- 旧素材タブ（MediaTab）はそのまま。参照型の資産を消していないので、必要なら戻せる

## 次にやること（優先順）

1. ライブラリの画像を並べ替え・ドラッグ移動（キャラ間の移し替え）
2. バックアップの自動実行（WorkManager）と、書き出し済みかの表示
3. ライブラリの画像もプロンプトのセットから呼び出せるようにする
4. AI（Phase 2）は接続方式を決めてから着手。タグの器は用意済み
