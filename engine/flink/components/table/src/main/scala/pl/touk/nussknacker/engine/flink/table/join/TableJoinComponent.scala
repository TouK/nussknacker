package pl.touk.nussknacker.engine.flink.table.join

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}
import pl.touk.nussknacker.engine.flink.api.process.{
  AbstractLazyParameterInterpreterFunction,
  FlinkCustomJoinTransformation,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.table.utils.{RowConversions, ToTableTypeEncoder}
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions.{TypeInformationDetectionExtension, rowToContext}
import pl.touk.nussknacker.engine.flink.util.transformer.join.BranchType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object TableJoinComponent
    extends CustomStreamTransformer
    with JoinDynamicComponent[FlinkCustomJoinTransformation]
    with WithExplicitTypesToExtract {

  private val contextInternalColumnName   = "context"
  private val mainKeyInternalColumnName   = "mainKey"
  private val joinedKeyInternalColumnName = "joinedKey"
  private val outputInternalColumnName    = "output"

  val BranchTypeParamName: ParameterName = ParameterName("Branch Type")

  private val BranchTypeParamDeclaration
      : ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, BranchType]] =
    ParameterDeclaration.branchMandatory[BranchType](BranchTypeParamName).withCreator()

  val JoinTypeParamName: ParameterName = ParameterName("Join Type")

  private val JoinTypeParamDeclaration: ParameterCreatorWithNoDependency with ParameterExtractor[JoinType] =
    ParameterDeclaration.mandatory[JoinType](JoinTypeParamName).withCreator()

  val KeyParamName: ParameterName = ParameterName("Key")

  private val KeyParamDeclaration
      : ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, LazyParameter[AnyRef]]] =
    ParameterDeclaration.branchLazyMandatory[AnyRef](KeyParamName).withCreator()

  val OutputParamName: ParameterName = ParameterName("Output")

  override type State = JoinType

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(contexts: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        List(BranchTypeParamDeclaration, KeyParamDeclaration, JoinTypeParamDeclaration)
          .map(_.createParameter())
      )
    case TransformationStep(
          (
            `BranchTypeParamName`,
            DefinedEagerBranchParameter(branchTypeByBranchId: Map[String, BranchType] @unchecked, _)
          ) ::
          (`KeyParamName`, DefinedLazyBranchParameter(keyTypeByBranchId)) ::
          (`JoinTypeParamName`, _) ::
          Nil,
          _
        ) =>
      val illegalBranchTypesErrorOpt = {
        if (branchTypeByBranchId.values.toList.sorted != BranchType.values().toList)
          Some(
            CustomNodeError(
              s"Has to be exactly one MAIN and JOINED branch, got: ${branchTypeByBranchId.values.mkString(", ")}",
              Some(BranchTypeParamName)
            )
          )
        else
          None
      }
      val mismatchKeyTypesErrorOpt = Option(keyTypeByBranchId.values.toList.sortBy(_.display)).collect {
        case left :: right :: Nil if CommonSupertypeFinder.Intersection.commonSupertypeOpt(left, right).isEmpty =>
          CustomNodeError(
            s"Types ${left.display} and ${right.display} are not comparable",
            Some(KeyParamName)
          )
      }
      val joinedVariables = extractJoinedBranchId(branchTypeByBranchId)
        .map(contexts)
        .getOrElse(ValidationContext())
        .localVariables
        .mapValuesNow(AdditionalVariableProvidedInRuntime(_))
      NextParameters(
        List(
          ParameterDeclaration
            .lazyMandatory[AnyRef](OutputParamName)
            .withCreator(_.copy(additionalVariables = joinedVariables))
            .createParameter()
        ),
        illegalBranchTypesErrorOpt.toList ::: mismatchKeyTypesErrorOpt.toList ::: Nil
      )
    case TransformationStep(
          (
            `BranchTypeParamName`,
            DefinedEagerBranchParameter(branchTypeByBranchId: Map[String, BranchType] @unchecked, _)
          ) ::
          (`KeyParamName`, _) ::
          (`JoinTypeParamName`, DefinedEagerParameter(joinType: JoinType, _)) ::
          (`OutputParamName`, outputParameter: DefinedSingleParameter) ::
          Nil,
          _
        ) =>
      val outName     = OutputVariableNameDependency.extract(dependencies)
      val mainContext = extractMainBranchId(branchTypeByBranchId).map(contexts).getOrElse(ValidationContext())
      FinalResults
        .forValidation(mainContext)(
          _.withVariable(OutputVar.customNode(outName), outputParameter.returnType)
        )
        .copy(state = Some(joinType))
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      joinTypeState: Option[JoinType]
  ): FlinkCustomJoinTransformation = new FlinkCustomJoinTransformation {

    override def transform(
        inputs: Map[String, DataStream[Context]],
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[ValueWithContext[AnyRef]] = {
      val branchTypeByBranchId: Map[String, BranchType] = BranchTypeParamDeclaration.extractValueUnsafe(params)
      val mainBranchId =
        extractMainBranchId(branchTypeByBranchId).getOrElse(throw new IllegalStateException("Not defined main branch"))
      val joinedBranchId = extractJoinedBranchId(branchTypeByBranchId).getOrElse(
        throw new IllegalStateException("Not defined joined branch")
      )
      val mainStream   = inputs(mainBranchId)
      val joinedStream = inputs(joinedBranchId)
      val joinType     = joinTypeState.getOrElse(throw new IllegalStateException("Not defined join type"))

      val env      = mainStream.getExecutionEnvironment
      val tableEnv = StreamTableEnvironment.create(env)

      val mainTable = tableEnv.fromDataStream(
        mainStream.flatMap(
          new MainBranchToRowFunction(
            KeyParamDeclaration.extractValueUnsafe(params)(mainBranchId),
            flinkNodeContext.branchValidationContext(mainBranchId),
            flinkNodeContext.lazyParameterHelper
          ),
          mainBranchTypeInfo(
            flinkNodeContext,
            flinkNodeContext.branchValidationContext(mainBranchId),
            KeyParamDeclaration.extractValueUnsafe(params)(mainBranchId)
          )
        )
      )

      val outputLazyParam = params.extractUnsafe[LazyParameter[AnyRef]](OutputParamName)
      val outputTypeInfo =
        flinkNodeContext.valueWithContextInfo.forBranch[AnyRef](mainBranchId, outputLazyParam.returnType)

      val joinedTable = tableEnv.fromDataStream(
        joinedStream.flatMap(
          new JoinedBranchToRowFunction(
            KeyParamDeclaration.extractValueUnsafe(params)(joinedBranchId),
            outputLazyParam,
            flinkNodeContext.lazyParameterHelper
          ),
          joinedBranchTypeInfo(
            flinkNodeContext,
            outputLazyParam,
            KeyParamDeclaration.extractValueUnsafe(params)(joinedBranchId)
          )
        )
      )

      val joinPredicate = $(joinedKeyInternalColumnName).isEqual($(mainKeyInternalColumnName))
      val resultTable = joinType match {
        case JoinType.INNER => mainTable.join(joinedTable, joinPredicate)
        case JoinType.OUTER => mainTable.leftOuterJoin(joinedTable, joinPredicate)
      }

      tableEnv
        .toDataStream(resultTable)
        .map(
          (row: Row) =>
            ValueWithContext[AnyRef](
              row.getField(outputInternalColumnName),
              rowToContext(row.getField(contextInternalColumnName).asInstanceOf[Row])
            ),
          outputTypeInfo
        )
    }

  }

  private class MainBranchToRowFunction(
      mainKeyLazyParam: LazyParameter[AnyRef],
      mainBranchValidationContext: ValidationContext,
      lazyParameterHelper: FlinkLazyParameterFunctionHelper,
  ) extends AbstractLazyParameterInterpreterFunction(lazyParameterHelper)
      with FlatMapFunction[Context, Row] {

    private lazy val evaluateKey = toEvaluateFunctionConverter.toEvaluateFunction(mainKeyLazyParam)

    override def flatMap(context: Context, out: Collector[Row]): Unit = {
      collectHandlingErrors(context, out) {
        val row = Row.withNames()
        row.setField(contextInternalColumnName, RowConversions.contextToRow(context, mainBranchValidationContext))
        row.setField(
          mainKeyInternalColumnName,
          ToTableTypeEncoder.encode(evaluateKey(context), mainKeyLazyParam.returnType)
        )
        row
      }
    }

  }

  private def mainBranchTypeInfo(
      flinkNodeContext: FlinkCustomNodeContext,
      mainBranchValidationContext: ValidationContext,
      mainKeyLazyParam: LazyParameter[_]
  ) = {
    Types.ROW_NAMED(
      Array(contextInternalColumnName, mainKeyInternalColumnName),
      TypeInformationDetection.instance.contextRowTypeInfo(mainBranchValidationContext),
      TypeInformationDetection.instance.forType(
        ToTableTypeEncoder.alignTypingResult(mainKeyLazyParam.returnType)
      )
    )
  }

  private class JoinedBranchToRowFunction(
      joinedKeyLazyParam: LazyParameter[AnyRef],
      outputLazyParam: LazyParameter[AnyRef],
      lazyParameterHelper: FlinkLazyParameterFunctionHelper
  ) extends AbstractLazyParameterInterpreterFunction(lazyParameterHelper)
      with FlatMapFunction[Context, Row] {

    private lazy val evaluateKey = toEvaluateFunctionConverter.toEvaluateFunction(joinedKeyLazyParam)

    private lazy val evaluateOutput = toEvaluateFunctionConverter.toEvaluateFunction(outputLazyParam)

    override def flatMap(context: Context, out: Collector[Row]): Unit = {
      collectHandlingErrors(context, out) {
        val row = Row.withNames()
        row.setField(
          joinedKeyInternalColumnName,
          ToTableTypeEncoder.encode(evaluateKey(context), joinedKeyLazyParam.returnType)
        )
        row.setField(
          outputInternalColumnName,
          ToTableTypeEncoder.encode(evaluateOutput(context), outputLazyParam.returnType)
        )
        row
      }
    }

  }

  private def joinedBranchTypeInfo(
      flinkNodeContext: FlinkCustomNodeContext,
      outputLazyParam: LazyParameter[_],
      joinedKeyLazyParam: LazyParameter[_]
  ) = {
    Types.ROW_NAMED(
      Array(joinedKeyInternalColumnName, outputInternalColumnName),
      TypeInformationDetection.instance.forType(
        ToTableTypeEncoder.alignTypingResult(joinedKeyLazyParam.returnType)
      ),
      TypeInformationDetection.instance.forType(
        ToTableTypeEncoder.alignTypingResult(outputLazyParam.returnType)
      )
    )
  }

  private def extractMainBranchId(branchTypeByBranchId: Map[String, BranchType]) = {
    branchTypeByBranchId.collectFirst { case (branchId, BranchType.MAIN) =>
      branchId
    }
  }

  private def extractJoinedBranchId(branchTypeByBranchId: Map[String, BranchType]) = {
    branchTypeByBranchId.collectFirst { case (branchId, BranchType.JOINED) =>
      branchId
    }
  }

  override def typesToExtract: List[TypedClass] = List(
    Typed.typedClass[BranchType],
    Typed.typedClass[JoinType],
  )

}
