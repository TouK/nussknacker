package pl.touk.nussknacker.engine.flink.table.aggregate

import enumeratum._
import org.apache.flink.table.api.DataTypes.RAW
import org.apache.flink.table.catalog.DataTypeFactory
import org.apache.flink.table.functions.{BuiltInFunctionDefinition, BuiltInFunctionDefinitions, FunctionDefinition}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.inference.CallContext
import org.apache.flink.table.types.logical.LogicalTypeRoot.{
  BIGINT,
  BOOLEAN,
  DATE,
  DECIMAL,
  DOUBLE,
  FLOAT,
  INTEGER,
  SMALLINT,
  TIMESTAMP_WITHOUT_TIME_ZONE,
  TIMESTAMP_WITH_LOCAL_TIME_ZONE,
  TIME_WITHOUT_TIME_ZONE,
  TINYINT,
  VARCHAR
}
import org.apache.flink.table.types.logical.{LogicalTypeFamily, LogicalTypeRoot, RawType}
import org.apache.flink.table.types.utils.TypeInfoDataTypeConverter
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory.aggregateByParamName
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregatorType.{
  NuAggregationFunctionCallContext,
  buildError,
  dataTypeFactory,
  toDataType
}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypeFactoryPreparer

import java.util
import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions.LogicalTypeExtension

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
    override val displayName: String                                = "Average"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.AVG
  }

  case object Count extends TableAggregatorType {
    override val displayName: String                                = "Count"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.COUNT
  }

  case object Max extends TableAggregatorType {
    override val displayName: String                                = "Max"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.MAX
    override val inputParameterRuntimeAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(
      List(
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
    )
  }

  case object Min extends TableAggregatorType {
    override val displayName: String                                = "Min"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.MIN
    override val inputParameterRuntimeAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(
      List(
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
    )
  }

  // As of Flink 1.19, time-related types are not supported in FIRST_VALUE aggregate function.
  // See: https://issues.apache.org/jira/browse/FLINK-15867
  // See AggFunctionFactory.createFirstValueAggFunction
  case object First extends TableAggregatorType {
    override val displayName: String                                = "First"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.FIRST_VALUE
    override val inputParameterRuntimeAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(
      List(
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
    )

  }

  case object Last extends TableAggregatorType {
    override val displayName: String                                = "Last"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.LAST_VALUE
    override val inputParameterRuntimeAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = Some(
      List(
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
    )
  }

  case object Sum extends TableAggregatorType {
    override val displayName: String                                = "Sum"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.SUM
  }

  case object PopulationStandardDeviation extends TableAggregatorType {
    override val displayName: String                                = "Population standard deviation"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.STDDEV_POP
  }

  case object SampleStandardDeviation extends TableAggregatorType {
    override val displayName: String                                = "Sample standard deviation"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.STDDEV_SAMP
  }

  case object PopulationVariance extends TableAggregatorType {
    override val displayName: String                                = "Population variance"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.VAR_POP
  }

  case object SampleVariance extends TableAggregatorType {
    override val displayName: String                                = "Sample variance"
    override def flinkFunctionDefinition: BuiltInFunctionDefinition = BuiltInFunctionDefinitions.VAR_SAMP
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

  // TODO: specific type errors per aggregator that communicate the type constraint. Until we have consistent base
  //  validation of types in Table-API components it's going to be misleading.
  private def buildError(inputType: TypingResult, aggregator: TableAggregatorType, reason: Option[String])(
      implicit nodeId: NodeId
  ) = {
    val baseErrorMessage =
      s"Invalid type: '${inputType.withoutValue.display}' for selected aggregator: '${aggregator.displayName}'."
    val errorMessage = reason match {
      case Some(reason) => s"$baseErrorMessage $reason"
      case None         => baseErrorMessage
    }
    CustomNodeError(
      errorMessage,
      Some(aggregateByParamName)
    )
  }

}

// TODO: remodel aggregators to TableAggregatorType and instantiate Aggregator that will contain information like
//  output type, Table-API expression
sealed trait TableAggregatorType extends EnumEntry {

  val displayName: String

  def flinkFunctionDefinition: BuiltInFunctionDefinition

  val flinkFunctionName: String = flinkFunctionDefinition.getName

  val inputParameterConstraintDescription: Option[String] = None

  val inputParameterRuntimeAllowedTypesConstraint: Option[List[LogicalTypeRoot]] = None

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
        case Some(_) =>
          inputParameterRuntimeAllowedTypesConstraint match {
            case Some(allowedTypes) =>
              if (toDataType(inputType).getLogicalType.isAnyOf(allowedTypes: _*)) {
                Right(())
              } else {
                Left(buildError(inputType, this, inputParameterConstraintDescription))
              }
            case None => Right(())
          }
        case None => Left(buildError(inputType, this, inputParameterConstraintDescription))
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
        case None        => Left(buildError(inputType, this, inputParameterConstraintDescription))
      }
    }
    val callContext = NuAggregationFunctionCallContext(inputType, flinkFunctionDefinition)
    for {
      _          <- validateInputType(callContext)
      outputType <- inferOutputType(callContext)
    } yield outputType
  }

}
