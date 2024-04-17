Infra CLI

### Usage

CLI server manager

## Create Graalvm configs 

```shell
java \
  -agentlib:native-image-agent=config-output-dir=./src/main/resources/META-INF/native-image/br.com.mobilemind.infra.cli/infra-cli \
  -jar target/scala-3.4.1/infra-cli-assembly-0.1.0-SNAPSHOT.jar
```

## Generate Graalvm image

```shell
native-image \
  --static \
  --verbose \
  --allow-incomplete-classpath \
  --report-unsupported-elements-at-runtime \
  --no-fallback \
  -jar ./target/scala-3.4.1/infra-cli-assembly-0.1.0-SNAPSHOT.jar
```

# Compile Graalvm native image via SBT

* sbt compileNative

# Configuration file

```shell
~/.infra-cli.cfg
hosts=srv1,srv2,srv3
host.main=srv1
host.domain=domain.com
ssh.username=cli-user
ssh.port=22
ssh.key.identity=$HOME/.ssh/cli_id_rsa
logs.path=$HOME/Downloads
```

# Options

```shell
  use options:
  > deploy <service name>
  > update <stack name> <service name>
  > prune
  > ps
  > ps <service name>
  > ls
  > stop <service name>
  > rm <stack name>
  > stats
  > service <cmd args>
  > docker <cmd args>
  > getlogs <service name>
```
