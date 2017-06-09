package pl.touk.esp.engine.flink.queryablestate

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.{ExecutionConfig, JobID}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.query.QueryableStateClient
import org.apache.flink.runtime.query.netty.message.KvStateRequestSerializer
import org.apache.flink.runtime.state.{VoidNamespace, VoidNamespaceSerializer}
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api.QueryableState

import scala.concurrent.{ExecutionContext, Future}

object EspQueryableClient {

  def apply(config: Config): EspQueryableClient = {
    val configuration = mapToFlinkConfiguration(config)
    new EspQueryableClient(new QueryableStateClient(configuration))
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

class EspQueryableClient(client: QueryableStateClient) {

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
    fetchState[String](jobId, queryName)
  }

  def fetchState[V: TypeInformation](jobId: String, queryName: String)
                (implicit ec: ExecutionContext): Future[V] = {
    fetchState[V](jobId, queryName, QueryableState.defaultKey)
  }

}