package br.com.mobilemind.infra.cli

import br.com.mobilemind.infra.cli.codebuild.{awsInfoBuild, awsListProjects, awsShowBuild, awsStartBuild, incrementVersion}
import br.com.mobilemind.infra.cli.commands.*
import com.jcraft.jsch.{ChannelExec, JSch}

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
import java.util.Base64

case class ShowProcesses(all: Boolean)

case class BuildImage(dockerFile: Option[String], path: String)

val showProcessesOpts: Opts[ShowProcesses] =
  Opts.subcommand("ps", "Lists docker processes running!") {
    Opts.flag("all", "Whether to show all running processes.", short = "a")
      .orFalse
      .map(ShowProcesses.apply)
  }

val dockerFileOpts: Opts[Option[String]] =
  Opts.option[String]("file", "The name of the Dockerfile.", short = "f").orNone
// dockerFileOpts: Opts[Option[String]] = Opts([--file <string>])

val pathOpts: Opts[String] =
  Opts.argument[String](metavar = "path")
// pathOpts: Opts[String] = Opts(<path>)

val buildOpts: Opts[BuildImage] =
  Opts.subcommand("build", "Builds a docker image!") {
    (dockerFileOpts, pathOpts).mapN(BuildImage)
  }

// buildOpts: Opts[BuildImage] = Opts(build)

/*
object DockerApp extends CommandIOApp(
  name = "docker",
  header = "Faux docker command line",
  version = "0.0.x"
) {

  override def main: Opts[IO[ExitCode]] =
    (showProcessesOpts orElse buildOpts).map {
      case ShowProcesses(all) => ???
      case BuildImage(dockerFile, path) => ???
    }
}*/
// https://ben.kirw.in/decline/usage.html
case class Config(hosts: Seq[String] = Nil,
                  hostMain: String = "",
                  domain: String = "",
                  username: String = "",
                  port: Int = 0,
                  keyIdentity: String = "",
                  logsPath: String = "",
                  codebuildUrl: String = "",
                  codebuildUsername: String = "",
                  codebuildPassword: String = "")

@main def main(args: String*): Unit =
  try
    given cfg: Config = ssh.readConfigs()
    ssh.configure()
    args.toList match
      case "deploy" :: service :: Nil =>
        deploy(service)
      case "update" :: stack :: service :: Nil =>
        update(stack, service)
      case "prune" :: Nil => prune()
      case "ps" :: Nil => ps()
      case "ps" :: service :: Nil => ps(service)
      case "ls" :: Nil => ls()
      case "df" :: Nil => df()
      case "get-logs" :: service :: Nil => getlogs(service)
      case "stop" :: service :: Nil => stop(service)
      case "rm" :: stack :: Nil => rm(stack)
      case "stats" :: Nil => stats()
      case "service" :: cmd  if cmd.nonEmpty => service(cmd)
      case "docker" :: cmd  if cmd.nonEmpty => docker(cmd)
      case "increment-version" :: service :: Nil  => incrementVersion(service)
      case "aws" :: "start-build" :: projectName :: Nil =>
        awsStartBuild(projectName)
      case "codebuild" :: "start" :: projectName :: Nil =>
        awsStartBuild(projectName)
      case "codebuild" :: "status" :: projectName :: Nil =>
        awsShowBuild(projectName)
      case "codebuild" :: "info" :: projectName :: Nil =>
        awsInfoBuild(projectName)
      case "codebuild" :: "list" :: Nil =>
        awsListProjects()
      case _ => println("""
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
        > get-logs <service name>
        > increment-version <service name>
        > codebuild start <project name>
        > codebuild status <project name>
        > codebuild info <project name>
        > codebuild list
      """.stripIndent())
  catch
    case e =>
      println(s"error: ${e.getMessage}")



object ssh:

  def readConfigs(): Config =

    val home = System.getenv("HOME")
    val configFile = new File(home, ".infra-cli.cfg")

    if !configFile.exists()
    then throw new FileNotFoundException(configFile.getAbsolutePath)

    val buffer = Source.fromFile(configFile)
    var config = Config()
    try
      for line <- buffer.getLines() do
        line.split("=").toList match
          case "hosts" :: hosts :: Nil =>
            config = config.copy(hosts = hosts.split(","))
          case "host.main" :: main :: Nil =>
            config = config.copy(hostMain = main)
          case "host.domain" :: domain :: Nil =>
            config = config.copy(domain = domain)
          case "ssh.username" :: username :: Nil =>
            config = config.copy(username = username)
          case "ssh.port" :: port :: Nil =>
            config = config.copy(port = port.toInt)
          case "ssh.key.identity" :: keyIdentity :: Nil =>
            config = config.copy(keyIdentity = keyIdentity.replace("$HOME", home))
          case "logs.path" :: logsPath :: Nil =>
            config = config.copy(logsPath = logsPath.replace("$HOME", home))
          case "codebuild.url" :: url :: Nil =>
            config = config.copy(codebuildUrl = url)
          case "codebuild.username" :: username :: Nil =>
            config = config.copy(codebuildUsername = username)
          case "codebuild.password" :: password :: Nil =>
            config = config.copy(codebuildPassword = password)
          case _ => println(s"wrong config: ${line}")
    finally
        buffer.close()
    config

  def configure(): Unit =
    JSch.setConfig("StrictHostKeyChecking", "no")

  def create()(using config: Config): JSch =
    val jsch= new JSch()
    val home = System.getenv("HOME")
    jsch.addIdentity(config.keyIdentity)
    jsch

object codebuild:
  def incrementVersion(service: String)(using config: Config) =
    val credential = s"${config.codebuildUsername}:${config.codebuildPassword}"
    val auth = Base64
      .getEncoder
      .encode(credential.getBytes)
    val client = HttpClient.newHttpClient()
    val url = s"${config.codebuildUrl}/app/version/increment/${service}"
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(url))
      .header("Authorization", s"Basic ${new String(auth)}")
      .build()
    val resp =
      client
        .send(request, BodyHandlers.ofString())

    println(s"response status: ${resp.statusCode()}, body: ${resp.body()}")

  def awsStartBuild(projectName: String) =
    val cmd = s"aws codebuild start-build --project-name ${projectName}"
    val result = (cmd !!)
    println(result)

  def awsGetBuildId(projectName: String): String =
    val cmd = s"aws codebuild list-builds-for-project --project-name ${projectName}"
    val filterCmd = "jq -r '.ids[0]'"
    val buildId = ((cmd #| filterCmd) !!)
    buildId

  def awsShowBuild(projectName: String) =
    val buildId = awsGetBuildId(projectName)
    if buildId.isEmpty
    then println("build not found")
    else
      //println(s"build id = ${buildId}")
      val cmd = s"aws codebuild batch-get-builds --ids $buildId"
      val filterCmd = "jq '.builds[].phases[] | select (.phaseType==\"BUILD\") | .phaseStatus'"
      val result = ((cmd #| filterCmd) !!)
      println(s"STATUS: ${result}")

  def awsInfoBuild(projectName: String) =
    val buildId = awsGetBuildId(projectName)
    if buildId.isEmpty
    then println("build not found")
    else
      val cmd = s"aws codebuild batch-get-builds --ids $buildId"
      val result = (cmd !!)
      println(result)

  def awsListProjects() =
    val cmd = s"aws codebuild list-projects"
    println(cmd !!)

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