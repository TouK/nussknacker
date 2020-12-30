package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomStreamTransformation, FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction

import scala.concurrent.duration._

/**
  * This is general usage state transformation. It takes previous value of state and transform it using `newValue`
  * lambda parameter (having `previous` as a parameter). Transformation is done only when `transformWhen` expression
  * is satisfied. States has Time-To-Leave defined by `stateTimeoutSeconds` parameter.
  * So it is generally doing something like this:
  * ```
  * newStateValue = if (transformWhen) {
  *   newValue(previous)
  * } else {
  *   previous
  * }
  * ```
  */
object TransformStateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

  @MethodToInvoke(returnType = classOf[AnyRef])
  def invoke(@ParamName("key") key: LazyParameter[CharSequence],
             @ParamName("transformWhen") transformWhen: LazyParameter[java.lang.Boolean],
             @AdditionalVariables(Array(new AdditionalVariable(name = "previous", clazz = classOf[AnyRef])))
             @ParamName("newValue") newValue: LazyParameter[AnyRef],
             @ParamName("stateTimeoutSeconds") stateTimeoutSeconds: Long,
             @OutputVariableName variableName: String)
            (implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation
      .definedBy(_.withVariable(variableName, newValue.returnType))
      .implementedBy(
        FlinkCustomStreamTransformation { (stream, nodeContext) =>
          setUidToNodeIdIfNeed(nodeContext,
            stream
              .map(nodeContext.lazyParameterHelper.lazyMapFunction(key))
              .keyBy(vCtx => Option(vCtx.value).map(_.toString).orNull)
              .process(new TransformStateFunction(
                nodeContext.lazyParameterHelper, transformWhen, newValue, stateTimeoutSeconds.seconds)))
        }
      )
}


class TransformStateFunction(protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                             transformWhenParam: LazyParameter[java.lang.Boolean],
                             newValueParam: LazyParameter[AnyRef],
                             stateTimeout: FiniteDuration)
  extends LatelyEvictableStateFunction[ValueWithContext[CharSequence], ValueWithContext[AnyRef], GenericState]
  with LazyParameterInterpreterFunction {

  override protected def stateDescriptor: ValueStateDescriptor[GenericState] =
    new ValueStateDescriptor[GenericState]("state", classOf[GenericState])

  private lazy val evaluateTransformWhen = lazyParameterInterpreter.syncInterpretationFunction(transformWhenParam)

  private lazy val evaluateNewValue = lazyParameterInterpreter.syncInterpretationFunction(newValueParam)

  override def processElement(keyWithContext: ValueWithContext[CharSequence],
                              ctx: KeyedProcessFunction[String, ValueWithContext[CharSequence], ValueWithContext[AnyRef]]#Context,
                              out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val previousValue = Option(state.value()).map(_.value).orNull
    val newValue = if (evaluateTransformWhen(keyWithContext.context)) {
      val newValue = evaluateNewValue(keyWithContext.context.withVariable("previous", previousValue))
      state.update(GenericState(newValue))
      moveEvictionTime(stateTimeout.toMillis, ctx)
      newValue
    } else {
      previousValue
    }
    out.collect(keyWithContext.copy(value = newValue))
  }

}

case class GenericState(value: AnyRef)
