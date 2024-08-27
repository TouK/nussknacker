package pl.touk.nussknacker.engine.flink.table.aggregate

import enumeratum._
import org.apache.flink.table.catalog.DataTypeFactory
import org.apache.flink.table.functions.{BuiltInFunctionDefinition, BuiltInFunctionDefinitions, FunctionDefinition}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.inference.CallContext
import org.apache.flink.table.types.logical.{LogicalTypeFamily, LogicalTypeRoot, RawType}
import org.apache.flink.table.types.utils.TypeInfoDataTypeConverter
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory.aggregateByParamName
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregator.{
  NuAggregationFunctionCallContext,
  buildError,
  dataTypeFactory,
  validateFirstLastInputType
}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypeFactoryPreparer
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesConversions.LogicalTypeConverter
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

import java.util
import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

/*
   TODO: add remaining aggregations functions:
     - LISTAGG
     - ARRAY_AGG - after adding support for array
     - SUM0 - probably through specific parameter in standard SUM
     - COLLECT - after adding support for multiset

   TODO: unify aggregator function definitions with unbounded-streaming ones. Current duplication may lead to
     inconsistency in naming and may be confusing for users

   TODO: add distinct parameter - but not for First and Last aggregators
 */
object TableAggregator extends Enum[TableAggregator] {
  val values = findValues

  case object Average extends TableAggregator {
    override val displayName: String                                = "Average"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.AVG
  }

  case object Count extends TableAggregator {
    override val displayName: String                                = "Count"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.COUNT
  }

  case object Max extends TableAggregator {
    override val displayName: String                                = "Max"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.MAX
  }

  case object Min extends TableAggregator {
    override val displayName: String                                = "Min"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.MIN
  }

  // As of Flink 1.19, time-related types are not supported in FIRST_VALUE aggregate function.
  // See: https://issues.apache.org/jira/browse/FLINK-15867
  // See AggFunctionFactory.createFirstValueAggFunction
  // TODO: add validation for the above case
  case object First extends TableAggregator {
    override val displayName: String                                = "First"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.FIRST_VALUE

    override def inferOutputType(
        inputType: TypingResult
    )(implicit nodeId: NodeId): Either[ProcessCompilationError, TypingResult] = {
      for {
        _          <- validateFirstLastInputType(inputType)
        outputType <- super.inferOutputType(inputType)
      } yield outputType
    }

  }

  case object Last extends TableAggregator {
    override val displayName: String                                = "Last"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.LAST_VALUE

    override def inferOutputType(
        inputType: TypingResult
    )(implicit nodeId: NodeId): Either[ProcessCompilationError, TypingResult] = {
      validateFirstLastInputType(inputType).flatMap(_ => super.inferOutputType(inputType))
    }

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
      val parameterDataType = toDataType(parameterType)
      new NuAggregationFunctionCallContext(dataTypeFactory, function, List(parameterDataType))
    }

  }

  private def toDataType(parameterType: TypingResult) = {
    val typeInfo = typeInfoDetection.forType(parameterType)
    TypeInfoDataTypeConverter.toDataType(dataTypeFactory, typeInfo)
  }

  // TODO: specific type errors per aggregator that communicate the type constraint. Until we have consistent base
  //  validation of types in Table-API components it's going to be misleading.
  private def buildError(inputType: TypingResult)(implicit nodeId: NodeId) = CustomNodeError(
    s"Invalid type: ${inputType.withoutValue.display} for selected aggregator",
    Some(aggregateByParamName)
  )

  def validateFirstLastInputType(inputType: TypingResult)(implicit nodeId: NodeId) = {
    val parameterLogicalType = toDataType(inputType).getLogicalType
    val isTimeRelatedType    = parameterLogicalType.isAnyOf(LogicalTypeFamily.TIME, LogicalTypeFamily.DATETIME)
    if (isTimeRelatedType) {
      Left(buildError(inputType))
    } else {
      Right(())
    }
  }

}

// TODO: remodel aggregators to TableAggregatorType and instantiate Aggregator that will contain information like
//  output type, Table-API expression
sealed trait TableAggregator extends EnumEntry {

  val displayName: String

  def flinkFunctionDefinition: BuiltInFunctionDefinition

  val flinkFunctionName: String = flinkFunctionDefinition.getName

  // TODO: handle functions that take no args or more than 1 arg - will require to do context transformation and expect
  //  types defined by the selected aggregator
  def inferOutputType(inputType: TypingResult)(
      implicit nodeId: NodeId
  ): Either[ProcessCompilationError, TypingResult] = {
    def validateInputType(callContext: NuAggregationFunctionCallContext) = {
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
    ) = {
      flinkFunctionDefinition
        .getTypeInference(dataTypeFactory)
        .getOutputTypeStrategy
        .inferType(callContext)
        .toScala
        .map(value =>
          value.getLogicalType match {
            case _: RawType[_] => Left(buildError(inputType))
            case other         => Right(other.toTypingResult)
          }
        )
        .getOrElse(Left(buildError(inputType)))
    }
    val callContext = NuAggregationFunctionCallContext(inputType, flinkFunctionDefinition)
    validateInputType(callContext).flatMap(_ => inferOutputType(callContext))
  }

}
