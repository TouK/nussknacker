package pl.touk.nussknacker.engine.requestresponse.utils

import java.nio.charset.StandardCharsets
import io.circe.Decoder
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{CirceUtil, MethodToInvoke, VariableConstants}
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport}
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, RequestResponseSourceFactory}

import scala.reflect.ClassTag

class JsonRequestResponseSourceFactory[T: Decoder : ClassTag] extends RequestResponseSourceFactory {

  @MethodToInvoke
  def create(implicit nodeId: NodeId): ContextTransformation = ContextTransformation
    .definedBy(vc => vc.withVariable(VariableConstants.InputVariableName, Typed[T], None))
    .implementedBy(
      new RequestResponsePostSource[T] with SourceTestSupport[T] {

        override def parse(parameters: Array[Byte]): T = {
          parse(new String(parameters, StandardCharsets.UTF_8))
        }

        override def testDataParser: TestDataParser[T] = new NewLineSplittedTestDataParser[T] {
          override def parseElement(testElement: String): T = parse(testElement)
        }

        private def parse(str: String): T = CirceUtil.decodeJsonUnsafe[T](str, "invalid request in request-response source")

      })

}