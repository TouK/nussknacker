package pl.touk.nussknacker.engine.flink.queryablestate

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.{ExecutionConfig, JobID}
import org.apache.flink.runtime.query.QueryableStateClient
import org.apache.flink.runtime.query.netty.message.KvStateRequestSerializer
import org.apache.flink.runtime.state.{VoidNamespace, VoidNamespaceSerializer}
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.QueryableState

import scala.concurrent.{ExecutionContext, Future}

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