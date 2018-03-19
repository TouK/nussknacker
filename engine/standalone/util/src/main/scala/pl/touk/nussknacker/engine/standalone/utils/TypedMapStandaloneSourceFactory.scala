package pl.touk.nussknacker.engine.standalone.utils

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedMapTypingResult}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.standalone.api.{StandaloneGetSource, StandaloneSourceFactory}

import scala.collection.JavaConversions._

class TypedMapStandaloneSourceFactory extends StandaloneSourceFactory[TypedMap] {

  @MethodToInvoke
  def create(@ParamName("type") definition: java.util.Map[String, String]) : StandaloneGetSource[TypedMap] = new StandaloneGetSource[TypedMap] with ReturningType {

    //TODO: type conversions??
    override def parse(parameters: Map[String, List[String]]): TypedMap = TypedMap(parameters.mapValues(_.head))

    //TODO: classloaders?
    override def returnType: typing.TypingResult = TypedMapTypingResult(definition.toMap
      .mapValues(ClassUtils.getClass)
      .mapValues(ClazzRef(_))
      .mapValues(Typed(_)))

  }

  override def clazz: Class[_] = classOf[TypedMap]

  override def testDataParser: Option[TestDataParser[TypedMap]] = Some(new QueryStringTestDataParser)

}

