package pl.touk.nussknacker.engine.flink.util.context

import java.util.concurrent.atomic.AtomicLong

import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.Context

case class InitContextFunction[T](processId: String, taskName: String) extends RichMapFunction[T, Context] with ContextInitializingFunction {

  override def open(parameters: Configuration): Unit = {
    init(getRuntimeContext)
  }

  override def map(input: T): Context = newContext.withVariable(BaseContext.InputVariableName, input)
}


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

object BaseContext {

  final val InputVariableName = "input"
  final val MetaVariableName = "meta"
  final val OutputVariableName = "output"

}