<p align="center">
  <img src="docs/assets/logo.png" alt="EchoCampus-Bot Logo" width="50" height="50">
</p>

<h1 align="center">EchoCampus-Bot</h1>

<p align="center">
  <strong>RAG技術に基づくインテリジェントキャンパス知識Q&Aロボット</strong>
</p>

<p align="center">
  <a href="README.md">简体中文</a> | <a href="README.en.md">English</a> | 日本語
</p>

<p align="center">
  <!-- バッジ -->
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.1-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue.js-3.4.0-42b883.svg" alt="Vue.js">
  <img src="https://img.shields.io/badge/Java-17-orange.svg" alt="Java">
  <img src="https://img.shields.io/badge/TypeScript-5.3-blue.svg" alt="TypeScript">
</p>

<p align="center">
  <a href="#-機能">機能</a> •
  <a href="#-クイックスタート">クイックスタート</a> •
  <a href="#-デモ">デモ</a> •
  <a href="#-アーキテクチャ">アーキテクチャ</a> •
  <a href="#-ドキュメント">ドキュメント</a> •
  <a href="#-コントリビューション">コントリビューション</a>
</p>

---

## 📖 プロジェクト紹介

**EchoCampus-Bot**は、**RAG（検索拡張生成）**技術に基づくインテリジェントキャンパス知識Q&Aロボットです。モダンなフロントエンド・バックエンド分離アーキテクチャを採用し、Spring Boot、Vue.js、PostgreSQL、Milvusベクトルデータベースを組み合わせて、キャンパスユーザーに正確でインテリジェントな知識Q&Aサービスを提供します。

### コアハイライト

| 機能 | 説明 |
|------|------|
| 🧠 **RAGインテリジェントQ&A** | 検索拡張生成技術により、根拠に基づいた正確な回答を提供 |
| 📚 **マルチフォーマット対応** | PDF、Word、PPT、Markdown、TXTなど複数のドキュメント形式をサポート |
| 🔍 **セマンティック検索** | Milvusベクトルデータベースによる効率的なセマンティック類似度検索 |
| ⚡ **スマートチャンキング** | LangChain4jの再帰的分割により、意味の完全性を維持 |
| 🔐 **完全なセキュリティ** | JWT認証、メール認証、操作ログの完全カバレッジ |
| 🐳 **コンテナ化デプロイ** | Docker Composeによるワンクリックデプロイ |

### 動作原理

```
ユーザー質問 → ベクトル化 → Milvus検索 → コンテキスト構築 → DeepSeek生成 → 回答返却
```

---

## ✨ 機能

### 💬 インテリジェントQ&Aシステム

- **RAGアーキテクチャ** - 検索拡張生成により、根拠に基づく正確な回答
- **マルチターン対話** - 対話コンテキストを保持し、一貫した会話を実現
- **出典引用** - 回答に知識ソースを添付し、検証をサポート
- **Markdownレンダリング** - コードハイライト、テーブルなどのリッチテキスト表示をサポート

### 📚 ナレッジベース管理

- **マルチフォーマット解析** - PDF、DOCX、DOC、PPT、PPTX、MD、TXT形式をサポート
- **スマートチャンキング** - LangChain4jの再帰的分割により意味の完全性を維持
- **カテゴリ管理** - 柔軟な知識分類体系で整理が容易
- **バッチ操作** - 一括アップロード、削除などの操作をサポート

### 🔐 ユーザーとセキュリティ

- **ユーザー認証** - 安全なJWTトークン認証メカニズム
- **メール認証** - 登録/パスワードリセット時のメール認証コードサポート
- **ロール権限** - ユーザー/管理者のロール区分
- **操作ログ** - 完全な操作監査ログ記録

### ⚙️ システム管理

- **設定センター** - ビジュアル化されたパラメータ設定、再起動不要
- **データ統計** - Q&A統計、ユーザー統計などのデータ分析
- **システム監視** - APIレスポンス時間、呼び出し頻度の監視
- **レート制限** - リクエスト頻度制限を内蔵し、悪用を防止

### 対応ドキュメント形式

| 形式 | 拡張子 | パーサー | 説明 |
|------|--------|----------|------|
| PDF | `.pdf` | Apache PDFBox 3.0 | テキスト型PDFをサポート |
| Word | `.docx`, `.doc` | Apache POI 5.2 | Officeドキュメントをサポート |
| PPT | `.pptx`, `.ppt` | Apache POI 5.2 | スライドテキストを抽出 |
| Markdown | `.md` | Flexmark | 構造情報を保持 |
| プレーンテキスト | `.txt` | Java Native | 汎用テキスト形式 |

---

## 🚀 クイックスタート

### 環境要件

| 環境 | バージョン | 説明 |
|------|----------|------|
| JDK | 17+ | バックエンド実行環境 |
| Node.js | 18+ | フロントエンドビルド環境 |
| Docker | 20.10+ | コンテナ化デプロイ（推奨） |
| PostgreSQL | 15+ | リレーショナルデータベース |
| Milvus | 2.3+ | ベクトルデータベース |

### Dockerワンクリックデプロイ（推奨）

```bash
# 1. プロジェクトをクローン
git clone https://github.com/yourusername/EchoCampus-Bot.git
cd EchoCampus-Bot

# 2. 環境変数を設定
cp .env.example .env
# .envファイルを編集し、APIキーなどを入力

# 3. すべてのサービスを起動
docker-compose -f docker-compose.dev.yml up -d

# 4. サービスステータスを確認
docker-compose ps
```

### 手動インストール

<details>
<summary>手動インストール手順を展開</summary>

#### 1️⃣ 基本サービスの起動

```bash
# PostgreSQLを起動
docker run -d --name echocampus-postgres \
  -e POSTGRES_DB=echocampus_bot \
  -e POSTGRES_USER=echocampus \
  -e POSTGRES_PASSWORD=your_password \
  -p 5432:5432 postgres:15

# Milvusを起動（公式ドキュメントを参照）
# https://milvus.io/docs/install_standalone-docker.md
```

#### 2️⃣ バックエンドサービス

```bash
cd backend

# データベース接続を設定（application-dev.ymlを編集）
# ビルドして実行
./mvnw clean package -DskipTests
java -jar target/echocampus-bot-1.0.0.jar --spring.profiles.active=dev
```

#### 3️⃣ フロントエンドサービス

```bash
cd frontend

# 依存関係をインストール
pnpm install

# 開発モードで実行
pnpm dev

# プロダクションビルド
pnpm build
```

</details>

### 必要なAPIキー

このプロジェクトを実行するには、以下のAPIサービスを設定する必要があります：

| サービス | 用途 | 取得方法 |
|---------|------|----------|
| Alibaba Cloud 百炼 | テキストベクトル化（Qwen3-Embedding） | [百炼コンソール](https://bailian.console.aliyun.com/) |
| DeepSeek | 大規模言語モデル | [DeepSeekプラットフォーム](https://platform.deepseek.com/) |

`.env`ファイルで設定：

```bash
# AIサービス設定
ALIYUN_API_KEY=your_aliyun_api_key
DEEPSEEK_API_KEY=your_deepseek_api_key
```

### 基本的な使用方法

1. **システムにアクセス**: ブラウザで`http://localhost:5173`（開発モード）または`http://localhost`（Dockerデプロイ）にアクセス
2. **アカウント登録**: ユーザー名、メールアドレスを入力して登録完了
3. **ナレッジアップロード**: ナレッジベース管理に進み、ドキュメントをアップロード
4. **対話開始**: チャット画面で質問し、インテリジェントな回答を取得

---

## 📸 デモ

> 📌 **注意**: プロジェクトのスクリーンショットは近日公開予定です！

<!-- 
<p align="center">
  <img src="docs/assets/demo-chat.png" alt="チャット画面" width="80%">
  <br><em>インテリジェントQ&Aチャット画面</em>
</p>

<p align="center">
  <img src="docs/assets/demo-knowledge.png" alt="ナレッジベース管理" width="80%">
  <br><em>ナレッジベースドキュメント管理</em>
</p>
-->

---

## 🏗️ アーキテクチャ

### システムアーキテクチャ図

```
┌─────────────────────────────────────────────────────────────────────┐
│                       ユーザーインターフェース (Vue.js 3)              │
│     チャット  │  ナレッジ管理  │  ユーザーセンター  │  設定           │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ RESTful API
┌──────────────────────────────▼──────────────────────────────────────┐
│                    バックエンドサービス (Spring Boot 3)                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ Q&Aサービス│  │ナレッジ   │  │ユーザー   │  │システム   │            │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘            │
└───────┼─────────────┼─────────────┼─────────────┼───────────────────┘
        │             │             │             │
┌───────▼─────────────▼─────────────▼─────────────▼───────────────────┐
│                           データストレージ層                           │
│  ┌─────────────────┐              ┌─────────────────┐               │
│  │   PostgreSQL    │              │     Milvus      │               │
│  │  • ユーザーデータ │              │  • ドキュメントベクトル│          │
│  │  • 対話履歴      │              │  • セマンティックインデックス│     │
│  │  • ナレッジメタ  │              │                 │               │
│  └─────────────────┘              └─────────────────┘               │
└─────────────────────────────────────────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────────┐
│                          AIサービス層                                 │
│  ┌─────────────────────┐      ┌─────────────────────┐              │
│  │  Alibaba Cloud      │      │     DeepSeek        │              │
│  │  Qwen3-Embedding    │      │   deepseek-chat     │              │
│  │  ベクトル化 (1024次元) │      │   回答生成          │              │
│  └─────────────────────┘      └─────────────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

### 技術スタック

<table>
  <tr>
    <th align="center">🖥️ フロントエンド</th>
    <th align="center">⚙️ バックエンド</th>
    <th align="center">💾 データストレージ</th>
    <th align="center">🤖 AIサービス</th>
  </tr>
  <tr>
    <td>
      <img src="https://img.shields.io/badge/Vue.js-3.4-42b883?logo=vue.js" alt="Vue.js"><br>
      <img src="https://img.shields.io/badge/TypeScript-5.3-3178c6?logo=typescript" alt="TypeScript"><br>
      <img src="https://img.shields.io/badge/Vite-5.0-646cff?logo=vite" alt="Vite"><br>
      <img src="https://img.shields.io/badge/Ant_Design-4.1-0170fe?logo=antdesign" alt="Ant Design"><br>
      <img src="https://img.shields.io/badge/Pinia-2.1-ffd859" alt="Pinia">
    </td>
    <td>
      <img src="https://img.shields.io/badge/Spring_Boot-3.2-6db33f?logo=springboot" alt="Spring Boot"><br>
      <img src="https://img.shields.io/badge/MyBatis_Plus-3.5-blue" alt="MyBatis Plus"><br>
      <img src="https://img.shields.io/badge/LangChain4j-0.28-orange" alt="LangChain4j"><br>
      <img src="https://img.shields.io/badge/Knife4j-4.4-green" alt="Knife4j"><br>
      <img src="https://img.shields.io/badge/JWT-0.12-purple" alt="JWT">
    </td>
    <td>
      <img src="https://img.shields.io/badge/PostgreSQL-15-336791?logo=postgresql" alt="PostgreSQL"><br>
      <img src="https://img.shields.io/badge/Milvus-2.3-00a1ea" alt="Milvus"><br>
      <img src="https://img.shields.io/badge/Druid-1.2-blue" alt="Druid">
    </td>
    <td>
      <img src="https://img.shields.io/badge/Qwen3_Embedding-1024d-ff6600" alt="Qwen3"><br>
      <img src="https://img.shields.io/badge/DeepSeek-Chat-7c3aed" alt="DeepSeek">
    </td>
  </tr>
</table>

---

## 📂 プロジェクト構造

```
EchoCampus-Bot/
├── 📁 backend/                    # バックエンド Spring Boot プロジェクト
│   ├── src/main/java/            # Javaソースコード
│   │   └── com/echocampus/bot/
│   │       ├── config/           # 設定クラス (Security, AI, Milvusなど)
│   │       ├── controller/       # RESTfulコントローラー
│   │       ├── service/          # ビジネスロジック層
│   │       ├── mapper/           # MyBatisデータアクセス層
│   │       ├── entity/           # エンティティクラス
│   │       ├── dto/              # データ転送オブジェクト
│   │       ├── parser/           # ドキュメントパーサー
│   │       └── utils/            # ユーティリティクラス
│   ├── src/main/resources/       # 設定ファイル
│   └── pom.xml                   # Maven設定
│
├── 📁 frontend/                   # フロントエンド Vue.js プロジェクト
│   ├── src/
│   │   ├── api/                  # APIインターフェースラッパー
│   │   ├── components/           # 共有コンポーネント
│   │   ├── views/                # ページビュー
│   │   ├── stores/               # Pinia状態管理
│   │   ├── router/               # ルーター設定
│   │   └── utils/                # ユーティリティ関数
│   ├── package.json              # 依存関係設定
│   └── vite.config.ts            # Viteビルド設定
│
├── 📁 docs/                       # プロジェクトドキュメント
│   ├── 项目结构设计书.md          # 完全な技術アーキテクチャドキュメント（中国語）
│   ├── deployment/               # デプロイドキュメント
│   ├── development/              # 開発ドキュメント
│   └── reference/                # 参考資料
│
├── 🐳 docker-compose.dev.yml     # 開発環境 Docker 設定
├── 🐳 docker-compose.prod.yml    # 本番環境 Docker 設定
├── 📄 .env.example               # 環境変数テンプレート
└── 📄 README.md                  # プロジェクトドキュメント
```

> 📖 **詳細ドキュメント**: [プロジェクトアーキテクチャ設計](docs/项目结构设计书.md)を参照（中国語）

---

## 📚 ドキュメント

| ドキュメント | 説明 |
|-------------|------|
| 📐 [プロジェクトアーキテクチャ設計](docs/项目结构设计书.md) | 完全なシステムアーキテクチャ、データベース設計、API仕様（中国語） |
| 🚀 [クイックデプロイガイド](docs/deployment/快速部署指南.md) | ローカル開発と本番環境のデプロイ説明（中国語） |
| ⚙️ [環境変数設定](docs/deployment/环境变量配置说明.md) | 環境変数の詳細説明（中国語） |
| 🧪 [テストガイド](docs/development/测试代码说明.md) | テストアーキテクチャとガイド（中国語） |
| 🔍 [RAG実装](docs/reference/知识库检索增强功能说明.md) | RAG技術実装説明（中国語） |

### APIドキュメント

バックエンドサービス起動後、Swaggerドキュメントにアクセス：
- 開発環境: `http://localhost:8083/doc.html`
- 本番環境: `http://your-domain/api/doc.html`

---

## 🤝 コントリビューション

あらゆる形式のコントリビューションを歓迎します！問題の報告、機能の提案、コードの提出など。

### コントリビュート方法

1. このリポジトリを**Fork**
2. 機能ブランチを**作成** (`git checkout -b feature/AmazingFeature`)
3. 変更を**コミット** (`git commit -m 'Add some AmazingFeature'`)
4. ブランチに**プッシュ** (`git push origin feature/AmazingFeature`)
5. Pull Requestを**提出**

### コード規約

- バックエンドは[Alibaba Javaコーディングガイドライン](https://github.com/alibaba/p3c)に従います
- フロントエンドは[Vue.jsスタイルガイド](https://vuejs.org/style-guide/)に従います
- コミットメッセージは[Conventional Commits](https://www.conventionalcommits.org/)に従います

### Issueの提出

- 🐛 **バグレポート**: バグレポートテンプレートを使用し、詳細な再現手順を提供
- 💡 **機能リクエスト**: 期待される機能とユースケースを説明
- 📖 **ドキュメント改善**: ドキュメントの誤りや不明確な点を指摘

---

## 📋 ロードマップ

- [x] 🧠 RAGインテリジェントQ&Aコア機能
- [x] 📚 マルチフォーマットドキュメント解析とベクトル化
- [x] 🔐 ユーザー認証と権限管理
- [x] 🐳 Dockerコンテナ化デプロイ
- [x] 📧 メール認証機能
- [ ] 🔄 ストリーミングレスポンス（SSE）
- [ ] 🌐 多言語サポート
- [ ] 📱 モバイル対応
- [ ] 🔌 プラグインシステム
- [ ] 📊 高度なデータ分析ダッシュボード

> 💬 新機能の提案はありますか？[Issueを提出](../../issues/new)してください！

---

## 💬 コミュニティサポート

使用中に問題が発生した場合、以下の方法でヘルプを得ることができます：

- 📖 [プロジェクトドキュメント](docs/README.md)を参照
- 🔍 [既存のIssue](../../issues)を検索
- ❓ [新しいIssue](../../issues/new)を提出
- 💬 [Discussions](../../discussions)に参加

---

## 📄 ライセンス

このプロジェクトは[GNU General Public License v3.0](LICENSE)の下でオープンソース化されています。

GPL-3.0は強力なコピーレフトライセンスで、派生作品も同じライセンスでオープンソース化する必要があります。

---

## 🙏 謝辞

以下のオープンソースプロジェクトとサービスに感謝します：

- [Spring Boot](https://spring.io/projects/spring-boot) - バックエンドフレームワーク
- [Vue.js](https://vuejs.org/) - フロントエンドフレームワーク
- [LangChain4j](https://docs.langchain4j.dev/) - LLMアプリケーションフレームワーク
- [Milvus](https://milvus.io/) - ベクトルデータベース
- [Apache PDFBox](https://pdfbox.apache.org/) - PDF解析
- [Apache POI](https://poi.apache.org/) - Officeドキュメント解析
- [Alibaba Cloud 百炼](https://bailian.console.aliyun.com/) - Embeddingサービス
- [DeepSeek](https://www.deepseek.com/) - LLMサービス

---

<p align="center">
  このプロジェクトが役に立った場合は、⭐ Starをお願いします！
</p>

<p align="center">
  Made with ❤️ by EchoCampus Team from Shanghai Institute of Technology
</p>
