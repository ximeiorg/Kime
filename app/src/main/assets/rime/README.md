# Rime 配置文件目录

此目录用于存放 Rime 输入法的配置文件。

## 如何使用

1. 将你的 Rime 配置文件（如 `default.yaml`, `wubi86.schema.yaml`, `wubi86.dict.yaml` 等）放入此目录
2. 编译应用时，这些文件会被自动复制到应用的 Rime 数据目录

## 推荐配置

你可以使用以下配置仓库：
- https://github.com/kingzcheung/rime-wubi

将仓库中的配置文件复制到此目录即可。

## 必需文件
 
- `default.yaml` - 默认配置
- `wubi86.schema.yaml` - 五笔86输入方案
- `wubi86.dict.yaml` - 五笔86词库

## 注意事项

- 配置文件会在应用首次启动时复制到应用数据目录
- 如果用户数据目录中已存在同名文件，将不会被覆盖（保留用户修改）
- 修改此目录的文件后需要重新编译应用