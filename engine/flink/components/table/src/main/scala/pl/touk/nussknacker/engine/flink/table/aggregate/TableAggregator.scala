package pl.touk.nussknacker.engine.flink.table.aggregate

import enumeratum._
import org.apache.flink.table.catalog.DataTypeFactory
import org.apache.flink.table.functions.{BuiltInFunctionDefinition, BuiltInFunctionDefinitions, FunctionDefinition}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.inference.CallContext
import org.apache.flink.table.types.utils.TypeInfoDataTypeConverter
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory.aggregateByParamName
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregator.{
  NuSingleParameterFunctionCallContext,
  buildError,
  dataTypeFactory
}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypeFactoryPreparer
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesConversions.LogicalTypeConverter
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

import java.util
import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

// TODO: unify aggregator function definitions with unbounded-streaming ones. Current duplication may lead to
//  inconsistency in naming and may be confusing for users
// TODO: add remaining aggregators
object TableAggregator extends Enum[TableAggregator] {
  val values = findValues

  case object Sum extends TableAggregator {
    override val displayName: String = "Sum"
//    TODO: without lazy it doesnt work because of order of initialization? why
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.SUM
  }

  case object First extends TableAggregator {
    override val displayName: String                                = "First"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.FIRST_VALUE
  }

  private val dataTypeFactory   = DataTypeFactoryPreparer.prepare()
  private val typeInfoDetection = TypingResultAwareTypeInformationDetection.apply(getClass.getClassLoader)

  private class NuSingleParameterFunctionCallContext(
      val dataTypeFactory: DataTypeFactory,
      val functionDefinition: FunctionDefinition,
      val argument: DataType
  ) extends CallContext {
    override def getDataTypeFactory: DataTypeFactory                         = dataTypeFactory
    override def getFunctionDefinition: FunctionDefinition                   = functionDefinition
    override def isArgumentLiteral(pos: Int): Boolean                        = false
    override def isArgumentNull(pos: Int): Boolean                           = false
    override def getArgumentValue[T](pos: Int, clazz: Class[T]): Optional[T] = None.toJava
    override def getName: String                                             = "???"
    override def getArgumentDataTypes: util.List[DataType]                   = List(argument).asJava
    override def getOutputDataType: Optional[DataType]                       = Some(argument).toJava
    override def isGroupedAggregation: Boolean                               = true
  }

  private object NuSingleParameterFunctionCallContext {

    def apply(
        parameterType: TypingResult,
        function: BuiltInFunctionDefinition
    ): NuSingleParameterFunctionCallContext = {
      val typeInfo = typeInfoDetection.forType(parameterType)
      val dataType = TypeInfoDataTypeConverter.toDataType(dataTypeFactory, typeInfo)
      new NuSingleParameterFunctionCallContext(dataTypeFactory, function, dataType)
    }

  }

  // TODO: better error messages
  private def buildError(inputType: TypingResult)(implicit nodeId: NodeId) = {
    CustomNodeError(
      s"Invalid type: ${inputType.withoutValue.display}",
      Some(aggregateByParamName)
    )
  }

}

// TODO: extract call expression to TableAggregator
// TODO: add distinct
sealed trait TableAggregator extends EnumEntry {

  val displayName: String

  def flinkFunctionDefinition: BuiltInFunctionDefinition

  val flinkFunctionName: String = flinkFunctionDefinition.getName

  // TODO: separate functions for validating input type and infering output type?
  def inferOutputType(inputType: TypingResult)(
      implicit nodeId: NodeId
  ): Either[ProcessCompilationError, TypingResult] = {
    def validateInputType(callContext: NuSingleParameterFunctionCallContext): Either[ProcessCompilationError, Unit] = {
      flinkFunctionDefinition
        .getTypeInference(dataTypeFactory)
        .getInputTypeStrategy
        .inferInputTypes(callContext, false)
        .toScala
        .flatMap(_.asScala.headOption) match {
        case Some(_) => Right(())
        case None    => Left(buildError(inputType))
      }
    }
    def inferOutputType(
        callContext: NuSingleParameterFunctionCallContext
    ): Either[ProcessCompilationError, TypingResult] = {
      flinkFunctionDefinition
        .getTypeInference(dataTypeFactory)
        .getOutputTypeStrategy
        .inferType(callContext)
        .toScala
        .map(value => Right(value.getLogicalType.toTypingResult))
        .getOrElse(Left(buildError(inputType)))
    }
    val callContext = NuSingleParameterFunctionCallContext(inputType, flinkFunctionDefinition)
    validateInputType(callContext).flatMap(_ => inferOutputType(callContext))
  }

}
