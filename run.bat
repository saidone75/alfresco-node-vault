@echo off

set JAVA_HOME=C:\Program Files\Java\jbr_jcef-21.0.6-windows-x64-b872.80

set JAVA_OPTS=-Xms1G -Xmx1G -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:9000

:: activate HotswapAgent when using Trava OpenJDK
:: https://github.com/TravaOpenJDK/trava-jdk-11-dcevm/releases
:: set JAVA_OPTS=%JAVA_OPTS% -XX:HotswapAgent=fatjar

set SPRING_PROFILES_ACTIVE=dev

:: use mvn for running application without building it
mvn spring-boot:run -Dspring-boot.run.jvmArguments="%JAVA_OPTS%" -Dlicense.skip=true