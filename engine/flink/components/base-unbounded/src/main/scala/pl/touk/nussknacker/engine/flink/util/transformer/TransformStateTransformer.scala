package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkCustomStreamTransformation,
  FlinkLazyParameterFunctionHelper,
  LazyParameterInterpreterFunction
}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.util.richflink.FlinkKeyOperations

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
object TransformStateTransformer extends CustomStreamTransformer {

  private val groupByParameterName = ParameterName("groupBy")

  @MethodToInvoke(returnType = classOf[AnyRef])
  def invoke(
      @ParamName("groupBy") groupBy: LazyParameter[CharSequence],
      @ParamName("transformWhen") transformWhen: LazyParameter[java.lang.Boolean],
      @AdditionalVariables(Array(new AdditionalVariable(name = "previous", clazz = classOf[AnyRef])))
      @ParamName("newValue") newValue: LazyParameter[AnyRef],
      @ParamName("stateTimeoutSeconds") stateTimeoutSeconds: Long,
      @OutputVariableName variableName: String
  )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation
      .definedBy(_.withVariable(OutputVar.customNode(variableName), newValue.returnType))
      .implementedBy(
        FlinkCustomStreamTransformation { (stream, ctx) =>
          implicit val nctx: FlinkCustomNodeContext = ctx
          stream
            .groupBy(groupBy, groupByParameterName)
            .process(
              new TransformStateFunction[String](
                ctx.lazyParameterHelper,
                transformWhen,
                newValue,
                stateTimeoutSeconds.seconds
              ),
              ctx.valueWithContextInfo.forClass[AnyRef](classOf[String])
            )
            .setUidAndName(ctx.nodeId.value, ctx.nodeName.value)
        }
      )

}

//We don't actually care about T here, but it's needed because of type/variance problems...
class TransformStateFunction[T](
    protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
    transformWhenParam: LazyParameter[java.lang.Boolean],
    newValueParam: LazyParameter[AnyRef],
    stateTimeout: FiniteDuration
) extends LatelyEvictableStateFunction[ValueWithContext[T], ValueWithContext[AnyRef], AnyRef, String]
    with LazyParameterInterpreterFunction {

  override protected def stateDescriptor: ValueStateDescriptor[AnyRef] = {
    val valueType: TypeInformation[AnyRef] = TypeInformationDetection.instance.forType(newValueParam.returnType)
    new ValueStateDescriptor("state", valueType)
  }

  private lazy val evaluateTransformWhen = toEvaluateFunctionConverter.toEvaluateFunction(transformWhenParam)

  private lazy val evaluateNewValue = toEvaluateFunctionConverter.toEvaluateFunction(newValueParam)

  override def processElement(
      keyWithContext: ValueWithContext[T],
      ctx: KeyedProcessFunction[String, ValueWithContext[T], ValueWithContext[AnyRef]]#Context,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    collectHandlingErrors(keyWithContext.context, out) {
      val previousValue = state.value()
      val newValue = if (evaluateTransformWhen(keyWithContext.context)) {
        val newValue = evaluateNewValue(keyWithContext.context.withVariable("previous", previousValue))
        state.update(newValue)
        moveEvictionTime(stateTimeout.toMillis, ctx)
        newValue
      } else {
        previousValue
      }
      keyWithContext.copy(value = newValue)
    }
  }

}
