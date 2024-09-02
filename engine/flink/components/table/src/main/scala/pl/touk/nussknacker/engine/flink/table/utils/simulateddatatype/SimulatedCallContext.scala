package pl.touk.nussknacker.engine.flink.table.utils.simulateddatatype

import org.apache.flink.table.catalog.DataTypeFactory
import org.apache.flink.table.functions.{BuiltInFunctionDefinition, FunctionDefinition}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.inference.CallContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions.LogicalTypeExtension

import java.util
import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object SimulatedCallContext {

  def validateAgainstFlinkFunctionDefinition(
      functionDefinition: BuiltInFunctionDefinition,
      singleInputParameterType: TypingResult
  ): Either[Unit, TypingResult] = {
    val callContext =
      new BuiltInFunctionOneArgSimulatedCallContext(
        functionDefinition = functionDefinition,
        argument = ToSimulatedDataTypeConverter.toDataType(singleInputParameterType)
      )
    for {
      _          <- callContext.validateUsingInputTypeStrategy
      outputType <- callContext.inferOutputType
    } yield outputType
  }

  // This is to simulate type validation and output type inference for a Flink function on a real Flink cluster with
  // a real DataTypeFactory
  private class BuiltInFunctionOneArgSimulatedCallContext(
      val functionDefinition: BuiltInFunctionDefinition,
      val argument: DataType
  ) extends CallContext {
    override def getDataTypeFactory: DataTypeFactory                         = SimulatedDataTypeFactory.instance
    override def getFunctionDefinition: FunctionDefinition                   = functionDefinition
    override def isArgumentLiteral(pos: Int): Boolean                        = false
    override def isArgumentNull(pos: Int): Boolean                           = false
    override def getArgumentValue[T](pos: Int, clazz: Class[T]): Optional[T] = None.toJava
    override def getName: String                                             = functionDefinition.getName
    override def getArgumentDataTypes: util.List[DataType]                   = List(argument).asJava
    override def getOutputDataType: Optional[DataType]                       = None.toJava
    override def isGroupedAggregation: Boolean                               = true

    def validateUsingInputTypeStrategy: Either[Unit, Unit] = {
      getFunctionDefinition
        .getTypeInference(SimulatedDataTypeFactory.instance)
        .getInputTypeStrategy
        .inferInputTypes(this, false)
        .toScala
        .flatMap(_.asScala.headOption) match {
        case Some(_) => Right(())
        case None    => Left(())
      }
    }

    def inferOutputType: Either[Unit, TypingResult] = {
      getFunctionDefinition
        .getTypeInference(SimulatedDataTypeFactory.instance)
        .getOutputTypeStrategy
        .inferType(this)
        .toScala match {
        case Some(value) => Right(value.getLogicalType.toTypingResult)
        case None        => Left(())
      }
    }

  }

}
