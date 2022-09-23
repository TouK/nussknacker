package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.MapTypeInfo
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.co.CoMapFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyedValue, StringKeyedValueMapper}
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper
import pl.touk.nussknacker.engine.flink.util.transformer.UnionWithMemoTransformer.KeyField
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.typeinformation.{KeyedValueType, TupleType, ValueWithContextType}
import pl.touk.nussknacker.engine.util.KeyedValue

import java.time.Duration
import java.util

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
          private val processedInnerTypeInfo = Typed.fromDetailedType[KeyedValue[String, (String, AnyRef)]]

          private def processedTypeInfoBranch(ctx: FlinkCustomNodeContext, key: String):
            TypeInformation[ValueWithContext[KeyedValue[String, (String, AnyRef)]]] =
            ValueWithContextType.infoBranch(ctx, key, processedInnerTypeInfo)

          private def processedTypeInfo(ctx: FlinkCustomNodeContext, finalCtx: ValidationContext):
            TypeInformation[ValueWithContext[KeyedValue[String, (String, AnyRef)]]] =
            ValueWithContextType.infoWithCustomContext(ctx, finalCtx, processedInnerTypeInfo)


          override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
            val keyedInputStreams = inputs.toList.map {
              case (branchId, stream) =>
                val keyParam = keyByBranchId(branchId)
                val valueParam = valueByBranchId(branchId)
                stream
                  .flatMap(new StringKeyedValueMapper(context, keyParam, valueParam))
                  .map(_.map(_.mapValue(v => (ContextTransformation.sanitizeBranchName(branchId), v))))
                  .returns(processedTypeInfoBranch(context, branchId))
            }
            val connectedStream = keyedInputStreams.reduce(_.connectAndMerge(_))

            val finalContextValidated = transformContextsDefinition(valueByBranchId, variableName)(context.validationContext.right.get)
            val finalContext = finalContextValidated.toOption.get

            // TODO: Add better TypeInformation
            val afterOptionalAssigner = timestampAssigner
              .map(new TimestampAssignmentHelper[ValueWithContext[KeyedValue[String, (String, AnyRef)]]](_)(processedTypeInfo(context, finalContext))
                .assignWatermarks(connectedStream))
              .getOrElse(connectedStream)

            setUidToNodeIdIfNeed(context, afterOptionalAssigner
              .keyBy((v: ValueWithContext[KeyedValue[String, (String, AnyRef)]]) => v.value.key)
              .process(new UnionMemoFunction(stateTimeout)))
          }
        }
      )

  def transformContextsDefinition(valueByBranchId: Map[String, LazyParameter[AnyRef]], variableName: String)
                                 (inputContexts: Map[String, ValidationContext])
                                 (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val ids = valueByBranchId.keySet

    val validatedIdentical = NonEmptyList
      .fromList(ContextTransformation.checkIdenticalSanitizedNodeNames(ids.toList))
      .map(Validated.invalid)
      .getOrElse(Validated.validNel(Unit))

    val validatedBranches = NonEmptyList
      .fromList(ContextTransformation.checkNotAllowedNodeNames(ids.toList, Set(KeyField)))
      .map(Validated.invalid)
      .getOrElse(Validated.validNel(Unit))

    val validatedContext = ContextTransformation.findUniqueParentContext(inputContexts).map { parent =>
      val newType = TypedObjectTypingResult(
        (KeyField -> Typed[String]) :: inputContexts.map {
          case (branchId, _) =>
            ContextTransformation.sanitizeBranchName(branchId) -> valueByBranchId(branchId).returnType
        }.toList
      )
      ValidationContext(Map(variableName -> newType), Map.empty, parent)
    }
    validatedIdentical.product(validatedBranches).product(validatedContext).map(_._2)
  }
}

class UnionMemoFunction(stateTimeout: Duration) extends LatelyEvictableStateFunction[ValueWithContext[StringKeyedValue[(String, AnyRef)]], ValueWithContext[AnyRef], java.util.Map[String, AnyRef]] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[(String, AnyRef)]], ValueWithContext[AnyRef]]#Context

  // TODO: Add TypeInformation depending on context.
  override protected def stateDescriptor: ValueStateDescriptor[java.util.Map[String, AnyRef]] = {
    new ValueStateDescriptor("state", new MapTypeInfo(TypeInformation.of(classOf[String]), TypeInformation.of(classOf[AnyRef])))
  }

  override def processElement(valueWithCtx: ValueWithContext[StringKeyedValue[(String, AnyRef)]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentState = Option(readState()).getOrElse(new util.HashMap[String, AnyRef]())
    val (sanitizedBranchName, value) = valueWithCtx.value.value
    currentState.put(KeyField, valueWithCtx.value.key)
    currentState.put(sanitizedBranchName, value)
    updateState(currentState, ctx.timestamp() + stateTimeout.toMillis, ctx.timerService())
    out.collect(new ValueWithContext[AnyRef](currentState, valueWithCtx.context))
  }

}
