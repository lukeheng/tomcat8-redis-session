# tomcat8-redis-session
tomcat8 session 共享
参考教程 http://www.cnblogs.com/interdrp/p/4056525.html
https://github.com/jcoleman/tomcat-redis-session-manager
感谢原作者
原文中 不支持tomcat8
解决 异常 (方法参见 RedisSessionManager 334行)
"Race condition encountered: attempted to load session错误!!![" + id + "] which has been created but not yet serialized."
