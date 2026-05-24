# 安全测试 curl 模板

这组脚本用于验证当前仓库里已经落地的两条权限边界：

1. 学生只能查自己。
2. 管理员只能走独立 `/system/admin/edu/**` 接口查任意学生。

## 1. 使用前准备

```bash
export BASE_URL="http://127.0.0.1:8080"
export TOKEN_STUDENT_A="replace-with-student-a-token"
export TOKEN_STUDENT_B="replace-with-student-b-token"
export TOKEN_COMMON="replace-with-common-token"
export TOKEN_ADMIN="replace-with-admin-token"
export STUDENT_ID_A="202301010001"
export STUDENT_ID_B="202301010002"
```

说明：

- `TOKEN_COMMON` 推荐对应“已登录但未绑定 student.user_id”的账号。
- 所有脚本都只提供模板，不会自动执行。
- 学生接口、管理员接口、公共聊天接口路径均来自当前仓库真实代码。

## 2. 学生侧脚本

| 脚本 | 作用 |
|---|---|
| `public-chat-anonymous.sh` | 匿名访问公共聊天 |
| `public-chat-grade-anonymous.sh` | 匿名问成绩/一卡通，验证不会触发学生工具 |
| `student-chat-anonymous-fail.sh` | 匿名访问学生聊天失败 |
| `student-chat-student-a-self.sh` | studentA 通过学生聊天查自己的成绩 |
| `student-chat-student-a-impersonate-b.sh` | studentA 诱导查询 studentB，仍只能查自己 |
| `student-chat-unbound-user.sh` | 未绑定学生身份账号访问学生聊天 |
| `edu-score-student-a.sh` | studentA 查自己成绩 |
| `edu-score-student-a-manual-studentid.sh` | studentA 手工追加 studentId，也不能越权 |
| `card-balance-student-a.sh` | studentA 查自己一卡通余额 |
| `legacy-stream-public.sh` | 旧 `/api/ai/chat/stream` 只做公共问答 |
| `legacy-edu-anonymous-fail.sh` | 旧教务接口匿名访问失败 |

## 3. 管理员侧脚本

| 脚本 | 作用 |
|---|---|
| `admin-score-student-a.sh` | admin 查询 studentA 成绩 |
| `admin-score-student-b.sh` | admin 查询 studentB 成绩 |
| `admin-card-balance-student-a.sh` | admin 查询 studentA 一卡通余额 |
| `admin-score-student-fail.sh` | studentA 访问管理员成绩接口失败 |
| `admin-card-balance-student-fail.sh` | studentB 访问管理员余额接口失败 |
| `admin-score-common-fail.sh` | userCommon 访问管理员接口失败 |
| `admin-score-anonymous-fail.sh` | 匿名访问管理员接口失败 |

## 4. 结果判断建议

1. `/system/edu/api/**` 只能返回当前登录学生自己的数据。
2. `/system/admin/edu/**` 只允许 `admin` 角色访问，查询目标由 `studentId` 指定。
3. `/api/ai/chat/public/stream` 和 `/api/ai/chat/stream` 只能是公共问答，不应返回真实学生个人数据。
4. 如果需要进一步确认聊天入口没有注册学生工具，建议同时观察服务端日志中的 `studentTools=true/false`。
