package pl.touk.nussknacker.engine.requestresponse.utils

import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.TestRecordParser
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.{MethodToInvoke, NodeId, ParamName}
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseGetSource, RequestResponseSourceFactory}
import pl.touk.nussknacker.engine.util.typing.TypingUtils
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class TypedMapRequestResponseSourceFactory extends RequestResponseSourceFactory {

  @MethodToInvoke
  def create(@ParamName("type") definition: java.util.Map[String, _])(implicit nodeIdPassed: NodeId): RequestResponseGetSource[TypedMap] = new RequestResponseGetSource[TypedMap] with ReturningType with SourceTestSupport[TypedMap] {

    override val nodeId: NodeId = nodeIdPassed

    //TODO: type conversions??
    override def parse(parameters: Map[String, List[String]]): TypedMap = TypedMap(parameters.mapValuesNow(_.head))

    override def returnType: typing.TypingResult = TypingUtils.typeMapDefinition(definition)

    override def testRecordParser: TestRecordParser[TypedMap] = new QueryStringTestDataParser
  }

}

