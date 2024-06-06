package io.cloud.cli

import com.monovore.decline.Opts
import com.monovore.decline.effect.*
import cats.effect.*
import cats.implicits.*

object AppCmds:

  trait Cmd:
    def cmd: String

  case class StackDeploy(stack: String) extends Cmd:
    def cmd = s"deploy $stack}"

  case class ServiceUpdate(stack: String, service: String) extends Cmd:
    def cmd = s"docker service update --force ${stack}_${service}"

  case class ServicePS(service: String) extends Cmd:
    def cmd = s"ps $service"

  case class ServiceRemove(service: String) extends Cmd:
    def cmd = s"stop $service"

  case class ServiceList()extends Cmd:
    def cmd = s"ls"

  case class ServiceGetLogs(service: String)extends Cmd:
    def cmd = s"getlogs $service"

  case class StackRemove(stack: String)extends Cmd:
    def cmd = s"rm $stack"

  case class DockerPrune() extends Cmd:
    def cmd = "docker system prune -a -f"

  case class DockerPS() extends Cmd:
    def cmd = "docker ps"

  case class DockerDF() extends Cmd:
    def cmd = "docker df"

  case class ServiceStop(service: String) extends Cmd:
    def cmd = s"stop $service}"

  case class DockerStats() extends Cmd:
    def cmd = "docker stats --no-stream --no-trunc"

  trait CodeBuild extends Cmd:
    def cmd = ""
  case class CodeBuildInc(ecrName: String) extends CodeBuild
  case class CodeBuildDec(ecrName: String) extends CodeBuild
  case class CodeBuildCurr(ecrName: String) extends CodeBuild
  case class CodeBuildStart(projectName: String) extends CodeBuild:
    override def cmd = s"aws codebuild start-build --project-name $projectName"
  case class CodeBuildShowBuildId(projectName: String) extends CodeBuild:
    override def cmd =  s"aws codebuild list-builds-for-project --project-name $projectName"
  case class CodeBuildStop(projectName: String) extends CodeBuild:
    override def cmd = s"aws codebuild stop-build --id __build_id__"
  case class CodeBuildInfo(projectName: String) extends CodeBuild:
    override def cmd = s"aws codebuild batch-get-builds --ids __build_id__"
  case class CodeBuildStatus(projectName: String) extends CodeBuild:
    override def cmd =  s"aws codebuild batch-get-builds --ids __build_id__"
  case class CodeBuildProjects() extends CodeBuild:
    override def cmd = s"aws codebuild list-projects"


  val codebuildInc: Opts[CodeBuild] =
    Opts.subcommand("inc", "Increment service version") {
      Opts.argument[String](metavar = "ECR repository name")
        .map(CodeBuildInc.apply)
    }

  val codebuildDec: Opts[CodeBuild] =
    Opts.subcommand("dec", "Decrement service version") {
      Opts.argument[String](metavar = "ECR repository name")
        .map(CodeBuildDec.apply)
    }

  val codebuildCurr: Opts[CodeBuild] =
    Opts.subcommand("curr", "Show current service version") {
      Opts.argument[String](metavar = "ECR repository name")
        .map(CodeBuildCurr.apply)
    }

  val codebuildStart: Opts[CodeBuild] =
    Opts.subcommand("start", "Start build (AWS CodeBuild)") {
      Opts.argument[String](metavar = "AWS CodeBuild project name")
        .map(CodeBuildStart.apply)
    }

  val codebuildStop: Opts[CodeBuild] =
    Opts.subcommand("stop", "Stop build (AWS CodeBuild)") {
      Opts.argument[String](metavar = "AWS CodeBuild project name")
        .map(CodeBuildStop.apply)
    }

  val codebuildInfo: Opts[CodeBuild] =
    Opts.subcommand("info", "Show build info (AWS CodeBuild)") {
      Opts.argument[String](metavar = "AWS CodeBuild project name")
        .map(CodeBuildInfo.apply)
    }

  val codebuildStatus: Opts[CodeBuild] =
    Opts.subcommand("status", "Show build status (AWS CodeBuild)") {
      Opts.argument[String](metavar = "AWS CodeBuild project name")
        .map(CodeBuildStatus.apply)
    }

  val codebuildList: Opts[CodeBuild] =
    Opts.subcommand("projects", "List all projects (AWS CodeBuild)") {
      Opts(CodeBuildProjects())
    }

  val codebuild: Opts[CodeBuild] =
    Opts.subcommand("cb", "CodeBuild actions") {
      (codebuildInc
        orElse codebuildDec
        orElse codebuildCurr
        orElse codebuildStart
        orElse codebuildStop
        orElse codebuildInfo
        orElse codebuildStatus
        orElse codebuildList)
    }

  val stackDeploy: Opts[StackDeploy] =
    Opts.subcommand("deploy", "Swarm stack deploy") {
      Opts.argument[String](metavar = "stack name").map(StackDeploy.apply)
    }

  val stackRemove: Opts[StackRemove] =
    Opts.subcommand("rm", "Swarm stack remove") {
      Opts.argument[String](metavar = "stack name").map(StackRemove.apply)
    }

  val serviceRemove: Opts[ServiceRemove] =
    Opts.subcommand("stop", "Swarm service remove") {
      Opts.argument[String](metavar = "service name").map(ServiceRemove.apply)
    }

  val serviceUpdate: Opts[ServiceUpdate] =
    Opts.subcommand("update", "Swarm force service update") {
      (Opts.argument[String](metavar = "stack name"),
        Opts.argument[String](metavar = "service name")).mapN(ServiceUpdate.apply)
    }

  val servicePS: Opts[ServicePS] =
    Opts.subcommand("ps", "Swarm show service info") {
      Opts.argument[String](metavar = "service name").map(ServicePS.apply)
    }

  val serviceLS: Opts[ServiceList] =
    Opts.subcommand("ls", "Swarm list all services") {
      Opts(ServiceList())
    }

  val serviceRM: Opts[ServiceStop] =
    Opts.subcommand("ls", "Swarm stop service") {
      Opts.argument[String](metavar = "service name").map(ServiceStop.apply)
    }

  val serviceGetLogs: Opts[ServiceGetLogs] =
    Opts.subcommand("get-logs", "Swarm get service logs") {
      Opts.argument[String](metavar = "service name").map(ServiceGetLogs.apply)
    }

  val dockerPrune: Opts[DockerPrune] =
    Opts.subcommand("prune", "Docker remove unused data") {
      Opts(DockerPrune())
    }

  val dockerPS: Opts[DockerPS] =
    Opts.subcommand("ps", "Docker lists containers") {
      Opts(DockerPS())
    }

  val dockerDF: Opts[DockerDF] =
    Opts.subcommand("df", "Docker file system space usage") {
      Opts(DockerDF())
    }

  val dockerStats: Opts[DockerStats] =
    Opts.subcommand("stats", "Docker containers resource usage statistics") {
      Opts(DockerStats())
    }

  val cmds = (
    stackDeploy
      orElse stackRemove
      orElse serviceRemove
      orElse serviceUpdate
      orElse servicePS
      orElse serviceLS
      orElse serviceRM
      orElse serviceGetLogs
      orElse dockerPrune
      orElse dockerPS
      orElse dockerDF
      orElse dockerStats
      orElse codebuild)


