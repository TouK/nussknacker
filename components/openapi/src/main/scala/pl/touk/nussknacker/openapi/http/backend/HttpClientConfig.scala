package pl.touk.nussknacker.openapi.http.backend

import net.ceedubs.ficus.readers.ValueReader
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.openapi.http.backend.HttpClientConfig.EffectiveHttpClientConfig
import sttp.client.SttpBackendOptions

import scala.concurrent.duration._

case class HttpClientConfig(timeout: Option[FiniteDuration],
                            connectTimeout: Option[FiniteDuration],
                            maxPoolSize: Option[Int],
                            useNative: Option[Boolean],
                            followRedirect: Option[Boolean],
                            forceShutdown: Option[Boolean],
                            //this can be used to tune single scenario
                            configForProcess: Option[Map[String, HttpClientConfig]]) {

  def toAsyncHttpClientConfig(processId: Option[String]): DefaultAsyncHttpClientConfig.Builder = {
    val effectiveConfig = toEffectiveHttpClientConfig(processId)
    new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(effectiveConfig.connectTimeout.toMillis.toInt)
      .setRequestTimeout(effectiveConfig.timeout.toMillis.toInt)
      .setIoThreadsCount(effectiveConfig.maxPoolSize)
      .setUseNativeTransport(effectiveConfig.useNative)
      .setFollowRedirect(effectiveConfig.followRedirect)
      .setThreadPoolName(processId.map(_ + s"-http-pool").getOrElse(s"http-pool"))
  }

  def toSttpBackendOptions(processId: Option[String]): SttpBackendOptions = {
    val effectiveConfig = toEffectiveHttpClientConfig(processId)
    SttpBackendOptions.Default.copy(connectionTimeout = effectiveConfig.connectTimeout)
  }

  private def toEffectiveHttpClientConfig(processId: Option[String]): EffectiveHttpClientConfig = {
    def extractConfig[T](extract: HttpClientConfig => Option[T], default: T): T = {
      val specificConfig = processId.flatMap(id => configForProcess.flatMap(_.get(id)))
      specificConfig.flatMap(extract).orElse(extract(this)).getOrElse(default)
    }
    EffectiveHttpClientConfig(
      timeout = extractConfig(_.timeout, DefaultHttpClientConfig.timeout),
      connectTimeout = extractConfig(_.connectTimeout, DefaultHttpClientConfig.timeout),
      maxPoolSize = extractConfig(_.maxPoolSize, DefaultHttpClientConfig.maxPoolSize),
      //FIXME: does not work by default?
      useNative = extractConfig(_.useNative, false),
      followRedirect = extractConfig(_.followRedirect, false),
      forceShutdown = extractConfig(_.forceShutdown, false)
    )
  }
}

object HttpClientConfig {

  private case class EffectiveHttpClientConfig(timeout: FiniteDuration,
                                               connectTimeout: FiniteDuration,
                                               maxPoolSize: Int,
                                               useNative: Boolean,
                                               followRedirect: Boolean,
                                               forceShutdown: Boolean)

  //ArbitraryTypeReader cannot handle nested option here... :/
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

  def apply() : HttpClientConfig = HttpClientConfig(None, None, None, None, None, None, None)

  val maxPoolSize: Int = 20

  val timeout: FiniteDuration = 10 seconds

}
