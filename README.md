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
codebuild.url=
codebuild.username=
codebuild.password=

```

# Usage

```shell

Usage:
    cloud deploy
    cloud rm
    cloud stop
    cloud update
    cloud ps
    cloud ls
    cloud get-logs
    cloud docker-prune
    cloud docker-ps
    cloud docker-df
    cloud docker-stats
    cloud cb

Subcommands:
    deploy
        Swarm stack deploy
    rm
        Swarm stack remove
    stop
        Swarm service remove
    update
        Swarm force service update
    ps
        Swarm show service info
    ls
        Swarm list all services
    get-logs
        Swarm get service logs
    docker-prune
        Docker remove unused data
    docker-ps
        Docker lists containers
    docker-df
        Docker file system space usage
    docker-stats
        Docker containers resource usage statistics
    cb
        CodeBuild actions
```
