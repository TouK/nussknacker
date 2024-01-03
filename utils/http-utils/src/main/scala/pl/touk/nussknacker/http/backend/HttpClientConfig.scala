package pl.touk.nussknacker.http.backend

import net.ceedubs.ficus.readers.ValueReader
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.http.backend.HttpClientConfig.EffectiveHttpClientConfig
import sttp.client3.SttpBackendOptions

import scala.concurrent.duration._

case class HttpClientConfig(
    timeout: Option[FiniteDuration],
    connectTimeout: Option[FiniteDuration],
    maxPoolSize: Option[Int],
    useNative: Option[Boolean],
    followRedirect: Option[Boolean],
    forceShutdown: Option[Boolean],
    // this can be used to tune single scenario
    configForProcess: Option[Map[String, HttpClientConfig]]
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
      forceShutdown = extractConfig(_.forceShutdown, false)
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
      forceShutdown: Boolean
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
      configForProcess = forOption("configForProcess")(mapValueReader(vr))
    )
  })

}

object DefaultHttpClientConfig {

  def apply(): HttpClientConfig = HttpClientConfig(None, None, None, None, None, None, None)

  val maxPoolSize: Int = 20

  val timeout: FiniteDuration = 10 seconds

}
