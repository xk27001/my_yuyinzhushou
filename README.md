# MyYuyin 语音交互助手

这是一个 Java 21 编写的离线语音交互助手，不接入 LLM，也不使用 Ollama。服务端运行在 Umbrel/Docker 中，客户端运行在带麦克风的电脑上。

- 服务端：Spring Boot、MySQL 8、Vosk 离线中文识别、eSpeak NG 离线语音合成、WebSocket、循环闹钟。
- 客户端：Java Swing、Java Sound、WebSocket，负责录音、播放、状态监控和参数调整。
- 数据原则：业务表全部包含 `sfyx`；查询只读取 `sfyx = 1`；删除通过 `UPDATE ... SET sfyx = 0` 完成。
- 参数原则：能持久化的参数都写入 `va_config`；命令词和回复模板写入 `va_command_rule`；唤醒词写入 `va_wake_word`。

## 已实现功能

1. 唤醒词：`三角洲`
2. 唤醒回复：`我在`
3. 时间查询：说“三角洲几点了”，回复格式为 `现在19点50分，重复现在19点50分。星期一`
4. 循环闹钟：说“三角洲定一个30分钟的闹钟”，回复 `30分钟闹钟已设置。`
5. 重复设置：回复 `已有30分钟闹正在运行，请先取消。`
6. 剩余时间：说“三角洲还剩多久”，回复格式为 `还剩8分钟，重复还剩8分钟`
7. 取消闹钟：说“三角洲取消闹钟”，回复 `闹钟已取消`
8. 循环闹钟到达时间后自动播报并重新计算下一次触发时间。
9. 客户端显示服务端连接、实时通道、监听状态、接口延迟、客户端资源、当前闹钟和最近识别/回复。
10. 客户端可修改数据库中的唤醒、会话、TTS、音频阈值等参数。

## 目录结构

```text
assistant-common/  公共请求和响应对象
assistant-server/  Spring Boot 服务端、Vosk、TTS、MySQL、WebSocket
assistant-client/  Swing 客户端、录音、播放、参数监控
docker/            多架构镜像 Dockerfile
umbrel-app-store.yml  Umbrel 社区商店清单
myyuyin-assistant/    Umbrel 应用清单、图标和 docker-compose.yml
scripts/            构建、运行、建库和 Umbrel 镜像发布脚本
```

## 服务端工作原理

```text
客户端麦克风 -> 16 kHz / 单声道 / 16 位 PCM WAV
             -> POST /api/v1/voice/process
             -> Vosk 中文识别
             -> 手工命令/正则规则匹配（数据库）
             -> 时间或循环闹钟业务逻辑
             -> eSpeak NG 生成 WAV
             -> 返回文本和 Base64 WAV
             -> 客户端播放并显示
```

循环闹钟由服务端定时扫描 `va_alarm.next_fire_at`。到达时间后通过 WebSocket 把文本和 WAV 推送到对应客户端，同时更新 `last_fired_at` 和下一次 `next_fire_at`。

## 环境要求

- JDK 21 或更高版本
- Maven 3.9+（Maven 4 RC 也可构建）
- MySQL 8
- 本地运行服务端时需要 Vosk 中文模型和 `espeak-ng`
- Docker/Umbrel 部署时上述依赖已经包含在 `docker/Dockerfile`

Vosk 小模型下载地址：

```text
https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip
```

解压后目录应重命名为 `models/vosk-model-small-cn`，或者通过 `VOSK_MODEL_PATH` 指定实际目录。

## 数据库

先复制配置模板，再把真实的 MySQL 连接信息写入本地 `.env`：

```powershell
Copy-Item .env.example .env
```

`.env` 已被 `.gitignore` 忽略，不要提交。服务端启动时会先连接 MySQL 实例并执行 `CREATE DATABASE IF NOT EXISTS`，然后由 Flyway 自动执行 `V1__init_schema.sql` 和后续迁移建表并写入种子数据。也可以先手动执行：

```powershell
mysql -h <DB_HOST> -u <DB_USER> -p < scripts/init.sql
```

数据库表：

- `va_config`：系统参数、语音参数、客户端参数。
- `va_command_rule`：命令别名、正则、回复模板、启停状态。
- `va_wake_word`：唤醒词。
- `va_device`：客户端设备及在线状态。
- `va_runtime_status`：预留的客户端运行指标。
- `va_session`：唤醒后的会话状态。
- `va_alarm`：循环闹钟。
- `va_interaction_log`：交互记录。

所有表都有 `sfyx`。删除配置、规则、唤醒词、闹钟、日志和设备时，应设置 `sfyx = 0`，不要物理删除。

## 本地构建和运行

```powershell
# 全量测试和打包
mvn test
mvn -DskipTests package

# 运行服务端
.\scripts\run-server.ps1

# 运行客户端
.\scripts\run-client.ps1
```

构建产物：

```text
assistant-server/target/assistant-server-1.0.0.jar
assistant-client/target/assistant-client-1.0.0-jar-with-dependencies.jar
```

客户端启动参数可以通过系统属性或启动配置覆盖：

```properties
server.url=http://umbrel.local:8080
device.code=client-001
client.name=客厅电脑
```

也可以把该文件放到 `%USERPROFILE%\.myyuyin\assistant-client.properties`。这里只保留连接服务端所需的引导信息；业务参数统一从数据库读取和保存。

客户端操作：

1. 在顶部填写服务端地址、设备编号、客户端名称，点击“连接 / 重连”。
2. 选择麦克风。点击“开始连续监听”后说“三角洲”，等回复“我在”，再说“几点了”等命令。
3. 也可以点击“按住说话”，松开后上传录音。
4. “参数配置”页修改值后点击“保存修改”，值会写回 `va_config`。
5. “文本测试”页可以不使用麦克风直接测试手工回复逻辑。

## Umbrel 部署

### 1. 构建并推送 ARM64 镜像

树莓派 Umbrel 使用 ARM64。Dockerfile 在镜像构建阶段从与 Java 端相同版本的 Vosk 0.3.45 ARM64 wheel 中提取 `libvosk.so`，并通过 JNA 加载，不需要修改 Java 识别代码。发布脚本默认构建 `linux/arm64`：

```powershell
.\scripts\publish-umbrel.ps1 -Image ghcr.io/你的账号/myyuyin-assistant-server:1.0.2
```

该脚本使用 Docker Buildx 构建 `linux/arm64` 并推送到镜像仓库。在 x86 电脑上构建 ARM64 镜像时，Docker Desktop/Buildx 需要启用 QEMU；直接在树莓派上构建最稳妥。当前工作机如果没有 Docker，需要先在安装 Docker 的树莓派或构建机执行该命令。

### 2. 准备 Umbrel 应用目录

仓库根目录已经包含 `umbrel-app-store.yml`，应用位于 `myyuyin-assistant/`。在 Umbrel 的“应用商店设置”中添加仓库地址 `https://github.com/xk27001/my_yuyinzhushou` 即可识别。Umbrel 版内置 MySQL 8.4，使用系统生成的 `APP_PASSWORD`，不需要配置外部数据库。

如镜像使用私有仓库，需要在 Umbrel 上执行 `docker login`。MySQL 仍使用你提供的阿里云 RDS，不需要在 Umbrel 内运行数据库。

### 3. 启动

在 Umbrel 的对应应用目录执行：

```bash
export APP_SERVER_IMAGE=ghcr.io/你的账号/myyuyin-assistant-server:1.0.2
docker compose up -d
```

健康检查：

```text
http://umbrel.local:8080/actuator/health
```

如果 Umbrel 使用应用代理，服务地址就是 `umbrel-app.yml` 中配置的端口 `8080`。

> 当前工作机没有安装 Docker，也没有你的镜像仓库和 Umbrel SSH 凭据，因此已经生成完整 Docker/Umbrel 部署文件，但未替我执行镜像推送或远程启动。数据库建库建表已经在提供的 MySQL 上实际执行成功。

## 语音识别和语音合成

- 语音识别：Vosk `vosk-model-small-cn-0.22`，完全离线，不是 LLM。
- 语音合成：`espeak-ng -v zh`，完全离线，不是 LLM。
- Docker 构建时自动下载 Vosk 模型并安装 eSpeak NG。
- 本机开发时如果没有 `espeak-ng`，服务端仍会返回正确文本，但 `audioWavBase64` 为空。客户端会显示文本，无法播放合成音频。
- Docker/Umbrel 里默认为客户端播放。若服务端主机有可用音频设备，可把 `audio.server.playback.enabled` 配置为 `true`。

## 主要接口

```text
GET  /actuator/health
POST /api/v1/voice/process                  multipart: deviceCode, clientName, audio
POST /api/v1/text/process                   JSON: deviceCode, clientName, text
GET  /api/v1/devices/{deviceCode}
DELETE /api/v1/devices/{deviceCode}
POST /api/v1/devices/{deviceCode}/heartbeat
GET  /api/v1/devices/{deviceCode}/logs
GET  /api/v1/config
PUT  /api/v1/config/{key}
POST /api/v1/config
DELETE /api/v1/config/{key}
GET  /api/v1/wake-words
POST /api/v1/wake-words/{word}
DELETE /api/v1/wake-words/{word}
GET  /api/v1/alarms/{deviceCode}/active
DELETE /api/v1/alarms/{deviceCode}/active
WS   /ws/devices?deviceCode={deviceCode}
```

## 常见问题

### 服务端启动失败

- 确认 MySQL 8 账号具有 `CREATE` 权限；如果数据库已经建好，只执行应用迁移即可。
- 阿里云 RDS 白名单需要允许 Umbrel 所在网络的公网出口 IP。
- 修改数据库参数后，必须重启服务端或重新执行迁移以确认种子数据。

### 客户端能识别但听不到声音

- Docker 容器已经内置 `espeak-ng`，检查服务端日志是否有 TTS 警告。
- 查看文本回复中的 `audioWavBase64` 是否为空。
- 客户端需要在本地操作系统中有可用的播放设备。
- 如果要让服务端容器直接播放，宿主机必须映射音频设备，Umbrel 默认无此能力。

### 说唤醒词没有反应

- 客户端必须处于“连续监听中”或使用“按住说话”。
- 第一次说“三角洲”后，服务端只返回“我在”，会话默认保持 15 秒。
- 可以直接说“三角洲几点了”或“三角洲定一个30分钟的闹钟”。
- 可以在 `va_config.session.wait_ms` 调整唤醒后的等待时间。

## 安全说明

本地开发使用的数据库连接信息只保存在 `.env`，该文件已被 `.gitignore` 忽略；仓库只提交 `.env.example` 占位模板。Umbrel 商店版使用内置 MySQL 8.4，数据库密码由 Umbrel 自动生成并保存在本机应用设置中。

客户端只保留连接地址、设备编号和客户端名称作为引导配置；业务参数都来自数据库。
