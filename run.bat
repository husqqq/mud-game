@echo off
chcp 65001 >nul
cd /d %~dp0

echo 正在编译...
javac -d bin -cp bin src\*.java src\io\*.java src\network\*.java src\server\*.java src\client\*.java 2>nul

echo 启动游戏...
java -cp bin main.Main

pause

