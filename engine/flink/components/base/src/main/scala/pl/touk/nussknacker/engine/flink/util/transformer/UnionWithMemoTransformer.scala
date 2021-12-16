package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.ValidatedNel
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyedValue, StringKeyedValueMapper}
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper
import pl.touk.nussknacker.engine.flink.util.transformer.UnionWithMemoTransformer.KeyField
import pl.touk.nussknacker.engine.util.KeyedValue

import java.time.Duration

object UnionWithMemoTransformer extends UnionWithMemoTransformer(None)

class UnionWithMemoTransformer(timestampAssigner: Option[TimestampWatermarkHandler[TimestampedValue[ValueWithContext[StringKeyedValue[(String, AnyRef)]]]]])
  extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

  val KeyField = "key"

  override def canHaveManyInputs: Boolean = true

  @MethodToInvoke
  def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[CharSequence]],
              @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[AnyRef]],
              @ParamName("stateTimeout") stateTimeout: Duration,
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation =
    ContextTransformation
      .join.definedBy(transformContextsDefinition(valueByBranchId, variableName)(_))
      .implementedBy(
        new FlinkCustomJoinTransformation {

          override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
            val keyedInputStreams = inputs.toList.map {
              case (branchId, stream) =>
                val keyParam = keyByBranchId(branchId)
                val valueParam = valueByBranchId(branchId)
                stream
                  .flatMap(new StringKeyedValueMapper(context, keyParam, valueParam))
                  .map(_.map(_.mapValue(v => (ContextTransformation.sanitizeBranchName(branchId), v))))
            }
            val connectedStream = keyedInputStreams.reduce(_.connect(_).map(mapElement, mapElement))

            val afterOptionalAssigner = timestampAssigner
              .map(new TimestampAssignmentHelper[ValueWithContext[StringKeyedValue[(String, AnyRef)]]](_).assignWatermarks(connectedStream))
              .getOrElse(connectedStream)

            setUidToNodeIdIfNeed(context, afterOptionalAssigner
              .keyBy(_.value.key)
              .process(new UnionMemoFunction(stateTimeout)))
          }
        }
      )


  protected def mapElement: ValueWithContext[KeyedValue[String, (String, AnyRef)]] => ValueWithContext[KeyedValue[String, (String, AnyRef)]] = identity

  def transformContextsDefinition(valueByBranchId: Map[String, LazyParameter[AnyRef]], variableName: String)
                                 (inputContexts: Map[String, ValidationContext])
                                 (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    ContextTransformation.findUniqueParentContext(inputContexts).map { parent =>
      val newType = TypedObjectTypingResult(
        (KeyField -> Typed[String]) :: inputContexts.map {
          case (branchId, _) =>
            ContextTransformation.sanitizeBranchName(branchId) -> valueByBranchId(branchId).returnType
        }.toList
      )
      ValidationContext(Map(variableName -> newType), Map.empty, parent)
    }
  }
}

class UnionMemoFunction(stateTimeout: Duration) extends LatelyEvictableStateFunction[ValueWithContext[StringKeyedValue[(String, AnyRef)]], ValueWithContext[AnyRef], Map[String, AnyRef]] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[(String, AnyRef)]], ValueWithContext[AnyRef]]#Context

  import scala.collection.JavaConverters._

  override protected def stateDescriptor: ValueStateDescriptor[Map[String, AnyRef]] =
    new ValueStateDescriptor("state", implicitly[TypeInformation[Map[String, AnyRef]]])

  override def processElement(valueWithCtx: ValueWithContext[StringKeyedValue[(String, AnyRef)]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentState = Option(readState()).getOrElse(Map.empty)
    val (sanitizedBranchName, value) = valueWithCtx.value.value
    val newValue = Map(
      KeyField -> valueWithCtx.value.key,
      sanitizedBranchName -> value
    )
    val mergedValue = currentState ++ newValue
    updateState(mergedValue, ctx.timestamp() + stateTimeout.toMillis, ctx.timerService())
    out.collect(new ValueWithContext[AnyRef](mergedValue.asJava, valueWithCtx.context))
  }

}
