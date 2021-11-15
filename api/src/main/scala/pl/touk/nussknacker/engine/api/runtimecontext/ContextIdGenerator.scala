package pl.touk.nussknacker.engine.api.runtimecontext

import java.util.concurrent.atomic.AtomicLong

/**
  * Context id generator - context id should fulfill rules:
  * - should be unique across all nodes used in all scenarios during normal engine run - we assume that one node can't use multiple generators
  *   and it is not mandatory to be unique after job restart
  * - is easy to read by end user (will be presented in testing mechanism on designer)
  */
trait ContextIdGenerator {

  def nextContextId(): String

}

class IncContextIdGenerator(prefix: String, counter: AtomicLong = new AtomicLong(0)) extends ContextIdGenerator {

  override def nextContextId(): String = prefix + "-" + counter.getAndIncrement()

}