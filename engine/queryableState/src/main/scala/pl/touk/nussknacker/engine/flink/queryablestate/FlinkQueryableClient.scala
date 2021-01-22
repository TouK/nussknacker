package pl.touk.nussknacker.engine.flink.queryablestate

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.apache.flink.api.common.{ExecutionConfig, JobID}
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.queryablestate.client.QueryableStateClient
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentId
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.api.queryablestate.{QueryableClient, QueryableState}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation

import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object FlinkQueryableClient {

  /**
   * Creates FlinkQueryableClient using Flink's default deserialization strategy (ExecutionConfig)
   * @param queryableStateProxyUrl comma separated list of proxy urls
   */
  def apply(queryableStateProxyUrl: String): FlinkQueryableClient = {
    FlinkQueryableClient(queryableStateProxyUrl, new ExecutionConfig)
  }

  /**
   * Creates FlinkQueryableClient using custom deserialization strategy (ExecutionConfig)
   * @param queryableStateProxyUrl comma separated list of proxy urls
   * @param executionConfig config that is used by underlying QueryableStateClient - you can use it to set deserialization strategy.
   *                        keep in mind that deserializers should match serializers used in state. in case if you query
   *                        state serialized by NK process, probable you should prepare this config using
   *                        `pl.touk.nussknacker.engine.process.util.Serializers.registerSerializers()` method.
   */
  def apply(queryableStateProxyUrl: String, executionConfig: ExecutionConfig): FlinkQueryableClient = {

    //TODO: this is workaround for https://issues.apache.org/jira/browse/FLINK-10225, we want to be able to configure all task managers
    val queryableStateProxyUrlsParts = queryableStateProxyUrl.split(",").toList.map { url =>
      parseHostAndPort(url.trim)
    }
    def createClients = queryableStateProxyUrlsParts.map {
      case (queryableStateProxyHost, queryableStateProxyPort) =>
        val client = new QueryableStateClient(queryableStateProxyHost, queryableStateProxyPort)
        client.setExecutionConfig(executionConfig)
        client
    }
    new FlinkQueryableClient(createClients)
  }

  private def parseHostAndPort(url: String): (String, Int) = {
    val parts = url.split(':')
    if (parts.length != 2)
      throw new IllegalArgumentException("Should by in host:port format")
    (parts(0), parts(1).toInt)
  }
}

class FlinkQueryableClient(createClients: => List[QueryableStateClient]) extends QueryableClient with LazyLogging {

  private lazy val clients = createClients
  
  def fetchState[V: TypeInformation](jobId: DeploymentId, queryName: String, key: String)
                                    (implicit ec: ExecutionContext): Future[V] = {
    val keyTypeInfo = implicitly[TypeInformation[String]]
    val valueTypeInfo = implicitly[TypeInformation[V]]
    val flinkJobId = JobID.fromHexString(jobId.value)
    val stateDescriptor = new ValueStateDescriptor[V](key, valueTypeInfo)

    //TODO: this is workaround for https://issues.apache.org/jira/browse/FLINK-10225, we want to be able to configure all task managers
    def tryToFetchState(clientsToCheck: List[QueryableStateClient], lastException: Option[Throwable]): Future[V] = clientsToCheck match {
      case next::rest =>
        FutureConverters.toScala(next.getKvState(flinkJobId, queryName, key, keyTypeInfo, stateDescriptor)).map { valueState =>
          valueState.value()
        }.recoverWith {
          case NonFatal(e) =>
            logger.trace(s"Failed to fetch state from $next with ${e.getMessage}", e)
            tryToFetchState(rest, Some(e))
        }
      case Nil =>
        Future.failed(new IllegalArgumentException(
          s"Failed to retrieve queryable state, last exception: ${lastException.map(_.getMessage).getOrElse("")}", lastException.orNull))
    }

    tryToFetchState(clients, None)
  }

  def fetchJsonState(jobId: DeploymentId, queryName: String, key: String)
                    (implicit ec: ExecutionContext): Future[String] = {
    fetchState[String](jobId, queryName, key)
  }

  def fetchJsonState(jobId: DeploymentId, queryName: String)
                    (implicit ec: ExecutionContext): Future[String] = {
    fetchState[String](jobId, queryName)
  }

  def fetchState[V: TypeInformation](jobId: DeploymentId, queryName: String)
                (implicit ec: ExecutionContext): Future[V] = {
    fetchState[V](jobId, queryName, QueryableState.defaultKey)
  }

  override def fetchState(deploymentId: DeploymentId,
                          nodeId: NodeId,
                          queryName: String,
                          transformationObject: Any,
                          keyId: String
                )(implicit ec: ExecutionContext): Future[AnyRef] = {
    implicit val transformation: TypeInformation[AnyRef] = transformationObject.asInstanceOf[FlinkCustomStreamTransformation]
      .queryableStateTypes().apply(queryName).asInstanceOf[TypeInformation[AnyRef]]
    //TODO: probably we should include nodeId in state name, otherwise there won't be possibility to have e.g two aggregates?
    val keyWithNode = /*nodeId.id + "-" +*/ keyId
    fetchState[AnyRef](deploymentId, queryName, keyWithNode)
  }

  def close(): Unit = {
    //QueryableClient are not particularly robust, so we don't want to crash on exit
    try {
      clients.foreach(_.shutdownAndWait())
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Failed to close queryable client(s)", e)
    }
  }

}
