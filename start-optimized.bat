@echo off
echo 启动合同管理系统（优化版本）...

REM 设置JVM参数以优化性能和解决中文编码问题
set JAVA_OPTS=-Xms512m -Xmx2048m
set JAVA_OPTS=%JAVA_OPTS% -XX:+UseG1GC
set JAVA_OPTS=%JAVA_OPTS% -XX:MaxGCPauseMillis=200
set JAVA_OPTS=%JAVA_OPTS% -XX:G1HeapRegionSize=16m
set JAVA_OPTS=%JAVA_OPTS% -XX:+UseStringDeduplication
set JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8
set JAVA_OPTS=%JAVA_OPTS% -Dconsole.encoding=UTF-8
set JAVA_OPTS=%JAVA_OPTS% -Dspring.profiles.active=prod
set JAVA_OPTS=%JAVA_OPTS% -Djava.net.preferIPv4Stack=true

echo JVM参数: %JAVA_OPTS%

REM 启动应用
java %JAVA_OPTS% -jar target/ContractManagementSystem-0.0.1-SNAPSHOT.jar

pause 