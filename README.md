# 一个可以动态改变feign client url的agent工具,适用于微服务开发使用

1. idea vm启动参数添加
-javaagent:/path/redirect-agent-1.0.0-SNAPSHOT.jar=configFile=/path/[config_example.yml](config_example.yml)
2. `Shorten command line`下拉框配置为`JAR Manifest`



