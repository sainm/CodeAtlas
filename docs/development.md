# CodeAtlas 本地开发说明

本文记录重置版工程基线的本地开发入口。设计事实以 `docs/design.md` 为准，本文只说明如何运行当前仓库。

## 环境要求

- JDK 25
- Node.js 22 或更新版本
- npm
- Gradle wrapper 使用仓库内的 `gradlew.bat` / `gradlew`

如果本机默认 JDK 不是 25，可以在 PowerShell 中显式指定：

```powershell
$env:JAVA_HOME='D:\jdks\jdk-25.0.2+10'
```

## 后端测试

```powershell
.\gradlew.bat test --no-daemon
```

## 后端 health endpoint

当前 server 模块使用 Spring Boot + Spring MVC 提供最小 HTTP health endpoint：

```text
GET /api/v1/health
```

启动方式：

```powershell
.\gradlew.bat :codeatlas-server:bootRun --args='--server.port=8080'
```

响应示例：

```json
{"status":"ok","service":"codeatlas-server"}
```

## 前端开发

首次安装依赖：

```powershell
cd codeatlas-ui
npm ci
```

启动 Vite dev server：

```powershell
npm run dev
```

生产构建：

```powershell
npm run build
```

Gradle 根构建会调用前端 build：

```powershell
.\gradlew.bat build --no-daemon
```

## 忽略目录

以下目录和文件属于本地生成物，不应提交：

- `.gradle/`
- `**/build/`
- `codeatlas-ui/node_modules/`
- `codeatlas-ui/dist/`
- `.superpowers/`
- `gradle-*-bin.zip`
