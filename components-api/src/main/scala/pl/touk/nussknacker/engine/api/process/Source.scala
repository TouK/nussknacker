package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, VariableConstants}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.definition.Parameter
import shapeless.=:!=

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/**
  * Common trait for source of events. For Flink see pl.touk.nussknacker.engine.flink.api.process.FlinkSource
  */
trait Source

/**
  * Support for test source functionality. Uses [[pl.touk.nussknacker.engine.api.test.TestRecordParser]] to define
  * how test record is parsed and provided to the source.
 *
  * @tparam T - type of object that is passed to Source in tests. Please note that depending on engine it may
  *         be different from actual event type produced by source. See e.g. difference between FlinkKafkaSource
  *         and LiteKafkaSourceImpl which is due to the difference between implementation of test sources
  *
  */
trait SourceTestSupport[+T] { self: Source =>
  def testRecordParser: TestRecordParser[T]
}

/**
  * Optional support for test source functionality. Defines how test data should be prepared,
  * in a way that is recognized further by [[pl.touk.nussknacker.engine.api.test.TestRecordParser]].
  */
trait TestDataGenerator { self: Source with SourceTestSupport[_] =>
  def generateTestData(size: Int): TestData
}

/**
 * Optional functionality which should provide field definitions based on input schema
 * Based on those fields UI creates a window allowing user to test scenario based on schema.
 */
trait TestWithParameters[+T] { self: Source with SourceTestSupport[_] =>
  //TODO add support for dynamic parameters
  def parameterDefinitions: List[Parameter]
  def parametersToTestData(params: Map[String, AnyRef]): T
}

object TestWithParameters {
  def unflattenMap(flatMap: Map[String, AnyRef], delimiter: Char = '.'): Map[String, AnyRef] = {
    flatMap.foldLeft(Map.empty[String, AnyRef]) {
      case (result, (key, value)) =>
        if (key.contains(delimiter)) {
          val (parentKey, childKey) = key.span(_ != delimiter)
          val parentValue = result.getOrElse(parentKey, Map.empty[String, AnyRef]).asInstanceOf[Map[String, AnyRef]]
          val childMap = unflattenMap(Map(childKey.drop(1) -> value), delimiter)
          result + (parentKey -> (parentValue ++ childMap))
        } else {
          result + (key -> value)
        }
    }
  }
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
    NoParamSourceFactory(_ => source, inputType)

  def noParam[T: TypeTag](source: Source)(implicit ev: T =:!= Nothing): SourceFactory =
    NoParamSourceFactory(_ => source, Typed.fromDetailedType[T])

  def noParam[T: TypeTag](createSource: NodeId => Source)(implicit ev: T =:!= Nothing): SourceFactory =
    NoParamSourceFactory(createSource, Typed.fromDetailedType[T])

  def noParamFromClassTag[T: ClassTag](source: Source)(implicit ev: T =:!= Nothing): SourceFactory =
    NoParamSourceFactory(_ => source, Typed.apply[T])

  case class NoParamSourceFactory(createSource: NodeId => Source, inputType: TypingResult) extends SourceFactory {

    @MethodToInvoke
    def create()(implicit nodeId: NodeId): ContextTransformation = ContextTransformation
      .definedBy(vc => vc.withVariable(VariableConstants.InputVariableName, inputType, None))
      .implementedBy(createSource(nodeId))
  }

}
