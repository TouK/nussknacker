package pl.touk.nussknacker.openapi.http.backend

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.openapi.http.backend.OpenapiSttpBackend.HttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.ExecutionContext

//TODO: no nie jestem z tego zadowolony - to jest troche hack skopiowany z poca.
// Ale nie mozemy sobie pozwolic zeby kazdy task tworzyl wlasny Http, bo to pozera zasoby blyskawicznie
//Alternatywa to trzymanie jednego polaczenia per task - ale nie jestem pewny
//jak to ladnie zrobic z tym klientem...
object CacheableHttpBackend extends CacheableClient[HttpBackend] with LazyLogging {

  //to pod spodem to troche hack. Chodzi o to, aby zainicjalizowac klasy (zwlaszcza anonimowe/lambdy) ktorych
  //chcemy uzyc do zamykania
  {
    val toInitialize = new CacheableClient[HttpBackend] {
      override def info(str: => String): Unit = {}
      override def warn(str: => String): Unit = {}
      override def shutdown(client: HttpBackend): Unit = CacheableHttpBackend.shutdown(client)
      override def create(httpClientConfig: HttpClientConfig, metaData: MetaData): HttpBackend = CacheableHttpBackend.create(httpClientConfig, metaData)
    }
    val client = toInitialize.retrieveClient(DefaultHttpClientConfig().copy(useNative = Some(false)), MetaData("initializing", StreamMetaData()), "initializingClient")
    client.shutdown()
  }

  override def info(str: => String): Unit = logger.info(str)
  override def warn(str: => String): Unit = logger.warn(str)
  override def shutdown(client: HttpBackend): Unit = client.close()

  override def create(httpClientConfig: HttpClientConfig, metaData: MetaData): HttpBackend = {
    // Może powinniśmy przełączyć na EC z NK?
    implicit val ec: ExecutionContext = ExecutionContext.global
    val HttpClientConfig = httpClientConfig.toAsyncHttpClientConfig(Option(metaData.id))
    AsyncHttpClientFutureBackend.usingConfigBuilder(_ => HttpClientConfig)
  }
}

trait CacheableClient[T] {

  @volatile private var clients = 0

  //to jest na razie tylko w celach audytowych, logika opiera sie o licznik clients
  @volatile private var clientInfo: Map[ShutdownableClient, String] = Map()

  @volatile private var client = Option.empty[T]

  @volatile private var checkerThread = Option.empty[Thread]

  def info(str: => String): Unit
  def warn(str: => String): Unit

  def retrieveClient(httpClientConfig: HttpClientConfig, metaData: MetaData, serviceName: String): ShutdownableClient = synchronized {
    if (clients == 0) {
      client = Option(create(httpClientConfig, metaData))
      info(s"Created client processName:${metaData.id} serviceName:$serviceName by thread: ${Thread.currentThread().getName}}")
      checkerThread = Some(ShutdownDetector.startCheckerThread(metaData, () => client.isEmpty, forceCloseIfNeeded(httpClientConfig)))
    }
    clients += 1
    val clientToAdd = new ShutdownableClient(client.get)
    val clientAddedMsg = s"Client for node: $serviceName and processName: ${metaData.id} added by thread: ${Thread.currentThread().getName}"
    info(clientAddedMsg)
    clientInfo += (clientToAdd -> clientAddedMsg)
    clientToAdd
  }

  def shutdown(client: T): Unit

  def create(httpClientConfig: HttpClientConfig, metaData: MetaData): T

  class ShutdownableClient(val client: T) {

    def shutdown(): Unit = CacheableClient.this.synchronized {
      if (!clientInfo.contains(this)) {
        warn(s"Client does NOT contain ${this}, but: $clientInfo")
      }
      clients -= 1
      clientInfo -= this
      info(s"Closing client, $clients (${clientInfo.values.mkString(", ")}) left")
      if (clients == 0) {
        close()
      }
    }

  }

  private def forceCloseIfNeeded(config: HttpClientConfig)(): Unit = synchronized {
    val forceShutdown = config.forceShutdown.getOrElse(false)
    if (clientInfo.nonEmpty) {
      warn(s"Clients were not closed! Not closed clients: ${clientInfo.values.mkString(", ")}, forcing shutdown: $forceShutdown")
      //nie jestem calkiem pewien czy wszystko to bedzie dobrze dzialac, wiec na razie domyslnie tylko logowanie
      if (forceShutdown) {
        close()
      }
    }
  }

  private def close(): Unit = {
    client.foreach(shutdown)
    client = None
    checkerThread.foreach(_.interrupt())
    checkerThread = None
    info("Closed ShutdownableClient")
  }
}
