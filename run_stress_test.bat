@echo off
chcp 65001 > nul
echo ========================================
echo 多人游戏压力测试
echo ========================================
echo.
echo 重要提示：
echo 1. 请先在另一个终端窗口运行服务器 run.bat
echo    选择游戏模式：输入 2 (多人模式)
echo    输入玩家数量：输入 8 (必须是8)
echo.
echo 2. 等待服务器完全启动
echo    (显示"多人游戏服务器已启动，端口：12345")
echo.
echo 3. 然后按任意键开始测试
echo.
pause

echo.
echo 正在编译测试代码...
javac -d bin -encoding UTF-8 src\test\*.java src\*.java src\client\*.java src\server\*.java src\network\*.java src\io\*.java

if %ERRORLEVEL% NEQ 0 (
    echo 编译失败！
    pause
    exit /b 1
)

echo 编译成功！
echo.
echo 正在运行压力测试...
echo 注意：测试将固定创建 8 个客户端模拟并发操作（每轮8个）
echo.

cd bin
java -cp . test.MultiPlayerStressTest

echo.
echo 测试完成！
pause
