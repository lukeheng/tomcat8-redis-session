# tomcat8-redis-session

update 2020-04-23 22:22:32

时间过去很久了，当年是个小菜鸟。如今变成老菜鸟。

如今世面上有很多成熟的解决方案了。 比如spring-session-data-redis

-------------------------------------------------------------------------------------
tomcat8 session 共享
参考教程 http://www.cnblogs.com/interdrp/p/4056525.html

https://github.com/jcoleman/tomcat-redis-session-manager
感谢原作者
原文中 不支持tomcat8
解决 异常 (方法参见 RedisSessionManager 334行,解决方式比较暴力)
"Race condition encountered: attempted to load session错误!!![" + id + "] which has been created but not yet serialized."

使用方法:
首先，是配置tomcat，使其将session保存到redis上。有两种方法，也是在server.xml或context.xml中配置，不同的是memcached只需要添加一个manager标签，而redis需要增加的内容如下：（注意：valve标签一定要在manager前面。）

<Valve className="com.radiadesign.catalina.session.RedisSessionHandlerValve" />
<Manager className="com.radiadesign.catalina.session.RedisSessionManager"
         host="192.168.159.131"
         port="16300"
         password=""
         database="0"
         maxInactiveInterval="60"/>



第二步,将依赖的jar包和该项目打包好的jar包,共四个文件复制到tomcat根目录下的lib文件夹下
具体需要的jar包
commons-pool2-2.3.jar
jedis-2.7.2.jar
tomcat-juli-8.0.24.jar
tomcat8-redis-session-1.0-SNAPSHOT.jar
下载地址 http://download.csdn.net/download/qq_34511005/10132695
