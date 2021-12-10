package pl.touk.nussknacker.engine.api.runtimecontext

import pl.touk.nussknacker.engine.api.{JobData, MetaData}

import java.util.concurrent.atomic.AtomicLong

/**
  * Context id generator - it should fulfill rules:
  * - should produce unique ids across all nodes used in all scenarios during normal engine run - we assume that one node
  *   implementation can't use multiple generators on one execution unit and it is not mandatory to be unique after job restart.
  *   If engine creates many instances of ContextIdGenerator for each execution unit, each generator should has separate pool of ids
  * - is easy to read by end-user (will be presented in testing mechanism in designer)
  */
trait ContextIdGenerator {

  def nextContextId(): String

}

class IncContextIdGenerator(prefix: String, counter: AtomicLong = new AtomicLong(0)) extends ContextIdGenerator {

  override def nextContextId(): String = prefix + "-" + counter.getAndIncrement()

}

object IncContextIdGenerator {

  def withProcessIdNodeIdPrefix(jobData: JobData, nodeId: String): IncContextIdGenerator =
    withProcessIdNodeIdPrefix(jobData.metaData, nodeId)

  def withProcessIdNodeIdPrefix(metaData: MetaData, nodeId: String): IncContextIdGenerator =
    new IncContextIdGenerator(metaData.id + "-" + nodeId)

}