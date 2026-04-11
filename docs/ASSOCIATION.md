# 智能联想功能

## 功能说明

Kime 输入法集成了基于 ONNX Runtime 框架的 AI 智能联想功能，可以根据用户输入智能推荐候选词。

## 如何启用

1. 安装 prediction-onnx 插件 APK
2. 打开 Kime 输入法设置
3. 找到"智能联想"选项
4. 开启联想功能开关

## 工作原理

- 插件内置 ONNX 模型和词汇表
- 基于用户当前输入，AI 模型会预测可能的后续词语
- 支持离线运行，无需网络连接

## 技术细节

- 推理框架：ONNX Runtime (Android)
- 模型：量化后的 Transformer 模型
- 词汇表：vocab.json / vocab.txt

## 插件文件结构

```
plugins/prediction-onnx/src/main/assets/
├── model.onnx           # ONNX 模型
├── model.onnx.data      # 模型数据文件
├── model_int8_dynamic.onnx  # INT8 量化模型
├── vocab.json           # 词汇表 (JSON 格式)
├── vocab.txt            # 词汇表 (文本格式)
└── manifest.json        # 模型配置
```

## 多架构支持

prediction-onnx 插件支持多架构：
- arm64-v8a - 适用于大多数现代手机
- armeabi-v7a - 适用于旧款32位手机
- x86 - 适用于模拟器
- x86_64 - 适用于模拟器
- universal - 包含所有架构

## 开发说明

模型文件需要手动放置到插件 assets 目录，不包含在 git 仓库中。