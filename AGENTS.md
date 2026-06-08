# ADB Network Inspector - 開発ガイドライン (AGENTS.md)

このドキュメントは、「ADB Network Inspector」プロジェクトにおける開発方針、アーキテクチャ、およびAI Agentが従うべき開発ルールを定義するものです。

---

## 1. プロジェクト概要

「ADB Network Inspector」は、デバッグ中のAndroidアプリのHTTP/HTTPS通信を、PC（デスクトップ）上でリアルタイムに監視・可視化するための開発者向けツールです。
Android Studio標準のNetwork Inspectorよりも柔軟に動作し、かつAndroidエンジニアがメンテナンスしやすいように Kotlin & Compose Multiplatform を用いて実装します。

### 主な特徴
1. **ADB経由の通信**: Android端末とPCがUSBやWi-Fiで接続され、`adb` が通る環境であれば、特別なネットワーク設定なしで動作します。
2. **WebSocket & Ktor**: Androidアプリ側がWebSocketサーバー（Ktor）となり、デスクトップアプリがクライアント（Ktor）となって通信します。
3. **オフラインキャッシュ**: デスクトップアプリ接続前に発生した通信も、Androidアプリ側で一定時間/一定件数キャッシュしておき、接続直後にまとめてデスクトップアプリへ送信します。
4. **接続待機**: デスクトップアプリ側は、Androidアプリが起動していない場合は起動を検知して自動的に再接続を試みます。

---

## 2. モジュール構成

プロジェクトは以下のマルチモジュール構成とします。

- **`:library`** (Android Library)
  - Androidアプリに組み込む通信ログ収集ライブラリ。
  - OkHttp Interceptor を提供し、通信の要求(Request)および応答(Response)をフックします。
  - 内蔵されたKtor WebSocketサーバーを立ち上げ、フックしたデータを保持・送信します。
  - グループIDおよびアーティファクトID: `net.mm2d.inspector:inspector`
- **`:desktop`** (Compose Multiplatform Application)
  - PC上で動作する通信可視化デスクトップアプリ。
  - パッケージ名: `net.mm2d.inspector.desktop`
  - Ktor WebSocketクライアントを実装し、指定のポートに接続して通信ログを受信します。
  - UIは Compose Multiplatform (Desktop) で実装し、時系列での一覧表示、詳細表示（JSONフォーマット、画像表示など）を可能にします。
- **`:sample`** (Android Application)
  - `:library` を組み込んだ動作確認用のサンプルAndroidアプリ。
  - パッケージ名: `net.mm2d.inspector.sample`
  - OkHttp を使ってテスト用の各種API（JSON、画像など）を叩く画面を用意します。

---

## 3. 技術スタック & ビルド環境

- **言語**: Kotlin (Kotlin 2.4.x 以上)
- **ビルドツール**: Gradle
- **Android Gradle Plugin (AGP)**: 9.x 環境
  - **重要**: `android.builtInKotlin=false` や `android.newDsl=false` のような互換プロパティは使用せず、新DSLおよびビルトインKotlinを利用して構築します。
- **UI フレームワーク**: Compose Multiplatform (Desktop), Jetpack Compose (Android)
- **通信プロトコル / ライブラリ**:
  - アプリ間通信: Ktor (WebSocket Server / Client)
  - 収集対象: OkHttp3
- **シリアライズ**: kotlinx.serialization (JSON形式でのデータ送受信用)

---

## 4. 詳細アーキテクチャ & 設計方針

### 4.1. データフローとADB Port Forwarding
```
+------------------------------------+          +--------------------------------------+
|       デバッグAndroidアプリ         |          |          デスクトップアプリ           |
|  [OkHttp Client]                   |          |  [Compose Multiplatform UI]          |
|         │ (通信発生)                |          |                   │                  |
|         ▼                          |          |                   ▼                  |
|  [Inspector OkHttp Interceptor]    |          |  [WebSocket Client (Ktor)]           |
|         │ (データ抽出＆シリアライズ) |          |                   │                  |
|         ▼                          |          |                   │                  |
|  [Memory Cache (一時蓄積)]          |          |                   │                  |
|         │ (未送信データ送信)        |          |                   │                  |
|         ▼                          |          |                   │ (WebSocket接続)   |
|  [Ktor WebSocket Server (Port X)]  | ◄========|===================│                  |
|                   ▲                |   adb    |                   │                  |
|                   └────────────────|──────────|───────────────────┘                  |
|                    adb forward tcp:PortY tcp:PortX                                  |
+------------------------------------+          +--------------------------------------+
```

1. **ADB Port Forwarding のセットアップ**:
   デスクトップアプリ起動時、または接続処理の開始時に、デスクトップアプリ内部（または外部スクリプト/ユーザー手動）で `adb forward tcp:<DesktopPort> tcp:<AndroidPort>` を実行し、デスクトップのローカルポートから端末内のKtorサーバーが待機するポートへのフォワーディングを確立します。
2. **Ktor WebSocket 接続**:
   デスクトップアプリは `ws://localhost:<DesktopPort>` に接続を試みます。
   接続が成功すると、接続待機ステータスから監視中ステータスへと遷移します。

### 4.2. `:library` の設計方針 (通信キャッシュ & 送信)
- **OkHttp Interceptor**:
  - 各通信リクエスト開始時およびレスポンス完了時にデータをキャプチャします。
  - キャプチャする項目: ID(UUID等で一意にする)、URL、Method、Headers、Body (バイナリデータ含む)、ステータスコード、リクエスト/レスポンス時間など。
  - レスポンスBodyが巨大な場合のメモリ負荷を考慮し、上限サイズ（例: 2MB）を設けてそれを超える場合は切り捨てる仕組みを導入します。
- **メモリキャッシュ**:
  - デスクトップアプリが接続していない間も、直近の通信データをメモリ上にキャッシュ（例: 直近100件、または過去5分間）します。
  - 新しいクライアント（デスクトップアプリ）が接続してきたら、蓄積されているキャッシュデータを時系列順にすべて送信し、その後はリアルタイムで随時送信します。
- **Ktor WebSocket Server**:
  - `:library` の初期化時にバックグラウンドスレッドで起動します。
  - デフォルトポート（例: 8082）で待機し、接続を待ち受けます。複数のクライアントが接続可能な設計にします。

### 4.3. `:desktop` の設計方針 (UI & 接続待機)
- **再接続・待機ロジック**:
  - デスクトップアプリは、接続が切断された場合、または接続に失敗した場合は、一定時間（例: 1秒や2秒）おきに再接続を繰り返すように待機します。これにより、Androidアプリの再起動や新規起動時にも自動で即座に接続されます。
- **UI構成**:
  - **分割画面（スプリットビュー）**:
    - **左ペイン**: 送信された通信ログの時系列一覧表示。メソッド、URL、ステータスコード、レスポンスサイズ、発生時刻を一覧化。検索バーやフィルタ（テキスト、ステータス、Methodなど）を搭載します。
    - **右ペイン**: 選択された通信ログの詳細表示。
      - **General**: URL、Method、Status、Durationなど。
      - **Request Headers / Response Headers**: キーと値のテーブル表示。
      - **Request Body / Response Body**:
        - `Content-Type` が `application/json` 等の場合はJSONをきれいにインデント・ハイライトして表示。
        - 画像（`image/png`, `image/jpeg` 等）の場合は、デコードして画像として描画。
        - テキスト系（HTML, XML等）はプレーンテキスト表示。
        - それ以外はサイズやプレーンテキスト（デコード可能であれば）表示。

---

## 5. 開発ルール

- **ドキュメント & コメントの言語**:
  - ソースコード中のコメント、KDoc、コミットメッセージ、およびドキュメントは、**すべて日本語**で作成してください。
- **実装前のプロセス**:
  - 実装を開始する前に、必ず本ドキュメント（AGENTS.md）および `implementation_plan.md` を作成してユーザーの承認を得てください。
  - 承認が得られるまで、いかなるソースコードの記述、作成も行ってはなりません。
- **GradleとKotlinの構成**:
  - AGP 9の新DSLを使用し、Kotlinのビルトインコンパイル設定を活用します。非推奨のフラグや旧DSLへの回帰を防ぎます。
- **コードスタイルとフォーマット (ktlint)**:
  - コードの品質維持とスタイル統一のため、`ktlint` を導入しています。
  - 実装時およびコミット前には、必ず `./gradlew ktlintFormat` を実行してコードスタイルを自動整形し、エラーや警告（ワイルドカードインポートの禁止、1行最大120文字の制限など）がないことを確認してください。
