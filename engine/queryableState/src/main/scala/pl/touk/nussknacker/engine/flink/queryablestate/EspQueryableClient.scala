package pl.touk.nussknacker.engine.flink.queryablestate

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.{ExecutionConfig, JobID}
import org.apache.flink.queryablestate.client.QueryableStateClient

import scala.compat.java8.FutureConverters
import org.apache.flink.runtime.state.{VoidNamespace, VoidNamespaceSerializer}
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.QueryableState

import scala.concurrent.{ExecutionContext, Future}

class EspQueryableClient(client: QueryableStateClient) {

  def fetchState[V: TypeInformation](jobId: String, queryName: String, key: String)
                                    (implicit ec: ExecutionContext): Future[V] = {
    val keyTypeInfo = implicitly[TypeInformation[String]]
    val valueTypeInfo = implicitly[TypeInformation[V]]
    val flinkJobId = JobID.fromHexString(jobId)
    val stateDescriptor = new ValueStateDescriptor[V](key, valueTypeInfo)

    FutureConverters.toScala(client.getKvState(flinkJobId, queryName, key, keyTypeInfo, stateDescriptor)).map { valueState =>
      valueState.value()
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