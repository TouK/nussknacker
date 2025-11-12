package pl.touk.nussknacker.engine.requestresponse.utils

import io.circe.Decoder
import pl.touk.nussknacker.engine.api.{CirceUtil, MethodToInvoke, NodeId, VariableConstants}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.definition.WithExplicitTypesToExtract
import pl.touk.nussknacker.engine.api.process.SourceTestSupport
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponsePostSource, RequestResponseSourceFactory}
import pl.touk.nussknacker.engine.requestresponse.api.Request

import scala.reflect.ClassTag

class JsonRequestResponseSourceFactory[T: Decoder: ClassTag]
    extends RequestResponseSourceFactory
    with WithExplicitTypesToExtract {

  @MethodToInvoke
  def create(implicit nodeIdPassed: NodeId): ContextTransformation = ContextTransformation
    .definedBy(vc => vc.withVariable(VariableConstants.InputVariableName, Typed[T], None))
    .implementedBy(new RequestResponsePostSource[T] with SourceTestSupport[Request[T]] {

      override val nodeId: NodeId = nodeIdPassed

      override def parse(parameters: Array[Byte], headers: Map[String, String]): Request[T] = {
        val decoded = CirceUtil.decodeJsonUnsafe(parameters, "invalid request in request-response source")
        Request(decoded, headers)
      }

      // TODO: Add passing headers at tests from file
      override def testRecordParser: TestRecordParser[Request[T]] = (testRecords: List[TestRecord]) =>
        testRecords.map { testRecord =>
          val decoded = CirceUtil.decodeJsonUnsafe(testRecord.json, "invalid request in request-response source")
          Request(decoded)
        }

    })

  override def typesToExtract: List[typing.TypedClass] = Typed.typedClassOpt[T].toList

}
