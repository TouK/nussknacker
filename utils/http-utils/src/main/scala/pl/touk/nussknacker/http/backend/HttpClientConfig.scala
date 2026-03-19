package pl.touk.nussknacker.http.backend

import cats.data.NonEmptyList
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.ipfilter.{IpFilterRuleType, IpSubnetFilterRule}
import net.ceedubs.ficus.readers.ValueReader
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.filter.{FilterContext, FilterException, RequestFilter, ResponseFilter}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.http.backend.DefaultHttpClientConfig.ClientConfigBuilderExtension
import pl.touk.nussknacker.http.backend.HttpClientConfig.EffectiveHttpClientConfig
import sttp.client3.SttpBackendOptions

import java.net.{InetAddress, InetSocketAddress, URI}
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
    forbiddenCidrs: Option[List[String]],
) {

  def toAsyncHttpClientConfig(processName: Option[ProcessName]): DefaultAsyncHttpClientConfig.Builder = {
    val effectiveConfig = toEffectiveHttpClientConfig(processName)
    new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(effectiveConfig.connectTimeout.toMillis.toInt)
      .setRequestTimeout(effectiveConfig.timeout.toMillis.toInt)
      .setIoThreadsCount(effectiveConfig.maxPoolSize)
      .setUseNativeTransport(effectiveConfig.useNative)
      .setFollowRedirect(effectiveConfig.followRedirect)
      .setThreadPoolName(processName.map(_.value + s"-nu-http-pool").getOrElse(s"nu-http-pool"))
      .setForbiddenCidrRequestFilter(effectiveConfig.resolvedCidrs)
      .setForbiddenHostResponseFilter(effectiveConfig.followRedirect, effectiveConfig.resolvedCidrs)
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
      forbiddenCidrs = extractConfig(_.forbiddenCidrs.map(NonEmptyList.fromList), None),
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
      forbiddenCidrs: Option[NonEmptyList[String]],
  ) {

    lazy val resolvedCidrs: Option[NonEmptyList[IpSubnetFilterRule]] = forbiddenCidrs
      .map(_.map(cidrString => new IpSubnetFilterRule(cidrString, IpFilterRuleType.REJECT)))

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
      forbiddenCidrs = forOption[List[String]]("forbiddenCidrs"),
    )
  })

}

object DefaultHttpClientConfig {
  private val undefinedPort = 0

  def apply(): HttpClientConfig = HttpClientConfig(None, None, None, None, None, None, None, None)

  val maxPoolSize: Int = 20

  val timeout: FiniteDuration = 10 seconds

  implicit class ClientConfigBuilderExtension(val builder: DefaultAsyncHttpClientConfig.Builder) extends AnyVal {

    def setForbiddenCidrRequestFilter(
        ipSubnetFilterRules: Option[NonEmptyList[IpSubnetFilterRule]]
    ): DefaultAsyncHttpClientConfig.Builder =
      ipSubnetFilterRules
        .map(addForbiddenHostsRequestFilter)
        .getOrElse(builder)

    def setForbiddenHostResponseFilter(
        followRedirects: Boolean,
        ipSubnetFilterRules: Option[NonEmptyList[IpSubnetFilterRule]]
    ): DefaultAsyncHttpClientConfig.Builder =
      ipSubnetFilterRules
        .filter(_ => followRedirects)
        .map(addForbiddenHostsResponseFilter)
        .getOrElse(builder)

    private def addForbiddenHostsRequestFilter(
        ipSubnetFilterRules: NonEmptyList[IpSubnetFilterRule]
    ): DefaultAsyncHttpClientConfig.Builder = {
      builder.addRequestFilter(new RequestFilter {
        override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
          val maybeURI = Try(ctx.getRequest.getUri.toJavaNetURI).toOption
          checkAddressAllowed(ipSubnetFilterRules, maybeURI)
          ctx
        }
      })
    }

    private def addForbiddenHostsResponseFilter(
        ipSubnetFilterRules: NonEmptyList[IpSubnetFilterRule]
    ): DefaultAsyncHttpClientConfig.Builder = {
      builder.addResponseFilter(new ResponseFilter {
        override def filter[T](ctx: FilterContext[T]): FilterContext[T] = {
          val maybeURI = getURIFromLocationHeader(ctx)
          checkAddressAllowed(ipSubnetFilterRules, maybeURI)
          ctx
        }
      })
    }

    private def checkAddressAllowed(
        ipSubnetFilterRules: NonEmptyList[IpSubnetFilterRule],
        maybeURI: Option[URI],
    ): Unit =
      maybeURI
        .map(toInetSocketAddress)
        .filter(addresses => ipSubnetFilterRules.exists(filter => addresses.exists(address => filter.matches(address))))
        .map(addresses =>
          throw new FilterException(s"Request to: ${addresses.map(_.getAddress).mkString("[", ", ", "]")} is forbidden")
        )

    private def toInetSocketAddress(uri: URI): Array[InetSocketAddress] =
      InetAddress.getAllByName(uri.getHost).map { address => new InetSocketAddress(address, undefinedPort) }

    private def getURIFromLocationHeader(ctx: FilterContext[_]): Option[URI] =
      Option(ctx.getResponseHeaders)
        .flatMap(h => Option(h.get(HttpHeaderNames.LOCATION)))
        .flatMap(location => Try(new URI(location)).toOption)

  }

}
