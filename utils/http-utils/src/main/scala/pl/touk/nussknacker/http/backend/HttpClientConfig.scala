package pl.touk.nussknacker.http.backend

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.netty.handler.codec.http.HttpHeaderNames
import net.ceedubs.ficus.readers.ValueReader
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.filter.{FilterContext, FilterException, RequestFilter, ResponseFilter}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.http.backend.DefaultHttpClientConfig.{hostToInetAddress, ClientConfigBuilderExtension}
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
    forbiddenHosts: Option[List[String]],
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
      .setForbiddenLocalhostRequestFilter(!effectiveConfig.isLocalhostAllowed)
      .setForbiddenLocalhostResponseFilter(!effectiveConfig.isLocalhostAllowed && effectiveConfig.followRedirect)
      .setForbiddenHostRequestFilter(effectiveConfig.resolvedForbiddenHosts)
      .setForbiddenHostResponseFilter(effectiveConfig.followRedirect, effectiveConfig.resolvedForbiddenHosts)
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
      forbiddenHosts = extractConfig(_.forbiddenHosts.map(NonEmptyList.fromList), None),
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
      forbiddenHosts: Option[NonEmptyList[String]],
  ) extends LazyLogging {

    lazy val resolvedForbiddenHosts: Option[NonEmptyList[InetAddress]] = forbiddenHosts.flatMap(hosts => {
      val resolvedHosts = hosts.toList.flatMap(host =>
        hostToInetAddress(host).orElse {
          logger.warn(s"Cannot resolve host: $host - verifying if it's forbidden is skipped")
          None
        }
      )
      NonEmptyList.fromList(resolvedHosts)
    })

  }

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
      forbiddenHosts = forOption[List[String]]("forbiddenHosts"),
    )
  })

}

object DefaultHttpClientConfig {

  def apply(): HttpClientConfig = HttpClientConfig(None, None, None, None, None, None, None, None, None)

  val maxPoolSize: Int = 20

  val timeout: FiniteDuration = 10 seconds

  private[backend] def hostToInetAddress(host: String): Option[InetAddress] =
    Try(InetAddress.getByName(host)).toOption

  implicit class ClientConfigBuilderExtension(val builder: DefaultAsyncHttpClientConfig.Builder) extends AnyVal {

    def setForbiddenLocalhostRequestFilter(isFilterEnabled: Boolean): DefaultAsyncHttpClientConfig.Builder = {
      if (isFilterEnabled) {
        builder.addRequestFilter(new RequestFilter {
          override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
            val hostName = ctx.getRequest.getUri.getHost
            if (isLocalhost(hostName)) {
              throw new FilterException(s"Request to $hostName is forbidden")
            }
            ctx
          }
        })
      } else {
        builder
      }
    }

    def setForbiddenLocalhostResponseFilter(isFilterEnabled: Boolean): DefaultAsyncHttpClientConfig.Builder = {
      if (isFilterEnabled) {
        builder.addResponseFilter(new ResponseFilter {
          override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
            val maybeHost = getHostFromLocationHeader(ctx)
            maybeHost match {
              case Some(host) if isLocalhost(host) => throw new FilterException(s"Redirect to $host is forbidden")
              case _                               =>
            }
            ctx
          }
        })
      } else {
        builder
      }
    }

    def setForbiddenHostRequestFilter(
        forbiddenInetAddresses: Option[NonEmptyList[InetAddress]]
    ): DefaultAsyncHttpClientConfig.Builder =
      forbiddenInetAddresses match {
        case Some(addresses) => addForbiddenHostsRequestFilter(addresses.toList.toSet)
        case None            => builder
      }

    def setForbiddenHostResponseFilter(
        followRedirects: Boolean,
        forbiddenInetAddresses: Option[NonEmptyList[InetAddress]]
    ): DefaultAsyncHttpClientConfig.Builder =
      forbiddenInetAddresses match {
        case Some(addresses) if followRedirects => addForbiddenHostsResponseFilter(addresses.toList.toSet)
        case _                                  => builder
      }

    private def isLocalhost(host: String): Boolean = Try {
      val inetAddress = InetAddress.getByName(host)
      if (inetAddress.isLoopbackAddress || inetAddress.isAnyLocalAddress) {
        true
      } else {
        NetworkInterface.getByInetAddress(inetAddress) != null
      }
    }.recover { case _: UnknownHostException | _: SocketException | _: MalformedURLException =>
      false
    }.get

    private def getHostFromLocationHeader(ctx: FilterContext[_]): Option[String] =
      Option(ctx.getResponseHeaders)
        .flatMap(h => Option(h.get(HttpHeaderNames.LOCATION)))
        .flatMap(location => Try(new URL(location).getHost).toOption)

    private def addForbiddenHostsRequestFilter(
        forbiddenInetAddresses: Set[InetAddress]
    ): DefaultAsyncHttpClientConfig.Builder = {
      builder.addRequestFilter(new RequestFilter {
        override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
          val maybeInetAddress = hostToInetAddress(ctx.getRequest.getUri.getHost)
          checkAddressAllowed(forbiddenInetAddresses, maybeInetAddress)
          ctx
        }
      })
    }

    private def addForbiddenHostsResponseFilter(
        forbiddenInetAddresses: Set[InetAddress]
    ): DefaultAsyncHttpClientConfig.Builder = {
      builder.addResponseFilter(new ResponseFilter {
        override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
          val maybeInetAddress = getHostFromLocationHeader(ctx).flatMap(hostToInetAddress)
          checkAddressAllowed(forbiddenInetAddresses, maybeInetAddress)
          ctx
        }
      })
    }

  }

  private def checkAddressAllowed(
      forbiddenInetAddresses: Set[InetAddress],
      maybeInetAddress: Option[InetAddress]
  ): Unit =
    maybeInetAddress match {
      case Some(host) if forbiddenInetAddresses.contains(host) =>
        throw new FilterException(s"Redirect to $host is forbidden")
      case _ =>
    }

}
