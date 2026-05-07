# CodeAtlas Project Instructions

## 项目概要

CodeAtlas 是一个面向 Java 程序的静态分析与变更影响分析平台。多模块 Gradle 工程（Java 25），Spring Boot + React + TypeScript。

## 构建与测试

- 构建：`./gradlew build`
- 测试：`./gradlew test`
- 前端：`cd codeatlas-ui && npm install && npm run dev`

## 技术栈

- 后端：Java 25 + Gradle + Spring Boot + Spoon + JSqlParser + JGit + Jasper
- 前端：React 19 + TypeScript + Vite + Lucide Icons
- 图：Neo4j（可选）+ InMemoryFactStore（默认）
- 存储抽象：FactStore → InMemoryFactStore / Neo4jFactStore

## 模块结构

| 模块 | 职责 |
|------|------|
| codeatlas-symbols | SymbolId 定义与解析 |
| codeatlas-facts | FactStore、FactRecord、Evidence、Confidence |
| codeatlas-graph | 图谱模型、遍历引擎、影响分析服务 |
| codeatlas-analyzers | 源码分析器（Spoon/Jasper/SQL/JPA/MyBatis） |
| codeatlas-ai | AI 契约、加密、RAG |
| codeatlas-mcp | 只读 MCP Server |
| codeatlas-server | Spring Boot REST API |
| codeatlas-worker | 分析任务执行 |
| codeatlas-ui | 前端 |

## 代码风格

- Java 记录（record）优先，不可变数据
- 工厂方法 `defaults()` 贯穿
- `requireNonBlank` / `requireNonNull` 输入校验
- 读写锁保护共享状态
- 测试：JUnit 5 + assertTrue/assertEquals

## 自动加载

plugin load superpowers
