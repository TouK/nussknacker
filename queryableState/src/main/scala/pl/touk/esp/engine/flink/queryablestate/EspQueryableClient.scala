package pl.touk.esp.engine.flink.queryablestate

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.{ExecutionConfig, JobID}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.query.QueryableStateClient
import org.apache.flink.runtime.query.netty.message.KvStateRequestSerializer
import org.apache.flink.runtime.state.{VoidNamespace, VoidNamespaceSerializer}
import org.apache.flink.streaming.api.scala._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object EspQueryableClient {

  def apply(config: Config): EspQueryableClient = {
    val configuration = mapToFlinkConfiguration(config)
    val defaultKey = Try(config.getString("queryableState.defaultKey")).toOption
    new EspQueryableClient(new QueryableStateClient(configuration), defaultKey)
  }

  private def mapToFlinkConfiguration(config: Config) = {
    import scala.collection.JavaConverters._
    val plainConfig = config.entrySet().asScala.map(m => (m.getKey, m.getValue)).toMap.mapValues(_.unwrapped().toString)
    val configuration = new Configuration
    plainConfig.foreach { case (k, v) =>
      configuration.setString(k, v)
    }
    configuration
  }
}

class EspQueryableClient(client: QueryableStateClient, defaultKey: Option[String]) {

  def fetchState[V: TypeInformation](jobId: String, queryName: String, key: String)
                      (implicit ec: ExecutionContext): Future[V] = {
    val keySerializer = implicitly[TypeInformation[String]].createSerializer(new ExecutionConfig)
    val valueSerializer = implicitly[TypeInformation[V]].createSerializer(new ExecutionConfig)
    val flinkJobId = JobID.fromHexString(jobId)
    val serializedKey = KvStateRequestSerializer.serializeKeyAndNamespace(key, keySerializer, VoidNamespace.INSTANCE, VoidNamespaceSerializer.INSTANCE)

    client.getKvState(flinkJobId, queryName, key.hashCode, serializedKey).map { serializedResult =>
      KvStateRequestSerializer.deserializeValue(serializedResult, valueSerializer)
    }
  }

  def fetchJsonState(jobId: String, queryName: String, key: String)
                    (implicit ec: ExecutionContext): Future[String] = {
    fetchState[String](jobId, queryName, key)
  }

  def fetchJsonState(jobId: String, queryName: String)
                    (implicit ec: ExecutionContext): Future[String] = {
    fetchState[String](jobId, queryName, defaultKey.getOrElse(throw new RuntimeException("Default key for queryable state not defined")))
  }

  def fetchState[V: TypeInformation](jobId: String, queryName: String)
                (implicit ec: ExecutionContext): Future[V] = {
    fetchState[V](jobId, queryName, defaultKey.getOrElse(throw new RuntimeException("Default key for queryable state not defined")))
  }

}