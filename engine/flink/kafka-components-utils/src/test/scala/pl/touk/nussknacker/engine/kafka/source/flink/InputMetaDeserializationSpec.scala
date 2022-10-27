package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetection, TypingResultAwareTypeInformationCustomisation}
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import KafkaSourceFactoryMixin.SampleKey
import InputMetaDeserializationSpec.sampleKeyTypeInformation
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

import scala.collection.JavaConverters._

class InputMetaDeserializationSpec extends AnyFunSuite with Matchers with FlinkTypeInformationSerializationMixin{

  test("should serialize and deserialize input metadata with TypeInformation serializer") {
    val typeInformation = InputMetaTypeInformationProvider.typeInformation[SampleKey](sampleKeyTypeInformation)
    val givenObj = InputMeta[SampleKey](SampleKey("one", 2), "dummy", 3, 4L, 5L, TimestampType.CREATE_TIME, Map("one" -> "header value", "two" -> null).asJava, 6)

    serializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)()
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)()
  }

  test("should serialize and deserialize input metadata with TypingResultAwareTypeInformationDetection and customisations") {
    val inputMetaTypingResult = InputMeta.withType(Typed[SampleKey])
    val typeInformation: TypeInformation[InputMeta[SampleKey]] = TypingResultAwareTypeInformationDetection(getClass.getClassLoader).forType(inputMetaTypingResult)
    val givenObj = InputMeta[SampleKey](SampleKey("one", 2), "dummy", 3, 4L, 5L, TimestampType.CREATE_TIME, Map("one" -> "header value", "two" -> null).asJava, 6)
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)()
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)()
  }

}

object InputMetaDeserializationSpec {
  val partOneType: TypeInformation[String] = TypeInformation.of(classOf[String])
  val partTwoType: TypeInformation[Long] = TypeInformation.of(classOf[Long])

  val sampleKeyFieldTypes: List[TypeInformation[_]] = List(partOneType, partTwoType)
  val sampleKeyTypeInformation: TypeInformation[SampleKey] = ConcreteCaseClassTypeInfo[SampleKey](
    ("partOne", partOneType),
    ("partTwo", partTwoType)
  )
}

class SampleKeyTypeInformationCustomisation extends TypingResultAwareTypeInformationCustomisation {

  override def customise(originalDetection: TypeInformationDetection): PartialFunction[typing.TypingResult, TypeInformation[_]] = {
    case a:TypedClass if a.objType.klass == classOf[SampleKey] => sampleKeyTypeInformation
  }
}
