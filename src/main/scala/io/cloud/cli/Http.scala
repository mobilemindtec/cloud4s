package io.cloud.cli

import cats.effect.{IO, Resource}

import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.Base64

trait Auth

case class AuthBasic(username: String, password: String) extends Auth:

  val credential = s"${username}:${password}"

  def encode() : String = new String(encodeBytes())

  def encodeBytes(): Array[Byte] =
    Base64
      .getEncoder
      .encode(credential.getBytes)

case class Http(url: String,
                auth: Option[Auth] = None,
                headers: Map[String, String] = Map(),
                timeout: Long = 5000):

  private def mkClient: Resource[IO, HttpClient] =
    Resource.make {
      IO.blocking(HttpClient.newHttpClient())
    } { c =>
      IO.blocking(c.close())
    }

  private def mkRequest: HttpRequest =
    val builder =
      HttpRequest
        .newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofMillis(timeout))
    auth match
      case Some(basic: AuthBasic) =>
        builder.header("Authorization", s"Basic ${basic.encode()}")

    builder.build()

  def getAsString: IO[HttpResponse[String]] =
    mkClient.use:
      client =>
        IO.blocking(client.send(mkRequest, BodyHandlers.ofString()))


