package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.{AdditionalVariable, AdditionalVariables, Context, CustomStreamTransformer, LazyParameter, MethodToInvoke, ParamName, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import org.apache.flink.streaming.api.scala._

object AdditionalVariableTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Void])
  def execute(@AdditionalVariables(Array(new AdditionalVariable(name = "additional", clazz = classOf[String]))) @ParamName("expression") expression: LazyParameter[java.lang.Boolean]) =
    FlinkCustomStreamTransformation((start: DataStream[Context]) => start.map(ValueWithContext[AnyRef]("", _)))

}
