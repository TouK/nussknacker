package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, VariableConstants}
import shapeless.=:!=

import scala.reflect.runtime.universe._

/**
  * Common trait for source of events. For Flink see pl.touk.nussknacker.engine.flink.api.process.FlinkSource
  */
trait Source

/**
  * Support for test source functionality. Uses [[pl.touk.nussknacker.engine.api.test.TestDataParser]] to define
  * how test data are provided to the source.
  * @tparam T - type of object that is passed to Source in tests. Please note that depending on engine it may
  *         be different from actual event type produced by source. See e.g. difference between FlinkKafkaSource
  *         and LiteKafkaSourceImpl which is due to the difference between implementation of test sources
  *
  */
trait SourceTestSupport[+T] { self: Source =>
  def testDataParser: TestDataParser[T]
}

/**
  * Optional support for test source functionality. Defines how test data should be prepared,
  * in a way that is recognized further by [[pl.touk.nussknacker.engine.api.test.TestDataParser]].
  */
trait TestDataGenerator { self: Source with SourceTestSupport[_] =>
  def generateTestData(size: Int) : Array[Byte]
}

/**
  * [[pl.touk.nussknacker.engine.api.process.SourceFactory]] has to have method annotated with [[pl.touk.nussknacker.engine.api.MethodToInvoke]]
  * that returns [[pl.touk.nussknacker.engine.api.process.Source]]
  * IMPORTANT lifecycle notice:
  * Implementations of this class *must not* allocate resources (connections, file handles etc.)
  */
trait SourceFactory extends Serializable with Component

object SourceFactory {

  def noParam(source: Source, inputType: TypingResult): SourceFactory =
    NoParamSourceFactory(source, inputType)

  def noParam[T: TypeTag](source: Source)(implicit ev: T =:!= Nothing): SourceFactory =
    NoParamSourceFactory(source, Typed.fromDetailedType[T])

  case class NoParamSourceFactory(source: Source, inputType: TypingResult) extends SourceFactory {

    @MethodToInvoke
    def create()(implicit nodeId: NodeId): ContextTransformation = ContextTransformation
      .definedBy(vc => vc.withVariable(VariableConstants.InputVariableName, inputType, None))
      .implementedBy(source)
  }

}
