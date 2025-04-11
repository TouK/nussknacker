package pl.touk.nussknacker.http.backend

import cats.data.NonEmptyList
import io.netty.handler.codec.http.HttpHeaderNames
import net.ceedubs.ficus.readers.ValueReader
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.filter.{FilterContext, FilterException, RequestFilter, ResponseFilter}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.http.backend.DefaultHttpClientConfig.ClientConfigBuilderExtension
import pl.touk.nussknacker.http.backend.HttpClientConfig.EffectiveHttpClientConfig
import sttp.client3.SttpBackendOptions

import java.net.{InetAddress, MalformedURLException, NetworkInterface, SocketException, UnknownHostException, URL}
import scala.concurrent.duration._
import scala.util.Try

case class HttpClientConfig(
    timeout: Option[FiniteDuration],
    connectTimeout: Option[FiniteDuration],
    maxPoolSize: Option[Int],
    useNative: Option[Boolean],
    followRedirect: Option[Boolean],
    forceShutdown: Option[Boolean],
    // this can be used to tune single scenario
    configForProcess: Option[Map[String, HttpClientConfig]],
    isLocalhostAllowed: Option[Boolean],
    forbiddenHostRegexes: Option[List[String]],
) {

  def toAsyncHttpClientConfig(processName: Option[ProcessName]): DefaultAsyncHttpClientConfig.Builder = {
    val effectiveConfig = toEffectiveHttpClientConfig(processName)
    new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(effectiveConfig.connectTimeout.toMillis.toInt)
      .setRequestTimeout(effectiveConfig.timeout.toMillis.toInt)
      .setIoThreadsCount(effectiveConfig.maxPoolSize)
      .setUseNativeTransport(effectiveConfig.useNative)
      .setFollowRedirect(effectiveConfig.followRedirect)
      .setThreadPoolName(processName.map(_.value + s"-http-pool").getOrElse(s"http-pool"))
      .setForbiddenLocalhostRequestFilter(effectiveConfig.isLocalhostAllowed)
      .setForbiddenLocalhostResponseFilter(effectiveConfig.isLocalhostAllowed && effectiveConfig.followRedirect)
      .setForbiddenHostRequestFilter(effectiveConfig.forbiddenHostRegexes)
      .setForbiddenHostResponseFilter(effectiveConfig.followRedirect, effectiveConfig.forbiddenHostRegexes)
  }

  def toSttpBackendOptions(processName: Option[ProcessName]): SttpBackendOptions = {
    val effectiveConfig = toEffectiveHttpClientConfig(processName)
    SttpBackendOptions.Default.copy(connectionTimeout = effectiveConfig.connectTimeout)
  }

  private def toEffectiveHttpClientConfig(processName: Option[ProcessName]): EffectiveHttpClientConfig = {
    def extractConfig[T](extract: HttpClientConfig => Option[T], default: T): T = {
      val specificConfig = processName.flatMap(name => configForProcess.flatMap(_.get(name.value)))
      specificConfig.flatMap(extract).orElse(extract(this)).getOrElse(default)
    }
    EffectiveHttpClientConfig(
      timeout = extractConfig(_.timeout, DefaultHttpClientConfig.timeout),
      connectTimeout = extractConfig(_.connectTimeout, DefaultHttpClientConfig.timeout),
      maxPoolSize = extractConfig(_.maxPoolSize, DefaultHttpClientConfig.maxPoolSize),
      // FIXME: does not work by default?
      useNative = extractConfig(_.useNative, false),
      followRedirect = extractConfig(_.followRedirect, false),
      forceShutdown = extractConfig(_.forceShutdown, false),
      isLocalhostAllowed = extractConfig(_.isLocalhostAllowed, true),
      forbiddenHostRegexes = extractConfig(_.forbiddenHostRegexes.map(NonEmptyList.fromList), None),
    )
  }

}

object HttpClientConfig {

  private case class EffectiveHttpClientConfig(
      timeout: FiniteDuration,
      connectTimeout: FiniteDuration,
      maxPoolSize: Int,
      useNative: Boolean,
      followRedirect: Boolean,
      forceShutdown: Boolean,
      isLocalhostAllowed: Boolean,
      forbiddenHostRegexes: Option[NonEmptyList[String]],
  )

  // ArbitraryTypeReader cannot handle nested option here... :/
  implicit val vr: ValueReader[HttpClientConfig] = ValueReader.relative(conf => {
    import net.ceedubs.ficus.Ficus._
    def forOption[T](path: String)(implicit r: ValueReader[T]) = optionValueReader[T].read(conf, path)
    HttpClientConfig(
      timeout = forOption[FiniteDuration]("timeout"),
      connectTimeout = forOption[FiniteDuration]("connectTimeout"),
      maxPoolSize = forOption[Int]("maxPoolSize"),
      useNative = forOption[Boolean]("useNative"),
      followRedirect = forOption[Boolean]("followRedirect"),
      forceShutdown = forOption[Boolean]("forceShutdown"),
      configForProcess = forOption("configForProcess")(mapValueReader(vr)),
      isLocalhostAllowed = forOption[Boolean]("isLocalhostAllowed"),
      forbiddenHostRegexes = forOption[List[String]]("forbiddenHostRegexes"),
    )
  })

}

object DefaultHttpClientConfig {

  def apply(): HttpClientConfig = HttpClientConfig(None, None, None, None, None, None, None, None, None)

  val maxPoolSize: Int = 20

  val timeout: FiniteDuration = 10 seconds

  implicit class ClientConfigBuilderExtension(val builder: DefaultAsyncHttpClientConfig.Builder) extends AnyVal {

    def setForbiddenLocalhostRequestFilter(isLocalhostAllowed: Boolean): DefaultAsyncHttpClientConfig.Builder = {
      if (isLocalhostAllowed) {
        builder
      } else {
        builder.addRequestFilter(new RequestFilter {
          override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
            val hostName = ctx.getRequest.getUri.getHost
            if (isLocalhost(hostName)) {
              throw new FilterException(s"Request to $hostName is forbidden")
            }
            ctx
          }
        })
      }
    }

    def setForbiddenLocalhostResponseFilter(isLocalhostAllowed: Boolean): DefaultAsyncHttpClientConfig.Builder = {
      if (isLocalhostAllowed) {
        builder
      } else {
        builder.addResponseFilter(new ResponseFilter {
          override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
            val maybeHost = getLocalhostFromLocationHeader(ctx)
            maybeHost match {
              case Some(host) if isLocalhost(host) => throw new FilterException(s"Redirect to $host is forbidden")
              case _                               =>
            }
            ctx
          }
        })
      }
    }

    def setForbiddenHostRequestFilter(
        forbiddenHostRegex: Option[NonEmptyList[String]]
    ): DefaultAsyncHttpClientConfig.Builder =
      forbiddenHostRegex match {
        case Some(regexes) => addForbiddenHostsRequestFilter(regexes)
        case None          => builder
      }

    def setForbiddenHostResponseFilter(
        followRedirects: Boolean,
        forbiddenHostRegex: Option[NonEmptyList[String]]
    ): DefaultAsyncHttpClientConfig.Builder =
      forbiddenHostRegex match {
        case Some(regexes) if followRedirects => addForbiddenHostsResponseFilter(regexes)
        case _                                => builder
      }

    private def isLocalhost(host: String): Boolean = Try {
      val inetAddress = InetAddress.getByName(host)
      if (!inetAddress.isLoopbackAddress && !inetAddress.isAnyLocalAddress) {
        NetworkInterface.getByInetAddress(inetAddress) != null
      } else {
        true
      }
    }.recover { case _: UnknownHostException | _: SocketException | _: MalformedURLException =>
      false
    }.get

    private def getLocalhostFromLocationHeader(ctx: FilterContext[_]): Option[String] =
      Option(ctx.getResponseHeaders)
        .flatMap(h => Option(h.get(HttpHeaderNames.LOCATION)))
        .flatMap(location => Try(new URL(location).getHost).toOption)

    private def addForbiddenHostsRequestFilter(
        forbiddenHostRegexes: NonEmptyList[String]
    ): DefaultAsyncHttpClientConfig.Builder = {
      val regexes = forbiddenHostRegexes.toList.map(_.r)
      builder.addRequestFilter(new RequestFilter {
        override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
          val hostName = ctx.getRequest.getUri.getHost.toLowerCase
          if (regexes.exists(r => r.matches(hostName))) {
            throw new FilterException(s"Request to $hostName is forbidden")
          }
          ctx
        }
      })
    }

    private def addForbiddenHostsResponseFilter(
        forbiddenHostRegexes: NonEmptyList[String]
    ): DefaultAsyncHttpClientConfig.Builder = {
      val regexes = forbiddenHostRegexes.toList.map(_.r)
      builder.addResponseFilter(new ResponseFilter {
        override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
          val maybeHost = getLocalhostFromLocationHeader(ctx)
          maybeHost match {
            case Some(host) if regexes.exists(r => r.matches(host)) =>
              throw new FilterException(s"Redirect to $host is forbidden")
            case _ =>
          }
          ctx
        }
      })
    }

  }

}
