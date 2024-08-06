package pl.touk.nussknacker.engine.flink.table.aggregate

import enumeratum._
import org.apache.flink.table.catalog.DataTypeFactory
import org.apache.flink.table.functions.{BuiltInFunctionDefinition, BuiltInFunctionDefinitions, FunctionDefinition}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.inference.CallContext
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory.aggregateByParamName
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregator.{NuAggregatorCallContext, buildError}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesConversions.LogicalTypeConverter

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

  private def buildError(inputType: TypingResult)(implicit nodeId: NodeId) = {
    CustomNodeError(
      s"Invalid type: ${inputType.withoutValue.display}",
      Some(aggregateByParamName)
    )
  }

  private class NuAggregatorCallContext(
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

}

// TODO: extract call expression to TableAggregator
// TODO: add distinct
sealed trait TableAggregator extends EnumEntry {

  val displayName: String

  def flinkFunctionDefinition: BuiltInFunctionDefinition

  val flinkFunctionName: String = flinkFunctionDefinition.getName

  def outputType(inputTypeTypingResult: TypingResult, inputTypeDataType: DataType, dataTypeFactory: DataTypeFactory)(
      implicit nodeId: NodeId
  ): Either[ProcessCompilationError, TypingResult] = {
    val typeInferenceStrategy = flinkFunctionDefinition.getTypeInference(dataTypeFactory).getInputTypeStrategy
    val callContext =
      new NuAggregatorCallContext(dataTypeFactory, flinkFunctionDefinition, inputTypeDataType)
    typeInferenceStrategy.inferInputTypes(callContext, false).toScala match {
      case Some(value) =>
        value.asScala.headOption match {
          case Some(value) => Right(value.getLogicalType.toTypingResult)
          case None        => Left(buildError(inputTypeTypingResult))
        }
      case None => Left(buildError(inputTypeTypingResult))
    }
  }

}
