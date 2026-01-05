@echo off
chcp 65001 > nul
echo ========================================
echo 简单连接测试
echo ========================================
echo.
echo 此测试用于验证客户端和服务器的协议通信
echo.
echo 使用方法：
echo 1. 在另一个终端运行 run.bat
echo    - 选择模式 2 (多人模式)
echo    - 输入玩家数 2
echo 2. 等待服务器启动完成
echo 3. 然后运行此脚本
echo.

echo 正在编译测试代码...
javac -d bin -encoding UTF-8 src\test\SimpleConnectionTest.java src\network\*.java

if %ERRORLEVEL% NEQ 0 (
    echo 编译失败！
    pause
    exit /b 1
)

echo 编译成功！
echo.

cd bin
java -cp . test.SimpleConnectionTest

cd ..
echo.
pause
