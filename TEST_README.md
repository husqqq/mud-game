# 多人游戏压力测试

## 概述

这个压力测试脚本用于自动测试多人游戏的并发性能和稳定性。它会模拟多个客户端同时连接服务器，执行各种游戏操作，以检测潜在的并发bug。

## 测试内容

### 自动化操作
每个测试客户端会自动执行以下操作：

1. **连接服务器**
2. **注册账号**（使用时间戳生成唯一用户名）
3. **登录**
4. **创建角色**
   - 自动分配属性（平均分配）
   - 随机选择主技能（刀法/剑法/拳法）
5. **执行游戏操作**（3-5个随机操作）：
   - 查看状态
   - 修炼
   - 与NPC战斗
   - 进入决斗池（PvP）
   - 退出游戏
6. **断开连接**

### 测试场景

- **客户端数量**：每轮测试随机创建 2-8 个客户端
- **测试轮次**：默认运行 5 轮测试
- **并发操作**：所有客户端同时执行操作，模拟真实并发场景

## 如何运行

### Windows

直接双击运行：
```batch
run_stress_test.bat
```

或在命令行中：
```batch
run_stress_test.bat
```

### 手动运行

```batch
# 1. 编译
javac -d bin -encoding UTF-8 src\test\*.java src\*.java src\client\*.java src\server\*.java src\network\*.java src\io\*.java

# 2. 运行测试
cd bin
java -cp . test.MultiPlayerStressTest
```

## 测试结果

测试完成后会输出：

```
========================================
测试完成！
========================================
成功操作数: X
错误操作数: Y

错误日志:
  - 客户端 1 错误: ...
  - 客户端 2 错误: ...
```

## 测试指标

### 成功标准
- ✅ 所有客户端能正常连接
- ✅ 所有客户端能完成注册和登录
- ✅ 角色创建无冲突
- ✅ 游戏操作正常执行
- ✅ 决斗池能正确处理多人并发
- ✅ 无死锁或线程阻塞
- ✅ 无数据竞争或状态不一致

### 常见问题检测

1. **死锁**
   - 检测：客户端长时间无响应（超过120秒）
   - 表现：测试卡住，无法完成

2. **竞态条件**
   - 检测：数据不一致、重复处理
   - 表现：错误日志中出现状态异常

3. **内存泄漏**
   - 检测：多轮测试后观察内存使用
   - 表现：内存持续增长

4. **连接泄漏**
   - 检测：服务器端连接数异常增长
   - 表现：新客户端无法连接

5. **并发安全**
   - 检测：`ConcurrentModificationException`
   - 表现：集合操作失败

## 调试建议

### 增加详细日志

修改 `MultiPlayerStressTest.java`：

```java
private void handleMessage(GameMessage message) {
    // 启用详细日志
    System.out.println("[客户端 " + clientId + "] 收到: " + 
                       message.getType() + " - " + message.getData());
}
```

### 调整测试参数

在 `MultiPlayerStressTest.java` 中修改：

```java
private static final int MIN_CLIENTS = 2;  // 最小客户端数
private static final int MAX_CLIENTS = 8;  // 最大客户端数
private static final int totalRounds = 5;  // 测试轮次
```

### 单独测试特定场景

注释掉其他操作，只测试特定功能：

```java
private void performGameActions() throws Exception {
    // 只测试决斗池
    performArenaBattle();
}
```

## 扩展测试

### 添加新的测试场景

在 `TestClient` 类中添加新方法：

```java
private void performCustomTest() throws Exception {
    // 自定义测试逻辑
}
```

### 增加压力等级

```java
// 增加客户端数量
private static final int MAX_CLIENTS = 20;

// 增加操作次数
int numActions = 10 + random.nextInt(10);
```

## 注意事项

1. **端口占用**：确保端口 12345 未被占用
2. **资源清理**：测试会自动清理，但建议定期重启
3. **数据库清理**：测试会创建大量用户，可能需要清理
4. **日志文件**：长时间测试可能产生大量日志

## 性能基准

在标准配置（8核CPU，16GB内存）下：

- ✅ **2 客户端**：应无压力运行
- ✅ **4 客户端**：正常运行
- ✅ **8 客户端**：可接受的性能
- ⚠️ **16+ 客户端**：可能出现延迟

## 故障排除

### 测试无法启动
- 检查端口是否被占用
- 确认所有依赖已编译

### 客户端超时
- 增加超时时间（在代码中修改）
- 检查服务器性能

### 频繁出现错误
- 查看错误日志详情
- 启用详细日志模式
- 单独测试失败的操作

## 持续集成

可以将此测试集成到CI/CD流程：

```yaml
# GitHub Actions 示例
- name: Run Stress Test
  run: |
    ./run_stress_test.bat
    # 检查退出码
    if [ $? -ne 0 ]; then exit 1; fi
```

## 贡献

欢迎提交新的测试场景和改进建议！
