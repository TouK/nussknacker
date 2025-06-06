package pl.touk.nussknacker.engine.api.runtimecontext

import pl.touk.nussknacker.engine.api.{ContextId, JobData, MetaData}
import pl.touk.nussknacker.engine.api.process.ProcessName

import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._

/**
  * Context id generator - it should fulfill rules:
  * - should produce unique ids across all nodes used in all scenarios during normal engine run - we assume that one node
  *   implementation can't use multiple generators on one execution unit and it is not mandatory to be unique after job restart.
  *   If engine creates many instances of ContextIdGenerator for each execution unit, each generator should has separate pool of ids
  * - is easy to read by end-user (will be presented in testing mechanism in designer)
  */
trait ContextIdGenerator {

  def nextContextId(): ContextId

}

class IncContextIdGenerator(
    scenarioId: ProcessName,
    nodeId: String,
    taskId: Long,
    counter: AtomicLong = new AtomicLong(0),
) extends ContextIdGenerator {

  override def nextContextId(): ContextId =
    ContextId(scenarioId.value, nodeId, taskId, counter.getAndIncrement(), List.empty.asJava)

}

object IncContextIdGenerator {

  def withProcessIdNodeIdPrefix(jobData: JobData, nodeId: String, taskId: Long): IncContextIdGenerator =
    withProcessIdNodeIdPrefix(jobData.metaData, nodeId, taskId)

  def withProcessIdNodeIdPrefix(metaData: MetaData, nodeId: String, taskId: Long): IncContextIdGenerator =
    new IncContextIdGenerator(metaData.name, nodeId, taskId)

}
