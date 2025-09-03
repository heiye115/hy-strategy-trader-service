打包:
mvn clean install -Dmaven.test.skip=true

启动方式:
setsid java -Djasypt.encryptor.password=* -jar hy-strategy-trader-service.jar > app.log 2>&1 &

