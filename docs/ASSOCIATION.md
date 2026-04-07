# 智能联想功能

## 功能说明

Kime 输入法集成了基于 ONNX Runtime 框架的 AI 智能联想功能，可以根据用户输入智能推荐候选词。

## 如何启用

1. 打开 Kime 输入法设置
2. 找到"智能联想"选项
3. 开启联想功能开关

## 工作原理

- 当用户开启联想功能时，应用会从 assets 目录复制模型文件到应用私有目录
- 模型文件约 28MB，首次启用时需要几秒钟初始化
- 基于用户当前输入，AI 模型会预测可能的后续词语

## 技术细节

- 推理框架：ONNX Runtime (Android)
- 模型：量化后的 Transformer 模型（~28MB）
- 词汇表：~128KB

## 文件结构

```
app/src/main/assets/association_model/
├── model_q4.onnx   # 量化模型文件
└── vocab.json      # 词汇表
```

## 开发说明

模型文件需要手动放置到 assets 目录，不包含在 git 仓库中。