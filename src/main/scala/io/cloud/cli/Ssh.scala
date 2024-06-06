package io.cloud.cli

import cats.effect.{ExitCode, IO, Resource}
import com.jcraft.jsch.{Channel, ChannelExec, JSch, Session}

import java.io.InputStream
import scala.io.Source
import AppConfigs.Config
import io.cloud.cli.AppConfigs.Config

object Ssh:

  def configure(): IO[Unit] =
    IO.blocking(JSch.setConfig("StrictHostKeyChecking", "no"))

  def connectAndExec(host: String, cmd: String)(f: Seq[String] => IO[Unit]): Config ?=> IO[ExitCode] =
    mkSession(host).use:
      session =>
        mkChannel(session, cmd).use:
          channel =>
            mkChannelIn(channel).use:
              in =>
                mkChannelSource(in).use:
                  source =>
                    channel.connect()
                    f(source.getLines().toSeq) *>
                      IO.unit.as(ExitCode(channel.getExitStatus))
        

  def mkSession(host: String): (cfg: Config) ?=> Resource[IO, Session] =
    Resource.make {
      IO.blocking {
        configure()
        val jsch = new JSch()
        jsch.addIdentity(cfg.keyIdentity)
        val session = jsch.getSession(
          cfg.username, s"$host.${cfg.domain}", cfg.port)
        session.connect()
        session
      }
    } { s =>
      IO.blocking(s.disconnect())
    }

  def mkChannelIn(channel: Channel): Resource[IO, InputStream] =
    Resource.make {
      IO.blocking {
        channel.getInputStream
      }
    } { s =>
      IO.blocking(s.close())
    }
  
  def mkChannelSource(in: InputStream): Resource[IO, Source] =
    Resource.make {
      IO.blocking {
        Source.fromInputStream(in)
      } 
    } { s =>
      IO.blocking(s.close())
    }
    
  def mkChannel(session: Session, cmd: String): Resource[IO, Channel] =
    Resource.make {
      IO.blocking {
        val channel = session.openChannel("exec").asInstanceOf[ChannelExec]
        channel.setCommand(cmd)
        channel.setErrStream(System.err)
        channel.setOutputStream(System.out)
        channel.setInputStream(null)
        channel
      }
    } { c =>
      IO.blocking(c.disconnect())
    }
