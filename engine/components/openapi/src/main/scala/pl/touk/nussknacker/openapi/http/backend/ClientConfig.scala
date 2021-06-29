package pl.touk.nussknacker.openapi.http.backend

import net.ceedubs.ficus.readers.ValueReader
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.openapi.http.backend.DispatchConfig.EffectiveDispatchConfig
import sttp.client.SttpBackendOptions

import scala.concurrent.duration._

case class DispatchConfig(timeout: Option[FiniteDuration],
                          connectTimeout: Option[FiniteDuration],
                          maxPoolSize: Option[Int],
                          useNative: Option[Boolean],
                          followRedirect: Option[Boolean],
                          forceShutdown: Option[Boolean],
                          //jakbysmy chcieli tunowac pojedyncze procesy
                          configForProcess: Option[Map[String, DispatchConfig]]) {

  def toAsyncHttpClientConfig(processId: Option[String]): DefaultAsyncHttpClientConfig.Builder = {
    val effectiveConfig = toEffectiveDispatchConfig(processId)
    new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(effectiveConfig.connectTimeout.toMillis.toInt)
      .setRequestTimeout(effectiveConfig.timeout.toMillis.toInt)
      .setIoThreadsCount(effectiveConfig.maxPoolSize)
      .setUseNativeTransport(effectiveConfig.useNative)
      .setFollowRedirect(effectiveConfig.followRedirect)
      .setThreadPoolName(processId.map(_ + s"-http-pool").getOrElse(s"http-pool"))
  }

  def toSttpBackendOptions(processId: Option[String]): SttpBackendOptions = {
    val effectiveConfig = toEffectiveDispatchConfig(processId)
    SttpBackendOptions.Default.copy(connectionTimeout = effectiveConfig.connectTimeout)
  }

  private def toEffectiveDispatchConfig(processId: Option[String]): EffectiveDispatchConfig = {
    def extractConfig[T](extract: DispatchConfig => Option[T], default: T): T = {
      val specificConfig = processId.flatMap(id => configForProcess.flatMap(_.get(id)))
      specificConfig.flatMap(extract).orElse(extract(this)).getOrElse(default)
    }
    EffectiveDispatchConfig(
      timeout = extractConfig(_.timeout, DefaultDispatchConfig.timeout),
      connectTimeout = extractConfig(_.connectTimeout, DefaultDispatchConfig.timeout),
      maxPoolSize = extractConfig(_.maxPoolSize, DefaultDispatchConfig.maxPoolSize),
      useNative = extractConfig(_.useNative, true),
      followRedirect = extractConfig(_.followRedirect, false),
      forceShutdown = extractConfig(_.forceShutdown, false)
    )
  }
}

object DispatchConfig {

  private case class EffectiveDispatchConfig(timeout: FiniteDuration,
                                             connectTimeout: FiniteDuration,
                                             maxPoolSize: Int,
                                             useNative: Boolean,
                                             followRedirect: Boolean,
                                             forceShutdown: Boolean)

  //ArbitraryTypeReader nietety tu wymieka :/
  implicit val vr: ValueReader[DispatchConfig] = ValueReader.relative(conf => {
    import net.ceedubs.ficus.Ficus._
    def forOption[T](path: String)(implicit r: ValueReader[T]) = optionValueReader[T].read(conf, path)
    DispatchConfig(
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

object DefaultDispatchConfig {

  def apply() : DispatchConfig = DispatchConfig(None, None, None, None, None, None, None)

  val maxPoolSize: Int = 20

  val timeout: FiniteDuration = 10 seconds

}
