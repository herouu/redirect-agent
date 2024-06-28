# 一个可以动态改变feign client url的agent工具,适用于微服务开发使用

1. idea vm启动参数添加
-javaagent:/path/redirect-agent-1.0.0-SNAPSHOT.jar=configFile=/path/[config_example.yml](config_example.yml)
2. `Shorten command line`下拉框配置为`JAR Manifest`


# dns
### windows
* 清除系统的DNS缓存 ipconfig /flushdns
* 解析域名 nslookup baidu.com
* 指定dns服务器解析域名 nslookup baidu.com 114.114.114.114


