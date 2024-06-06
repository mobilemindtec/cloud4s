package io.cloud.cli

import com.jcraft.jsch.{Channel, ChannelExec, JSch}

import java.io.{File, FileNotFoundException}
import scala.io.Source
import scala.language.postfixOps
import scala.sys.process.*
import cats.effect.*
import cats.implicits.*
import com.monovore.decline.*
import com.monovore.decline.effect.*

import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.Base64
import AppCmds.*
import AppConfigs.Config
import io.cloud.cli.AppConfigs.Config

// buildOpts: Opts[BuildImage] = Opts(build)


object DockerApp extends CommandIOApp(
  name = "cloud",
  header = "Mobile Mind Cloud Tool",
  version = "0.0.1"
) {

  override def main: Opts[IO[ExitCode]] =
    cmds.map:
      c => runCmd(c)


  def runCmd(cmd: Cmd): IO[ExitCode] =
    for
      cfg <- AppConfigs.getConfigs()
      red <-
        given configs: Config = cfg
        cmd match
          case CodeBuildInc(ecrName) => codebuild.inc(ecrName)
          case CodeBuildDec(ecrName) => codebuild.inc(ecrName)
          case CodeBuildCurr(ecrName) => codebuild.curr(ecrName)
          case cb: CodeBuildStart => codebuild.start(cb)
          case cb: CodeBuildStop => codebuild.stop(cb)
          case cb: CodeBuildStatus => codebuild.status(cb)
          case cb: CodeBuildInfo => codebuild.info(cb)
          case cb: CodeBuildProjects => codebuild.projects(cb)
          case _ => IO.unit.as(ExitCode.Error)

    yield red

}
// https://ben.kirw.in/decline/usage.html

type IOResult = Config ?=> IO[ExitCode]

def say(s: String): IO[Unit] =
  IO.println(s"\n\n:: cloud ::> $s\n\n")

object codebuild:

  def inc(service: String): IOResult =
    cb(service, "increment")

  def dec(service: String): IOResult =
    cb(service, "decrement")

  def curr(service: String): IOResult =
    cb(service, "current")

  private def cb(service: String, action: String): IOResult =
    val cfg = summon[Config]
    val url = s"${cfg.codebuildUrl}/app/version/$action/$service"
    val auth = AuthBasic(cfg.codebuildUsername, cfg.codebuildPassword)
    Http(url, Some(auth))
      .getAsString
      .flatMap:
        resp =>
          say(s"Server response ${resp.statusCode()}: ${resp.body()}") *>
            (resp.statusCode() match
              case 200 => IO.unit.as(ExitCode.Success)
              case _ => IO.unit.as(ExitCode.Error))

  private def getBuildId(cb: CodeBuildShowBuildId): IO[String] =
    IO.blocking:
      val filterCmd = "jq -r '.ids[0]'"
      val buildId = ((cb.cmd #| filterCmd) !!)
      buildId

  def start(cb : CodeBuildStart): IOResult =
    IO.blocking((cb.cmd !!)).flatMap:
      r => say(r) *> IO.unit.as(ExitCode.Success)
  
  def status(cb: CodeBuildStatus): IOResult =
    getBuildId(CodeBuildShowBuildId(cb.projectName))
      .flatMap:
        bid =>
          if bid.isEmpty
          then say(s"build not found to project ${cb.projectName}")
          else
            IO.blocking:
              val cmd = cb.cmd.replace("__build_id__", bid)
              val filterCmd = "jq '.builds[].phases[] | select (.phaseType==\"BUILD\") | .phaseStatus'"
              ((cmd #| filterCmd) !!)
            .flatMap:
              r => say(s"build status: ${if r == "null" then "BUILDING" else r}")
      .flatMap:
        _ => IO.unit.as(ExitCode.Success)

  def info(cb: CodeBuildInfo): IOResult =
    getBuildId(CodeBuildShowBuildId(cb.projectName))
      .flatMap:
        bid =>
          if bid.isEmpty
          then say(s"build not found to project ${cb.projectName}")
          else
            IO.blocking:
              val cmd = cb.cmd.replace("__build_id__", bid)
              (cmd !!)
            .flatMap:
              r => say(s"build info:\n\n$r")
      .flatMap:
        _ => IO.unit.as(ExitCode.Success)

  def stop(cb: CodeBuildStop): IOResult =
    getBuildId(CodeBuildShowBuildId(cb.projectName))
      .flatMap:
        bid =>
          if bid.isEmpty
          then say(s"build not found to project ${cb.projectName}")
          else
            IO.blocking:
              val cmd = cb.cmd.replace("__build_id__", bid)
              (cmd !!)
            .flatMap:
              r => say(r)
      .flatMap:
        _ => IO.unit.as(ExitCode.Success)
  
  def projects(cb: CodeBuildProjects): IOResult =
    IO.blocking((cb.cmd !!)).flatMap:
      r => say(s"projects:\n\n$r") *> IO.unit.as(ExitCode.Success)
      
class docker:

  type Analyzer = Seq[String] => IO[Unit]

  def stackDeploy(opt: StackDeploy): IOResult =
    val cfg = summon[Config]
    runEach(cfg.hostMain, opt.cmd)

  def serviceUpdate(opt: ServiceUpdate): IOResult =
    val cfg = summon[Config]
    runEach(cfg.hostMain, opt.cmd)

  def servicePS(opt: ServicePS): IOResult =
    val cfg = summon[Config]
    runEach(cfg.hostMain, opt.cmd)

  def serviceRemove(opt: ServiceRemove): IOResult =
    val cfg = summon[Config]
    runEach(cfg.hostMain, opt.cmd)

  private def runEach(host: String, cmd: String, analyzer: Option[Analyzer] = None): IOResult =
    Ssh.connectAndExec(host, cmd) {
      lines =>
        analyzer match
          case Some(f) => f(lines)
          case None =>
            lines.foreach(println)
            IO.unit
    }
        
  private def exec(cmd: String, analyzer: Option[Analyzer], hosts: String*): IOResult =
    Ssh.configure() *>
      hosts.map:
        host => runEach(cmd, host, analyzer)
      .parSequence
      .flatMap:
        codes =>
          IO.pure {
            codes.find(_ != ExitCode.Success)
              .getOrElse(ExitCode.Success)
          }


      

  
/*

object commands:

  private def clusterCmd(cmd: String) =
    s"cd cluster && ./docker $cmd"

  def ps()(using config: Config) =
    exec("docker ps", config.hosts*)

  def df()(using config: Config) =
    exec("df", config.hosts*)

  def stats()(using config: Config) =
    exec("docker stats --no-stream --no-trunc", config.hosts*)

  def ls()(using config: Config) =
    exec(clusterCmd("ls"), config.hostMain)

  def service(args: Seq[String])(using config: Config) =
    exec(s"docker service ${args.mkString(" ")}", config.hostMain)

  def docker(args: Seq[String])(using config: Config) =
    exec(s"docker ${args.mkString(" ")}", config.hosts*)

  def rm(stack: String)(using config: Config) =
    exec(clusterCmd(s"rm $stack"), config.hostMain)

  def ps(service: String)(using config: Config) =
    exec(clusterCmd(s"ps $service"), config.hostMain)

  def stop(service: String)(using config: Config) =
    exec(clusterCmd(s"stop ${service} | grep Running"), config.hostMain)

  def update(stack: String, service: String)(using config: Config) =
    exec(s"docker service update --force ${stack}_${service}", config.hostMain)

  def prune()(using config: Config) =
    exec("docker system prune -a", config.hosts*)

  def deploy(service: String)(using config: Config) =
    exec(clusterCmd(s"deploy $service"), config.hostMain)

  def getlogs(service: String)(using config: Config) =
    val analyzer: Seq[String] => Unit = {
      lines =>
        lines.lastOption match
          case None => println("can't get log lines")
          case Some(lastLine) =>
            if lastLine.contains("DONE! save log at")
            then
              lastLine.split("logs/").lastOption match
                case Some(logname) =>
                  //println(s"logname = ${logname}")
                  val path = s"${config.logsPath}/${logname.trim}"
                  //println(s"save at = ${path}")
                  val scp = Seq(
                    "scp",
                    "-i",
                    config.keyIdentity,
                    s"${config.username}@${config.hostMain}.${config.domain}:~/cluster/logs/${logname.trim}",
                    path)
                  val result = scp ! ProcessLogger(s => println(s"SCP: ${s}"))
                  if result == 0
                  then println(s"log saved at ${path}")
                  else println("can't save log")
                case _ => println("can't get log name")

    }
    exec(clusterCmd(s"getlogs ${service}"), Some(analyzer), config.hostMain)

  def exec(cmd: String, hosts: String*)(using Config): Unit =
    exec(cmd, None, hosts*)

  def exec(cmd: String, analyzer: Option[Seq[String] => Unit], hosts: String*)(using config: Config): Unit =
    val jssh = ssh.create()

    for host <- hosts do

      println(s"... connect $host")

      val session = jssh.getSession(
        config.username, s"$host.${config.domain}", config.port)
      session.connect()

      val channel = session.openChannel("exec")
      channel.asInstanceOf[ChannelExec].setCommand(cmd)
      channel.asInstanceOf[ChannelExec].setErrStream(System.err)
      channel.setOutputStream(System.out)
      channel.setInputStream(null)

      val in = channel.getInputStream

      channel.connect()

      val source = Source.fromInputStream(in)

      try
        val lines = source.getLines().toSeq
        lines.foreach(println)
        analyzer.foreach(f => f(lines))
        if channel.getExitStatus > 0
        then println(s"... exit-status: ${channel.getExitStatus}")
      finally
        source.close()
        channel.disconnect()
        session.disconnect()

      println(s"... disconnect $host")


*/