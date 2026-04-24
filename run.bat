@echo off

set JAVA_OPTS=-Xms64m -Xmx64m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:9000

:: activate HotswapAgent when using Trava OpenJDK
:: https://github.com/TravaOpenJDK/trava-jdk-11-dcevm/releases
:: set JAVA_OPTS=%JAVA_OPTS% -XX:HotswapAgent=fatjar

set SPRING_PROFILES_ACTIVE=dev

:: use mvn for running application without building it
mvn spring-boot:run -Dspring-boot.run.jvmArguments="%JAVA_OPTS%" -Dlicense.skip=true