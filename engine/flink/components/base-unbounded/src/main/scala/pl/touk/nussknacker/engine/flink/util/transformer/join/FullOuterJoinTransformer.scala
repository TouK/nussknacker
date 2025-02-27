package pl.touk.nussknacker.engine.flink.util.transformer.join

import cats.Id
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.{
  ContextTransformation,
  OutputVar,
  ProcessCompilationError,
  ValidationContext
}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.KeyedValueType
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyedValue, StringKeyedValueMapper}
import pl.touk.nussknacker.engine.flink.util.richflink._
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.{AggregateHelper, Aggregator}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.aggregates.{MapAggregator, OptionAggregator}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.KeyedValue

import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class FullOuterJoinTransformer(
    timestampAssigner: Option[TimestampWatermarkHandler[TimestampedValue[ValueWithContext[AnyRef]]]]
) extends CustomStreamTransformer
    with JoinDynamicComponent[FlinkCustomJoinTransformation]
    with ExplicitUidInOperatorsSupport
    with WithExplicitTypesToExtract
    with LazyLogging
    with Serializable {

  import pl.touk.nussknacker.engine.flink.util.transformer.join.FullOuterJoinTransformer._

  override type State = Nothing

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(contexts: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val ids          = contexts.keySet
      val errors_names = ContextTransformation.checkIdenticalSanitizedNodeNames(ids.toList)
      val errors_key   = ContextTransformation.checkNotAllowedNodeNames(ids.toList, Set(KeyFieldName))
      NextParameters(
        parameters = List(
          KeyParamDeclaration,
          AggregatorParamDeclaration,
          AggregateByParamDeclaration,
          WindowLengthParamDeclaration
        )
          .map(_.createParameter()),
        errors = errors_names ++ errors_key
      )

    case TransformationStep(
          (`KeyParamName`, _) ::
          (
            `AggregatorParamName`,
            DefinedEagerBranchParameter(aggregatorByBranchId: Map[String, Aggregator] @unchecked, _)
          ) ::
          (`AggregateByParamName`, aggregateByParam) ::
          (`WindowLengthParamName`, _) ::
          Nil,
          _
        ) =>
      val outName = OutputVariableNameDependency.extract(dependencies)
      val mainCtx = contexts.headOption.map(_._2.clearVariables).getOrElse(ValidationContext())

      val validatedOutputType: ValidatedNel[ProcessCompilationError, TypingResult] = aggregateByParam match {
        case DefinedLazyBranchParameter(aggregateByByBranchId) =>
          val validatedAggregatorReturnTypes = aggregatorByBranchId
            .map { case (id, agg) =>
              agg
                .computeOutputType(aggregateByByBranchId(id))
                .leftMap(x => {
                  val branchParamId = AggregateByParamDeclaration.parameterName.withBranchId(id)
                  NonEmptyList.one(CustomNodeError(x, Some(branchParamId)))
                })
                .map(id -> _)
            }
            .toList
            .sequence
            .map(_.toMap)
          validatedAggregatorReturnTypes.map(outputTypeByBranchId => {
            val outputTypes = outputTypeByBranchId
              .map { case (k, v) => ContextTransformation.sanitizeBranchName(k) -> v } + (KeyFieldName -> Typed
              .typedClass[String])
            Typed.record(outputTypes)
          })

        case _ => Validated.validNel(Unknown)
      }

      FinalResults.forValidation(mainCtx, validatedOutputType.swap.map(_.toList).getOrElse(List()))(
        _.withVariable(OutputVar.customNode(outName), validatedOutputType.getOrElse(Unknown))
      )
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): FlinkCustomJoinTransformation = {
    val keyByBranchId: Map[String, LazyParameter[CharSequence]] = KeyParamDeclaration.extractValueUnsafe(params)
    val aggregatorByBranchId: Map[String, Aggregator]           = AggregatorParamDeclaration.extractValueUnsafe(params)
    val aggregateByByBranchId: Map[String, LazyParameter[AnyRef]] =
      AggregateByParamDeclaration.extractValueUnsafe(params)
    val window: Duration = WindowLengthParamDeclaration.extractValueUnsafe(params)
    val typeInfoDetector = TypeInformationDetection.instance

    val aggregator: Aggregator = new MapAggregator(
      aggregatorByBranchId.mapValuesNow(new OptionAggregator(_).asInstanceOf[Aggregator]).asJava
    )

    val baseElement: Map[String, AnyRef] = keyByBranchId.keySet.map(_ -> None).toMap

    (inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext) => {
      val keyedStreams = inputs.map { case (id, stream) =>
        val valueParameter: LazyParameter[AnyRef] = aggregateByByBranchId(id)
        val valueTypeInfo                         = typeInfoDetector.forType[AnyRef](valueParameter.returnType)

        val branchTypeInfo = context.valueWithContextInfo.forBranch(id, KeyedValueType.info(valueTypeInfo))

        stream
          .flatMap(
            new StringKeyedValueMapper(context, keyByBranchId(id), valueParameter),
            branchTypeInfo
          )
          .map(_.map(_.mapValue { x =>
            val sanitizedId = ContextTransformation.sanitizeBranchName(id)
            (baseElement + (sanitizedId -> Some(x))).asJava.asInstanceOf[AnyRef]
          }))
          .returns(
            context.valueWithContextInfo.forBranch[KeyedValue[String, AnyRef]](
              id,
              KeyedValueType.info[String, AnyRef](
                Types.STRING,
                Types
                  .MAP(
                    Types.STRING,
                    // FIXME: Passing the valueTypeInfo here will cause serialisation problems with a List[Int]
                    new OptionTypeInfo(TypeInformation.of(classOf[AnyRef]))
                  )
                  .asInstanceOf[TypeInformation[AnyRef]]
              )
            )
          )
      }

      val types       = aggregateByByBranchId.mapValuesNow(_.returnType)
      val optionTypes = types.mapValuesNow(t => Typed.genericTypeClass(classOf[Option[_]], List(t)))
      val inputType   = Typed.record(optionTypes)

      val storedType     = aggregator.computeStoredTypeUnsafe(inputType)
      val storedTypeInfo = TypeInformationDetection.instance.forType[AnyRef](storedType)
      val aggregatorFunction = prepareAggregatorFunction(
        aggregator,
        FiniteDuration(window.toMillis, TimeUnit.MILLISECONDS),
        inputType,
        storedTypeInfo,
        context.convertToEngineRuntimeContext
      )(NodeId(context.nodeId))
      val outputType = aggregator.computeOutputTypeUnsafe(inputType)
      val outputTypeInfo =
        TypeInformationDetection.instance.forValueWithContext[AnyRef](ValidationContext(), outputType)

      val stream = keyedStreams
        .reduce(_.connectAndMerge(_))
        .keyBy((v: ValueWithContext[StringKeyedValue[AnyRef]]) => v.value.key)
        .process(aggregatorFunction, outputTypeInfo)
        .setUidWithName(context, ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)

      timestampAssigner
        .map(new TimestampAssignmentHelper(_)(outputTypeInfo).assignWatermarks(stream))
        .getOrElse(stream)
    }
  }

  protected def prepareAggregatorFunction(
      aggregator: Aggregator,
      stateTimeout: FiniteDuration,
      aggregateElementType: TypingResult,
      storedTypeInfo: TypeInformation[AnyRef],
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
  )(
      implicit nodeId: NodeId
  ): KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]] =
    new FullOuterJoinAggregatorFunction[SortedMap](
      aggregator,
      stateTimeout.toMillis,
      nodeId,
      aggregateElementType,
      storedTypeInfo,
      convertToEngineRuntimeContext,
      KeyFieldName
    )

  override def typesToExtract: List[typing.TypedClass] = List(
    Typed.typedClass[BranchType],
    Typed.typedClass[AggregateHelper]
  )

}

object FullOuterJoinTransformer extends FullOuterJoinTransformer(None) with UnboundedStreamComponent {
  val KeyFieldName = "key"

  val KeyParamName: ParameterName = ParameterName("key")

  val KeyParamDeclaration
      : ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, LazyParameter[CharSequence]]] =
    ParameterDeclaration.branchLazyMandatory[CharSequence](KeyParamName).withCreator()

  val AggregatorParamName: ParameterName = ParameterName("aggregator")

  val AggregatorParamDeclaration: ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, Aggregator]] =
    ParameterDeclaration
      .branchMandatory[Aggregator](AggregatorParamName)
      .withCreator(
        modify = _.copy(
          editor = Some(AggregateHelper.DUAL_EDITOR),
          additionalVariables = Map("AGG" -> AdditionalVariableWithFixedValue(new AggregateHelper))
        )
      )

  val AggregateByParamName: ParameterName = ParameterName("aggregateBy")

  val AggregateByParamDeclaration
      : ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, LazyParameter[AnyRef]]] =
    ParameterDeclaration.branchLazyMandatory[AnyRef](AggregateByParamName).withCreator()

  val WindowLengthParamName: ParameterName = ParameterName("windowLength")
  val WindowLengthParamDeclaration: ParameterCreatorWithNoDependency with ParameterExtractor[Duration] =
    ParameterDeclaration.mandatory[Duration](WindowLengthParamName).withCreator()

}
