# Clear Book

TXT 电子书清洗工具 — 纯离线 Android App / Web 版

## 功能

- 15 条内置清洗规则（广告/推广/水印/翻页/空白/乱码/重复...）
- 智能同音字混淆检测（拼音相似度 + 上下文感知 + 模式识别）
- 格式转换（EPUB / HTML / PDF → TXT）
- 规则市场（基于 GitHub 仓库，社区共享）
- AI 规则生成（发给别的 AI 分析文件，返回规则 JSON）
- 完整 Material Design 3 UI
- 纯离线运行，无需网络

## 使用

**Web 版：** https://hrk666666.github.io/clearbook/

**Android APK：**
```bash
# Docker 构建（推荐）
./build-docker.sh

# 或本地构建
./build.sh
```

**测试文件：** `docs/test-novel.txt`（含广告、同音字、空行等垃圾内容）

## 规则市场

访问 [clearbook-rules](https://github.com/hrk666666/clearbook-rules) 浏览和提交社区规则包。

## 许可证

[AGPL-3.0](LICENSE)
