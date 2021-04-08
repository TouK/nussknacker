package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.flink.util.ContextInitializingFunction

private[registrar] case class InitContextFunction(processId: String, taskName: String) extends RichMapFunction[Any, Context] with ContextInitializingFunction {

  override def open(parameters: Configuration): Unit = {
    init(getRuntimeContext)
  }

  override def map(input: Any): Context = newContext.withVariable(VariableConstants.InputVariableName, input)
}