# spring-ai-lab

LLM 应用架构师主线项目。**不是若干孤立 Demo，而是一个系统的版本演进**：v0.1 → v1.0。

## 版本基线（2026-10-13 定）

| 组件 | 版本 | 备注 |
|---|---|---|
| JDK | 17 | `D:\Tools\jdk-17.0.11`；**不动系统 `JAVA_HOME`**（指向 jdk1.8，保护老项目） |
| Maven | **3.9.11**（wrapper 自带） | 见踩坑 #6：3.10.0 与 IDEA 导入服务不兼容，已降级 |
| Spring Boot | 4.1.1 | Boot 4 起 `spring-boot-starter-web` → **`spring-boot-starter-webmvc`** |
| Spring AI | 2.0.1 | 2.0 起 `FunctionCallback` → `ToolCallback`，且**配置属性扁平化** |
| Ollama | 0.40.3 | 本地 `http://localhost:11434`，iGPU 已生效 |
| 模型 | `qwen3:8b` / `bge-m3` | 本机实测 decode 基线 **10.7~11.5 tok/s** |
| LangChain4j | 1.22.0（Spring 集成 `-spring-boot4-starter` 1.22.0-beta32） | 供后续 `lab-langchain4j` 模块使用 |

## 模块

| 模块 | 职责 | 状态 |
|---|---|---|
| `lab-springai` | Spring AI 主线：对话 → 记忆 → RAG → 评估 → Agent | **v0.1 进行中** |
| `lab-langchain4j` | LangChain4j 对照线 | 未开始 |

> **为什么分模块而不合并成一个应用**：两个框架的自动配置都会注册同名 bean（如 `ollamaChatModel`），
> 同进程启动会抛 `BeanDefinitionOverrideException`。分模块 = 分进程，从根上绕开。

## 运行

```bash
# 1. 确认 Ollama 在跑
curl -s http://localhost:11434/api/tags

# 2. 启动（项目根目录：aistudy/项目/spring-ai-lab）
./mvnw -pl lab-springai spring-boot:run
```

> 命令行用 `./mvnw`（自带 Maven **3.9.11**，不依赖系统那个 3.2.5）。
> 若 `JAVA_HOME` 指向 jdk1.8，需临时覆盖：`JAVA_HOME=/d/Tools/jdk-17.0.11 ./mvnw ...`
> 用 IDEA 打开时给项目指定项目级 JDK 17 + Maven（Wrapper 或 Bundled 皆可，都是 3.9.11），
> **不要**改系统环境变量。

## 接口（v0.1）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | **内置聊天页面**（流式逐字渲染，推荐入口） |
| GET | `/chat?message=你好` | 同步对话，一次性返回 |
| GET | `/chat/stream?message=你好` | SSE 流式对话，逐字返回（供 `EventSource` 消费） |

> ⚠️ **`/chat/stream` 不要在浏览器地址栏直接打开** —— 它返回的 `text/event-stream` 不含 charset，
> 浏览器当纯文本渲染时按默认编码解码 → 中文乱码。这是 SSE 的用法问题（机制见踩坑 #7），
> 正确入口是页面 `/`。

## v0.1 实测验收（2026-10-13）

| 指标 | 实测值 |
|---|---|
| `/chat` 同步（含首次冷加载） | 20.55s |
| `/chat/stream` 首字延迟 | **0.35s**（模型已热时 0.76s / 2.24s 波动，量级一致） |
| 流式事件数 / 总耗时 | 30 / 3.88s |
| 中文流式 | 正常，无乱码 |
| 内置页面 `/` | HTTP 200，`<meta charset="UTF-8">`，`EventSource` 正常拿到全文 |
| `ollama ps` | `qwen3:8b 6.3 GB 100% GPU CONTEXT 8192` |
| Ollama server config | `OLLAMA_IGPU_ENABLE:1` / `OLLAMA_MODELS:D:\Tools\ollama-models` / `OLLAMA_VULKAN:true` 三项正常 |

`think: false` 生效：回答直接给结论，不被 qwen3 的 ` thinking` 推理段污染。

## 踩坑记录（实测得来，非推测）

**1. Boot 4 的 starter 改名**
`spring-boot-starter-web` → `spring-boot-starter-webmvc`；测试用 `spring-boot-starter-webmvc-test`。
照抄 3.x 的 pom 会直接报依赖不存在。

**2. Spring AI 2.0 的配置属性有两套名字并存**
扁平写法 `spring.ai.ollama.chat.model`（新）与 `spring.ai.ollama.chat.options.model`（兼容保留）**都有效** ——
这解释了官方文档正文与示例自相矛盾。用扁平写法。权威来源是从
`spring-ai-autoconfigure-model-ollama-<ver>.jar` 里的 `META-INF/spring-configuration-metadata.json` 读，不要猜。

**3. `SERVER__PORT` 环境变量会静默覆盖 `server.port`**
在 WorkBuddy 的终端里跑时，环境里带着 `SERVER__PORT=54428`（宿主自己的服务端口）。
Spring Boot 宽松绑定把它解析成 `server.port`，**优先级高于 application.yml**，
于是 Tomcat 被拉到 54428 并和宿主进程撞端口，报 "Port 54428 was already in use"。
在普通 CMD/PowerShell/IDEA 里不存在这个问题（IDEA 从 explorer.exe 继承环境，拿不到该变量）。
若在受污染终端里跑：`unset SERVER__PORT` 或显式 `-Dspring-boot.run.arguments=--server.port=8080`。

**4. 本机 `http_proxy` 会拦截对 localhost 的请求**
环境里 `http_proxy=http://127.0.0.1:62667`，curl / Python urllib 默认走代理，探活会得到 502 或超时。
测本地接口要用 `curl --noproxy '*'`，Python 用 `ProxyHandler({})`。
（补充：Java **不读** `http_proxy` 环境变量，只认 `-Dhttp.proxyHost` 系统属性，所以这个坑不影响 Spring Boot 应用本体。）

**5. Boot 4 父 pom 只提供 `maven.compiler.release`，不再提供 `source`/`target` —— 在 IDEA 里会退回 Java 5 语法级别**
Boot 3.x 的 `spring-boot-starter-parent` 同时定义 `maven.compiler.source`/`target`；**Boot 4.x 只定义 `maven.compiler.release`**。
而 **IDEA 的 Maven 导入器只认 source/target**（不认 release），拿不到就回落到历史默认值 `JDK_1_5`。
症状极具迷惑性：源码本身完全正常（命令行 `./mvnw compile` 一直 BUILD SUCCESS），
IDEA 里却报一片「未结束的字符串文字 / 非法字符 '\u3002'」—— 因为 `-source 1.5` 不认识文本块 `"""`，
被当成「空字符串 + 未闭合引号」，后面所有中文都成了字符串外的非法字符。
修法：在 pom 的 `<properties>` 显式补 `maven.compiler.source`/`target`（已在根 pom 落地，带注释）。
IDEA 日志佐证：`JavaBuilder - Target bytecode version 5 is not supported by SDK 17`。

**6. IDEA 2025.3.1 的 Maven 导入服务与 wrapper 的 Maven 3.10.0 不兼容**
症状：Maven 面板能看到工程树，但 **External Libraries 里只有 JDK、一个依赖都没有**；
`idea.log` 里有 `com.google.inject.CreationException: No implementation for ArtifactHandlerManager was bound`
（IDEA 的 `RemoteMavenServer36` / `Maven3XServerEmbedder.customizeComponents` 用 3.6 时代代码定制 Maven 3.10 的
Guice/Sisu 容器，`DefaultArtifactFactory` 在 3.9+ 已不再绑定）。导入崩溃 → 依赖模型缺失，
**语言级别退回 1.5 也是同一根因的症状**（effective model 没读全）。
修法：把 `.mvn/wrapper/maven-wrapper.properties` 的 `distributionUrl` 从 3.10.0 降到 **3.9.11**
（恰为 IDEA 2025.3.1 自带并实测的版本，`plugins/maven/lib/maven3/lib/maven-core-3.9.11.jar`）。
已验证：`./mvnw clean compile` 在 3.9.11 + JDK 17 下 BUILD SUCCESS。
若 IDEA 里仍异常，把 Maven home path 从「Use Maven wrapper」改成「Bundled (Maven 3)」也是 3.9.11，
与 CLI 同版本、共享 `~/.m2/repository`，无任何不一致。
残余的旧版缓存 `~/.m2/wrapper/dists/apache-maven-3.10.0*`（两份，约 20MB）可手动删。

**7. SSE 响应头不带 charset —— 浏览器地址栏直连 `/chat/stream` 会中文乱码**
`@GetMapping(produces = "text/event-stream;charset=UTF-8")` 里的 `;charset=UTF-8` **会被丢掉**。
实测响应头：同步接口 `text/plain;charset=UTF-8`（正常），流式接口却是 `text/event-stream`（无 charset）。

机制（读 `spring-webmvc-7.0.9.jar` 字节码确认，非推测）：Spring MVC 检测到 produces 含 `text/event-stream` 后，
由 `ReactiveTypeHandler` 创建 `SseEmitter`（只传 timeout，**不传 MediaType**），
而 `SseEmitter` 把 content type 硬编码为 `MediaType.TEXT_EVENT_STREAM`，与 produces 里写的参数无关。

- **这是用法问题，不是 Bug**：浏览器地址栏打开时把它当普通文本文档渲染，无 charset 就退回默认编码（ISO-8859-1）→ 乱码。
- **正确消费方式**：`EventSource` 按 SSE 规范强制 UTF-8 解码，不会乱码 → 见 `static/index.html`。

**8. Flux 正常结束时 EventSource 会自动重连，必须发结束标记**
Flux 完成后 Spring 只是关闭连接，`EventSource` 认为「断线」并按默认 3s 重试 → 同一请求被反复触发、
模型反复推理（吃满显存还费电）。修法：接口改为返回 `Flux<ServerSentEvent<String>>`，末尾
`concatWith` 一个 `ServerSentEvent.builder().event("done").build()`；
前端 `es.addEventListener('done', () => es.close())` 主动断开，阻止重连。
（另一道保险：`onerror` 里也立刻 `close()`。）

## 版本路线

| 版本 | 新增能力 | 周次 |
|---|---|---|
| v0.1 | 对话服务 | W1 |
| v0.2 | 多轮记忆 | W2–W3 |
| v0.3 | 基础 RAG | W4–W9 |
| v0.4 | 混合检索 + Rerank | W10–W11 |
| v0.5 | 生产化（缓存 / 限流 / 可观测） | W12–W16 |
| v0.6 | MCP 工具集成 | W17–W19 |
| v0.7 | 模型网关 | W20–W21 |
| v1.0 | 交付 | W22–W24 |
