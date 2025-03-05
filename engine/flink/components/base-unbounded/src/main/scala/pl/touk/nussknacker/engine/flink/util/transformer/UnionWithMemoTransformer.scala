package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{NodeId, _}
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.{
  ContextTransformation,
  JoinContextTransformation,
  ProcessCompilationError,
  ValidationContext
}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.KeyedValueType
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyedValue, StringKeyedValueMapper}
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper
import pl.touk.nussknacker.engine.flink.util.transformer.UnionWithMemoTransformer.KeyField
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.KeyedValue

import java.time.Duration
import java.util

object UnionWithMemoTransformer extends UnionWithMemoTransformer(None)

class UnionWithMemoTransformer(
    timestampAssigner: Option[
      TimestampWatermarkHandler[TimestampedValue[ValueWithContext[StringKeyedValue[util.Map[String, AnyRef]]]]]
    ]
) extends CustomStreamTransformer
    with UnboundedStreamComponent
    with Serializable
    with ExplicitUidInOperatorsSupport {

  val KeyField = "key"

  @transient
  private lazy val typeInfoDetector = TypeInformationDetection.instance

  @MethodToInvoke
  def execute(
      @BranchParamName("key") keyByBranchId: Map[String, LazyParameter[CharSequence]],
      @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[AnyRef]],
      @ParamName("stateTimeout") stateTimeout: Duration,
      @OutputVariableName variableName: String
  )(implicit nodeId: NodeId): JoinContextTransformation =
    ContextTransformation.join
      .definedBy(transformContextsDefinition(valueByBranchId, variableName)(_))
      .implementedBy(
        new FlinkCustomJoinTransformation {

          override def transform(
              inputs: Map[String, DataStream[Context]],
              context: FlinkCustomNodeContext
          ): DataStream[ValueWithContext[AnyRef]] = {
            val finalValidatedCtx =
              transformContextsDefinition(valueByBranchId, variableName)(context.validationContext.toOption.get)

            val finalCtx = finalValidatedCtx.toOption.get

            val mapTypeInfo = typeInfoDetector
              .forType(
                Typed.record(
                  valueByBranchId.mapValuesNow(_.returnType),
                  Typed.typedClass[java.util.Map[_, _]]
                )
              )
              .asInstanceOf[TypeInformation[java.util.Map[String, AnyRef]]]

            val processedTypeInfo = typeInfoDetector.forValueWithContext(finalCtx, KeyedValueType.info(mapTypeInfo))
            val returnTypeInfo    = typeInfoDetector.forValueWithContext(finalCtx, mapTypeInfo)

            val keyedInputStreams = inputs.toList.map { case (branchId, stream) =>
              val valueParam = valueByBranchId(branchId)

              val valueTypeInfo   = typeInfoDetector.forType[AnyRef](valueParam.returnType)
              val flatMapTypeInfo = typeInfoDetector.forValueWithContext(finalCtx, KeyedValueType.info(valueTypeInfo))

              stream
                .map(ctx => ctx.appendIdSuffix(branchId))
                .flatMap(
                  new StringKeyedValueMapper(context, keyByBranchId(branchId), valueParam),
                  flatMapTypeInfo
                )
                .map(valueWithCtx =>
                  valueWithCtx
                    .map(keyedValue =>
                      keyedValue
                        .mapValue(v =>
                          util.Collections.singletonMap(ContextTransformation.sanitizeBranchName(branchId), v)
                        )
                    )
                )
                .returns(processedTypeInfo)
            }
            val connectedStream = keyedInputStreams.reduce(_.connectAndMerge(_))

            val afterOptionalAssigner = timestampAssigner
              .map(new TimestampAssignmentHelper(_)(processedTypeInfo).assignWatermarks(connectedStream))
              .getOrElse(connectedStream)

            setUidToNodeIdIfNeed(
              context,
              afterOptionalAssigner
                .keyBy((v: ValueWithContext[KeyedValue[String, util.Map[String, AnyRef]]]) => v.value.key)
                .process(new UnionMemoFunction(stateTimeout, mapTypeInfo), returnTypeInfo)
            ).asInstanceOf[SingleOutputStreamOperator[ValueWithContext[AnyRef]]]
          }

        }
      )

  def transformContextsDefinition(valueByBranchId: Map[String, LazyParameter[AnyRef]], variableName: String)(
      inputContexts: Map[String, ValidationContext]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val ids = valueByBranchId.keySet

    val validatedIdentical = NonEmptyList
      .fromList(ContextTransformation.checkIdenticalSanitizedNodeNames(ids.toList))
      .map(Validated.invalid)
      .getOrElse(Validated.validNel(()))

    val validatedBranches = NonEmptyList
      .fromList(ContextTransformation.checkNotAllowedNodeNames(ids.toList, Set(KeyField)))
      .map(Validated.invalid)
      .getOrElse(Validated.validNel(()))

    val validatedContext = ContextTransformation.findUniqueParentContext(inputContexts).map { parent =>
      val newType = Typed.record(
        inputContexts.map { case (branchId, _) =>
          ContextTransformation.sanitizeBranchName(branchId) -> valueByBranchId(branchId).returnType
        } + (KeyField -> Typed[String])
      )
      ValidationContext(Map(variableName -> newType), Map.empty, parent)
    }
    validatedIdentical.product(validatedBranches).product(validatedContext).map(_._2)
  }

}

class UnionMemoFunction(stateTimeout: Duration, typeInfo: TypeInformation[java.util.Map[String, AnyRef]])
    extends LatelyEvictableStateFunction[ValueWithContext[
      StringKeyedValue[java.util.Map[String, AnyRef]]
    ], ValueWithContext[java.util.Map[String, AnyRef]], java.util.Map[String, AnyRef]] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[
    StringKeyedValue[java.util.Map[String, AnyRef]]
  ], ValueWithContext[java.util.Map[String, AnyRef]]]#Context

  override protected def stateDescriptor: ValueStateDescriptor[java.util.Map[String, AnyRef]] = {
    new ValueStateDescriptor("state", typeInfo)
  }

  override def processElement(
      valueWithCtx: ValueWithContext[StringKeyedValue[java.util.Map[String, AnyRef]]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[java.util.Map[String, AnyRef]]]
  ): Unit = {
    val currentState = Option(readState()).getOrElse(new util.HashMap[String, AnyRef]())
    val passedMap    = valueWithCtx.value.value
    currentState.put(KeyField, valueWithCtx.value.key)
    passedMap.forEach((k, v) => if (v != null) currentState.put(k, v))
    updateState(currentState, ctx.timestamp() + stateTimeout.toMillis, ctx.timerService())
    out.collect(ValueWithContext(currentState, valueWithCtx.context))
  }

}
