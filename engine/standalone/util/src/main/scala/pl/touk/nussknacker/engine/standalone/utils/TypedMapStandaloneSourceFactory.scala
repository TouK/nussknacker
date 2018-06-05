package pl.touk.nussknacker.engine.standalone.utils

import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.standalone.api.{StandaloneGetSource, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.util.typing.TypingUtils

class TypedMapStandaloneSourceFactory extends StandaloneSourceFactory[TypedMap] {

  @MethodToInvoke
  def create(@ParamName("type") definition: java.util.Map[String, _]) : StandaloneGetSource[TypedMap] = new StandaloneGetSource[TypedMap] with ReturningType {

    //TODO: type conversions??
    override def parse(parameters: Map[String, List[String]]): TypedMap = TypedMap(parameters.mapValues(_.head))

    override def returnType: typing.TypingResult = TypingUtils.typedMapDefinitionFromParameters(definition)

  }

  override def clazz: Class[_] = classOf[TypedMap]

  override def testDataParser: Option[TestDataParser[TypedMap]] = Some(new QueryStringTestDataParser)

}

