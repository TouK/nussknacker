package pl.touk.nussknacker.engine.requestresponse.utils

import io.circe.Json
import pl.touk.nussknacker.engine.api.{CirceUtil, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.api.typed.{ReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, RequestResponseSourceFactory}
import pl.touk.nussknacker.engine.util.typing.{JsonToTypedMapConverter, TypingUtils}

class GenericPostSourceFactory extends RequestResponseSourceFactory {

  @MethodToInvoke
  def create(@ParamName("type") definition: java.util.Map[String, Any])(implicit nodeIdPassed: NodeId): RequestResponsePostSource[TypedMap] = new RequestResponsePostSource[TypedMap] with ReturningType with SourceTestSupport[TypedMap] {

    override val nodeId: NodeId = nodeIdPassed

    override def returnType: typing.TypingResult = TypingUtils.typeMapDefinition(definition)

    override def testDataParser: TestDataParser[TypedMap] = new QueryStringTestDataParser

    override def parse(parameters: Array[Byte]): TypedMap = {
      val json = CirceUtil.decodeJsonUnsafe[Json](parameters)
      JsonToTypedMapConverter.jsonToTypedMap(json)
    }

  }

}
