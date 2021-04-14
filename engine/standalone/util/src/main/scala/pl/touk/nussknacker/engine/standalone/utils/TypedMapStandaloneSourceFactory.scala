package pl.touk.nussknacker.engine.standalone.utils

import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.standalone.api.{StandaloneGetSource, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import scala.collection.immutable.ListMap

class TypedMapStandaloneSourceFactory extends StandaloneSourceFactory[TypedMap] {
  import scala.collection.JavaConverters._

  @MethodToInvoke
  def create(@ParamName("type") definition: java.util.Map[String, _]) : StandaloneGetSource[TypedMap] = new StandaloneGetSource[TypedMap] with ReturningType with SourceTestSupport[TypedMap] {

    //TODO: type conversions??
    override def parse(parameters: Map[String, List[String]]): TypedMap = {
      TypedMap(ListMap(parameters.mapValues(_.head).toList: _*))
    }

    override def returnType: typing.TypingResult = {
      // FIXME TODO
      TypingUtils.typeMapDefinition(ListMap(definition.asScala.toList: _*))
    }

    override def testDataParser: TestDataParser[TypedMap] = new QueryStringTestDataParser
  }

  override def clazz: Class[_] = classOf[TypedMap]

}

