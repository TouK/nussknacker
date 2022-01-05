package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.headers.{`X-Forwarded-Host`, `X-Forwarded-Proto`}
import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import io.circe.Encoder
import pl.touk.nussknacker.restmodel.NuLink

import java.net.URL
import scala.util.Try

case class LinkEncodingConfig(publicPath: String = "/", encodeAbsoluteUrl: Boolean = true)

object NuLinkEncoder {

  private val xForwardedPort = "x-forwarded-port"

  private val xForwardedPath = "x-forwarded-path"

  def nuLinkEncoder(config: LinkEncodingConfig): Directive1[Encoder[NuLink]] = {
    extractRequestContext.map { ctx =>
      NuLink.encoderForBasePath(prepareUrl(ctx.request, config))
    }
  }

  //FIXME: handle Forwarded header...
  private[api] def prepareUrl(request: HttpRequest, config: LinkEncodingConfig): String = {
    val baseUrl = if (config.encodeAbsoluteUrl) {
      decodeUrlFromRequest(request)
    } else {
      ""
    }
    baseUrl + config.publicPath
  }

  private def decodeUrlFromRequest(request: HttpRequest): String = {
    def firstHeader[T](fun: PartialFunction[HttpHeader, T]): Option[T] = request.headers.collectFirst(fun)

    val protocol: String = firstHeader {
      case `X-Forwarded-Proto`(forwardedProto) => forwardedProto
    }.getOrElse(request.uri.scheme)

    val authority = request.uri.authority

    val host = firstHeader {
      case `X-Forwarded-Host`(forwardedHost) => forwardedHost
    }.getOrElse(authority.host)

    val port = firstHeader {
      case header if header.name().toLowerCase == xForwardedPort => Try(header.value().toInt).toOption
    }.flatten.getOrElse(authority.port)

    val path = firstHeader {
      case header if header.name().toLowerCase == xForwardedPath => header.value()
    }.getOrElse("")

    val normalized = authority.copy(host = host, port = port).normalizedFor(protocol)
    val normalizedPort = if (normalized.port == 0) -1 else normalized.port
    new URL(protocol, normalized.host.address(), normalizedPort, path).toString
  }
}
