package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

import scala.jdk.CollectionConverters._

class InputMetaDeserializationSpec extends AnyFunSuite with Matchers with FlinkTypeInformationSerializationMixin {

  test(
    "should serialize and deserialize input metadata with TypingResultAwareTypeInformationDetection"
  ) {
    val typeInformation =
      new TypingResultAwareTypeInformationDetection(List.empty).forType[java.util.Map[String @unchecked, _]](
        InputMeta.withType(Typed.record(List("partOne" -> Typed[String], "partTwo" -> Typed[Int])))
      )
    val givenObj = InputMeta(
      Map("partOne" -> "one", "partTwo" -> 2).asJava,
      "dummy",
      3,
      4L,
      5L,
      TimestampType.CREATE_TIME,
      Map("one" -> "header value", "two" -> null).asJava,
      6
    )
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)()
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)()
  }

}
