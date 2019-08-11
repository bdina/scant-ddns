# scant-ddns
A hardly sufficient Dynamic DNS updater

### Requirements
this project requires `Java 8` and `Scala 2.12.9`

## Create a native image (of uberJar)
1. use gradle to create a build of the uberJar:
```
gradle uberJar
```
2. execute graalvm `native-image` from the command line:
```
native-image --enable-url-protocols=http,https -H:ReflectionConfigurationFiles=reflectconfig -jar build/libs/ddns-1.0.jar
```
*or*
execute a docker build (no uberJar build step required):
```
docker build -f Dockerfile.native .
```
