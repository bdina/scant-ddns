# scant-ddns
A hardly sufficient Dynamic DNS updater

### Requirements
this project requires `Java 11` and `Scala 2.13.6`

## Create a native image (of shadowJar)
1. use gradle to create a build of the shadow jar:
```
gradle shadowJar
```
2. execute graalvm `native-image` from the command line, for example:
```
native-image --static -jar build/libs/ddns-<version>.jar
```
*or*
execute a docker build (no uberJar build step required):
```
docker build -f Dockerfile.native --tag ddns:<version> .
```
