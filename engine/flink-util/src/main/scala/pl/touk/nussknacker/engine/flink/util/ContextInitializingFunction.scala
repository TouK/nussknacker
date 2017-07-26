package pl.touk.nussknacker.engine.flink.util

import java.util.concurrent.atomic.AtomicLong

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.Context

trait ContextInitializingFunction extends Serializable {

  private val counter = new AtomicLong(0)

  private var name : String = _

  protected def processId: String

  def taskName: String

  protected def init(ctx: RuntimeContext) = {
    name = s"$processId-$taskName-${ctx.getIndexOfThisSubtask}"
  }

  protected def newContext = Context(s"$name-${counter.getAndIncrement()}")

}
