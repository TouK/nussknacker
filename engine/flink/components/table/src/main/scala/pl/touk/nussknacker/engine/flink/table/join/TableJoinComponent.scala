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
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerBranchParameter,
  DefinedSingleParameter,
  JoinDynamicComponent,
  NodeDependencyValue
}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.api.process.{
  AbstractLazyParameterInterpreterFunction,
  FlinkCustomJoinTransformation,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper
}
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions.{TypeInformationDetectionExtension, rowToContext}
import pl.touk.nussknacker.engine.flink.util.transformer.join.BranchType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object TableJoinComponent extends CustomStreamTransformer with JoinDynamicComponent[FlinkCustomJoinTransformation] {

  private val contextInternalColumnName   = "context"
  private val mainKeyInternalColumnName   = "mainKey"
  private val joinedKeyInternalColumnName = "joinedKey"
  private val outputInternalColumnName    = "output"

  val BranchTypeParamName: ParameterName = ParameterName("branchType")

  private val BranchTypeParamDeclaration
      : ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, BranchType]] =
    ParameterDeclaration.branchMandatory[BranchType](BranchTypeParamName).withCreator()

  val KeyParamName: ParameterName = ParameterName("key")

  private val KeyParamDeclaration
      : ParameterCreatorWithNoDependency with ParameterExtractor[Map[String, LazyParameter[String]]] =
    ParameterDeclaration.branchLazyMandatory[String](KeyParamName).withCreator()

  val OutputParamName: ParameterName = ParameterName("output")

  override type State = Nothing

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(contexts: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        List(BranchTypeParamDeclaration, KeyParamDeclaration)
          .map(_.createParameter())
      )
    case TransformationStep(
          (
            `BranchTypeParamName`,
            DefinedEagerBranchParameter(branchTypeByBranchId: Map[String, BranchType] @unchecked, _)
          ) :: (`KeyParamName`, _) :: Nil,
          _
        ) =>
      val error =
        if (branchTypeByBranchId.values.toList.sorted != BranchType.values().toList)
          List(
            CustomNodeError(
              s"Has to be exactly one MAIN and JOINED branch, got: ${branchTypeByBranchId.values.mkString(", ")}",
              Some(BranchTypeParamName)
            )
          )
        else
          Nil
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
        error
      )

    case TransformationStep(
          (
            `BranchTypeParamName`,
            DefinedEagerBranchParameter(branchTypeByBranchId: Map[String, BranchType] @unchecked, _)
          ) ::
          (`KeyParamName`, _) :: (`OutputParamName`, outputParameter: DefinedSingleParameter) :: Nil,
          _
        ) =>
      val outName     = OutputVariableNameDependency.extract(dependencies)
      val mainContext = extractMainBranchId(branchTypeByBranchId).map(contexts).getOrElse(ValidationContext())
      FinalResults.forValidation(mainContext)(
        _.withVariable(OutputVar.customNode(outName), outputParameter.returnType)
      )

  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Nothing]
  ): FlinkCustomJoinTransformation = new FlinkCustomJoinTransformation {

    override def transform(
        inputs: Map[String, DataStream[Context]],
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[ValueWithContext[AnyRef]] = {
      val branchTypeByBranchId: Map[String, BranchType] = BranchTypeParamDeclaration.extractValueUnsafe(params)
      val mainBranchId                                  = extractMainBranchId(branchTypeByBranchId).get
      val joinedBranchId                                = extractJoinedBranchId(branchTypeByBranchId).get
      val mainStream                                    = inputs(mainBranchId)
      val joinedStream                                  = inputs(joinedBranchId)

      val env      = mainStream.getExecutionEnvironment
      val tableEnv = StreamTableEnvironment.create(env)

      val mainTable = tableEnv.fromDataStream(
        mainStream.flatMap(
          new MainBranchToRowFunction(
            KeyParamDeclaration.extractValueUnsafe(params)(mainBranchId),
            flinkNodeContext.lazyParameterHelper
          ),
          mainBranchTypeInfo(flinkNodeContext, mainBranchId)
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
          joinedBranchTypeInfo(flinkNodeContext, outputLazyParam)
        )
      )

      val resultTable =
        mainTable.join(joinedTable, $(joinedKeyInternalColumnName).isEqual($(mainKeyInternalColumnName)))

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
      mainKeyLazyParam: LazyParameter[String],
      lazyParameterHelper: FlinkLazyParameterFunctionHelper
  ) extends AbstractLazyParameterInterpreterFunction(lazyParameterHelper)
      with FlatMapFunction[Context, Row] {

    private lazy val evaluateKey = toEvaluateFunctionConverter.toEvaluateFunction(mainKeyLazyParam)

    override def flatMap(context: Context, out: Collector[Row]): Unit = {
      collectHandlingErrors(context, out) {
        val row = Row.withNames()
        row.setField(contextInternalColumnName, RowConversions.contextToRow(context))
        row.setField(mainKeyInternalColumnName, evaluateKey(context))
        row
      }
    }

  }

  private def mainBranchTypeInfo(flinkNodeContext: FlinkCustomNodeContext, mainBranchId: String) = {
    Types.ROW_NAMED(
      Array(contextInternalColumnName, mainKeyInternalColumnName),
      flinkNodeContext.typeInformationDetection.contextRowTypeInfo(
        flinkNodeContext.branchValidationContext(mainBranchId)
      ),
      Types.STRING
    )
  }

  private class JoinedBranchToRowFunction(
      joinedKeyLazyParam: LazyParameter[String],
      outputLazyParam: LazyParameter[AnyRef],
      lazyParameterHelper: FlinkLazyParameterFunctionHelper
  ) extends AbstractLazyParameterInterpreterFunction(lazyParameterHelper)
      with FlatMapFunction[Context, Row] {

    private lazy val evaluateKey = toEvaluateFunctionConverter.toEvaluateFunction(joinedKeyLazyParam)

    private lazy val evaluateOutput = toEvaluateFunctionConverter.toEvaluateFunction(outputLazyParam)

    override def flatMap(context: Context, out: Collector[Row]): Unit = {
      collectHandlingErrors(context, out) {
        val row = Row.withNames()
        row.setField(joinedKeyInternalColumnName, evaluateKey(context))
        row.setField(outputInternalColumnName, evaluateOutput(context))
        row
      }
    }

  }

  private def joinedBranchTypeInfo(flinkNodeContext: FlinkCustomNodeContext, outputLazyParam: LazyParameter[_]) = {
    Types.ROW_NAMED(
      Array(joinedKeyInternalColumnName, outputInternalColumnName),
      Types.STRING,
      flinkNodeContext.typeInformationDetection.forType(outputLazyParam.returnType)
    )
  }

  private def extractMainBranchId(branchTypeByBranchId: Map[String, BranchType]) = {
    branchTypeByBranchId.find(_._2 == BranchType.MAIN).map(_._1)
  }

  private def extractJoinedBranchId(branchTypeByBranchId: Map[String, BranchType]) = {
    branchTypeByBranchId.find(_._2 == BranchType.JOINED).map(_._1)
  }

}
