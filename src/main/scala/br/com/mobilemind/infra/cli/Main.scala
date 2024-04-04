package br.com.mobilemind.infra.cli

import br.com.mobilemind.infra.cli.commands.*
import com.jcraft.jsch.{ChannelExec, JSch}

import java.io.{File, FileNotFoundException}
import scala.io.Source
import scala.language.postfixOps
import scala.sys.process.*

case class Config(hosts: Seq[String] = Nil,
                  hostMain: String = "",
                  domain: String = "",
                  username: String = "",
                  port: Int = 0,
                  keyIdentity: String = "",
                  logsPath: String = "")

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
      case "getlogs" :: service :: Nil => getlogs(service)
      case _ => println("""
        use options:
        > deploy <service name>
        > update <stack name> <service name>
        > prune
        > ps
        > ps <service name>
        > ls
        > getlogs <service name>
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

object commands:

  private def clusterCmd(cmd: String) =
    s"cd cluster && ./docker $cmd"

  def ps()(using config: Config) =
    exec("docker ps", config.hosts*)

  def df()(using config: Config) =
    exec("df", config.hosts*)

  def ls()(using config: Config) =
    exec(clusterCmd("ls"), config.hostMain)

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

  def ps(service: String)(using config: Config) =
    exec(clusterCmd(s"ps ${service} | grep Running"), config.hostMain)

  def update(stack: String, service: String)(using config: Config) =
    exec(s"docker service update --force ${stack}_${service}", config.hostMain)

  def prune()(using config: Config) =
    exec("docker system prune -a", config.hosts*)

  def deploy(service: String)(using config: Config) =
    exec(clusterCmd(s"deploy $service"), config.hostMain)

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