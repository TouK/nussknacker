package pl.touk.nussknacker.engine.flink.table.aggregate

import enumeratum._
import org.apache.flink.table.catalog.DataTypeFactory
import org.apache.flink.table.functions.{BuiltInFunctionDefinition, BuiltInFunctionDefinitions, FunctionDefinition}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.inference.CallContext
import org.apache.flink.table.types.logical.LogicalTypeRoot
import org.apache.flink.table.types.logical.LogicalTypeRoot._
import org.apache.flink.table.types.utils.TypeInfoDataTypeConverter
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory.aggregateByParamName
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregatorType.{
  NuAggregationFunctionCallContext,
  dataTypeFactory,
  toDataType
}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypeFactoryPreparer
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions.LogicalTypeExtension

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
object TableAggregatorType extends Enum[TableAggregatorType] {
  val values = findValues

  case object Average extends TableAggregatorType {
    override val displayName: String                                        = "Average"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.AVG
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(numericAggregationsAllowedTypes)
  }

  case object Count extends TableAggregatorType {
    override val displayName: String                                        = "Count"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.COUNT
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = None
  }

  case object Max extends TableAggregatorType {
    override val displayName: String                                        = "Max"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.MAX
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(minMaxAllowedTypes)
  }

  case object Min extends TableAggregatorType {
    override val displayName: String                                        = "Min"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.MIN
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(minMaxAllowedTypes)
  }

  // As of Flink 1.19, time-related types are not supported in FIRST_VALUE aggregate function.
  // See: https://issues.apache.org/jira/browse/FLINK-15867
  // See AggFunctionFactory.createFirstValueAggFunction
  case object First extends TableAggregatorType {
    override val displayName: String                                        = "First"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.FIRST_VALUE
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(firstLastAllowedTypes)
  }

  case object Last extends TableAggregatorType {
    override val displayName: String                                        = "Last"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.LAST_VALUE
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(firstLastAllowedTypes)
  }

  case object Sum extends TableAggregatorType {
    override val displayName: String                                        = "Sum"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.SUM
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(numericAggregationsAllowedTypes)
  }

  case object PopulationStandardDeviation extends TableAggregatorType {
    override val displayName: String                                        = "Population standard deviation"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.STDDEV_POP
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(numericAggregationsAllowedTypes)
  }

  case object SampleStandardDeviation extends TableAggregatorType {
    override val displayName: String                                        = "Sample standard deviation"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.STDDEV_SAMP
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(numericAggregationsAllowedTypes)
  }

  case object PopulationVariance extends TableAggregatorType {
    override val displayName: String                                        = "Population variance"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.VAR_POP
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(numericAggregationsAllowedTypes)
  }

  case object SampleVariance extends TableAggregatorType {
    override val displayName: String                                        = "Sample variance"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition         = BuiltInFunctionDefinitions.VAR_SAMP
    override def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(numericAggregationsAllowedTypes)
  }

  private val dataTypeFactory = DataTypeFactoryPreparer.prepare()

  private val typeInfoDetection = TypeInformationDetection.instance

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

  private val minMaxAllowedTypes = List(
    TINYINT,
    SMALLINT,
    INTEGER,
    BIGINT,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    VARCHAR,
    DECIMAL,
    TIME_WITHOUT_TIME_ZONE,
    DATE,
    TIMESTAMP_WITHOUT_TIME_ZONE,
    TIMESTAMP_WITH_LOCAL_TIME_ZONE
  )

  private val firstLastAllowedTypes = List(
    TINYINT,
    SMALLINT,
    INTEGER,
    BIGINT,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    VARCHAR,
    DECIMAL
  )

  private val numericAggregationsAllowedTypes = List(
    TINYINT,
    SMALLINT,
    INTEGER,
    BIGINT,
    FLOAT,
    DOUBLE,
    DECIMAL
  )

}

// TODO: remodel aggregators to TableAggregatorType and instantiate Aggregator that will contain information like
//  output type, Table-API expression
sealed trait TableAggregatorType extends EnumEntry {

  val displayName: String

  def flinkFunctionDefinition: BuiltInFunctionDefinition

  val flinkFunctionName: String = flinkFunctionDefinition.getName

  def inputAllowedTypesConstraint: Option[List[LogicalTypeRoot]]

  // TODO: handle functions that take no args or more than 1 arg - will require to do context transformation and expect
  //  types defined by the selected aggregator
  def inferOutputType(inputType: TypingResult)(
      implicit nodeId: NodeId
  ): Either[ProcessCompilationError, TypingResult] = {
    def buildError()(implicit nodeId: NodeId) = CustomNodeError(
      s"Invalid type: '${inputType.withoutValue.display}' for selected aggregator: '$displayName'.",
      Some(aggregateByParamName)
    )
    def validateUsingInputTypeStrategy(callContext: NuAggregationFunctionCallContext) = {
      flinkFunctionDefinition
        .getTypeInference(dataTypeFactory)
        .getInputTypeStrategy
        .inferInputTypes(callContext, false)
        .toScala
        .flatMap(_.asScala.headOption) match {
        case Some(_) => Right(())
        case None    => Left(buildError())
      }
    }
    def validateUsingAllowedTypesConstraint() = {
      this.inputAllowedTypesConstraint match {
        case Some(allowedTypes) =>
          if (toDataType(inputType).getLogicalType.isAnyOf(allowedTypes: _*)) {
            Right(())
          } else {
            Left(buildError())
          }
        case None => Right(())
      }
    }
    def inferOutputType(
        callContext: NuAggregationFunctionCallContext
    ) = {
      flinkFunctionDefinition
        .getTypeInference(dataTypeFactory)
        .getOutputTypeStrategy
        .inferType(callContext)
        .toScala match {
        case Some(value) => Right(value.getLogicalType.toTypingResult)
        case None        => Left(buildError())
      }
    }
    val callContext = NuAggregationFunctionCallContext(inputType, flinkFunctionDefinition)
    for {
      _          <- validateUsingInputTypeStrategy(callContext)
      _          <- validateUsingAllowedTypesConstraint()
      outputType <- inferOutputType(callContext)
    } yield outputType
  }

}
