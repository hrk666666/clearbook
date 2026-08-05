# Clear Book

TXT 电子书清洗工具 — 纯离线 Android App

## 功能

- 广告/推广/水印/翻页提示清理
- 同音字混淆智能检测（拼音相似度 + 上下文感知）
- 多余空行/空白字符清理
- 重复段落/章节去重
- 章节标题规范化（第1章 → 第一章）
- 格式转换（EPUB/HTML/PDF → TXT）
- 规则市场（GitHub 仓库）
- AI 规则生成（发给别的 AI 分析）

## 构建 APK

### 方式一：本地构建

```bash
# 前提：Java 17+、Android SDK
export ANDROID_HOME=/path/to/android-sdk
./build.sh
```

### 方式二：Docker 构建（无需本地 SDK）

```bash
./build-docker.sh
```

### 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 测试

浏览器直接打开 `app/src/main/assets/www/index.html`

测试文件：`app/src/main/assets/www/test-novel.txt`

## 目录结构

```
clearbook-app/
├── app/
│   ├── src/main/
│   │   ├── assets/www/          # Web 资源
│   │   │   ├── index.html       # 应用主文件
│   │   │   ├── jszip.min.js     # EPUB 解析库
│   │   │   ├── fonts/           # 图标字体
│   │   │   └── test-novel.txt   # 测试文件
│   │   ├── java/com/clearbook/  # Android 代码
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.sh                     # 本地构建脚本
├── build-docker.sh              # Docker 构建脚本
└── gradle/                      # Gradle Wrapper
```

## 纯离线

所有资源内嵌，无需网络：
- JSZip（EPUB 解析）
- Material Symbols（图标字体）
- 系统字体（无 CDN 依赖）
