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
  NuAggregationFunctionCallContext,
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
// TODO: add remaining aggregations: COUNT, LISTAGG, LAG, LEAD, JSON aggs
object TableAggregator extends Enum[TableAggregator] {
  val values = findValues

  case object Average extends TableAggregator {
    override val displayName: String                                = "Average"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.AVG
  }

  case object Max extends TableAggregator {
    override val displayName: String                                = "Max"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.MAX
  }

  case object Min extends TableAggregator {
    override val displayName: String                                = "Min"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.MIN
  }

  case object First extends TableAggregator {
    override val displayName: String                                = "First"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.FIRST_VALUE
  }

  case object Last extends TableAggregator {
    override val displayName: String                                = "Last"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.LAST_VALUE
  }

  case object Sum extends TableAggregator {
    override val displayName: String                                = "Sum"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.SUM
  }

  case object PopulationStandardDeviation extends TableAggregator {
    override val displayName: String                                = "Population standard deviation"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.STDDEV_POP
  }

  case object SampleStandardDeviation extends TableAggregator {
    override val displayName: String                                = "Sample standard deviation"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.STDDEV_SAMP
  }

  case object PopulationVariance extends TableAggregator {
    override val displayName: String                                = "Population variance"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.VAR_POP
  }

  case object SampleVariance extends TableAggregator {
    override val displayName: String                                = "Sample variance"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.VAR_SAMP
  }

  private val dataTypeFactory   = DataTypeFactoryPreparer.prepare()
  private val typeInfoDetection = TypingResultAwareTypeInformationDetection.apply(getClass.getClassLoader)

  private class NuAggregationFunctionCallContext(
      val dataTypeFactory: DataTypeFactory,
      val functionDefinition: FunctionDefinition,
      val arguments: List[DataType]
  ) extends CallContext {
    override def getDataTypeFactory: DataTypeFactory                         = dataTypeFactory
    override def getFunctionDefinition: FunctionDefinition                   = functionDefinition
    override def isArgumentLiteral(pos: Int): Boolean                        = false
    override def isArgumentNull(pos: Int): Boolean                           = false
    override def getArgumentValue[T](pos: Int, clazz: Class[T]): Optional[T] = None.toJava
    override def getName: String                                             = "NuAggregationFunction"
    override def getArgumentDataTypes: util.List[DataType]                   = arguments.asJava
    override def getOutputDataType: Optional[DataType]                       = None.toJava
    override def isGroupedAggregation: Boolean                               = true
  }

  private object NuAggregationFunctionCallContext {

    def apply(
        parameterType: TypingResult,
        function: BuiltInFunctionDefinition
    ): NuAggregationFunctionCallContext = {
      val typeInfo = typeInfoDetection.forType(parameterType)
      val dataType = TypeInfoDataTypeConverter.toDataType(dataTypeFactory, typeInfo)
      new NuAggregationFunctionCallContext(dataTypeFactory, function, List(dataType))
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
  // TODO: handle functions that take no args or more than 1 arg
  def inferOutputType(inputType: TypingResult)(
      implicit nodeId: NodeId
  ): Either[ProcessCompilationError, TypingResult] = {
    def validateInputType(callContext: NuAggregationFunctionCallContext): Either[ProcessCompilationError, Unit] = {
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
        callContext: NuAggregationFunctionCallContext
    ): Either[ProcessCompilationError, TypingResult] = {
      flinkFunctionDefinition
        .getTypeInference(dataTypeFactory)
        .getOutputTypeStrategy
        .inferType(callContext)
        .toScala
        .map(value => Right(value.getLogicalType.toTypingResult))
        .getOrElse(Left(buildError(inputType)))
    }
    val callContext = NuAggregationFunctionCallContext(inputType, flinkFunctionDefinition)
    validateInputType(callContext).flatMap(_ => inferOutputType(callContext))
  }

}
