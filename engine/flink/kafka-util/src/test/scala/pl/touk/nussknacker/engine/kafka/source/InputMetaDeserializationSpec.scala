package pl.touk.nussknacker.engine.kafka.source

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, ScalaCaseClassSerializer}
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetection, TypingResultAwareTypeInformationCustomisation}
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import KafkaSourceFactoryMixin.SampleKey
import pl.touk.nussknacker.engine.kafka.source.InputMetaDeserializationSpec.sampleKeyTypeInformation
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection

import scala.collection.JavaConverters._

class InputMetaDeserializationSpec extends FunSuite with Matchers with FlinkTypeInformationSerializationMixin{

  test("should serialize and deserialize input metadata with TypeInformation serializer") {
    val typeInformation = InputMeta.typeInformation[SampleKey](sampleKeyTypeInformation)
    val givenObj = InputMeta[SampleKey](SampleKey("one", 2), "dummy", 3, 4L, 5L, TimestampType.CREATE_TIME, Map("one" -> "header value", "two" -> null).asJava, 6)

    serializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)()
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)()
  }

  test("should serialize and deserialize input metadata with TypingResultAwareTypeInformationDetection and customisations") {
    val inputMetaTypingResult = InputMeta.withType(Typed[SampleKey])
    val typeInformation = TypingResultAwareTypeInformationDetection(getClass.getClassLoader).forType(inputMetaTypingResult)
    val givenObj = InputMeta[SampleKey](SampleKey("one", 2), "dummy", 3, 4L, 5L, TimestampType.CREATE_TIME, Map("one" -> "header value", "two" -> null).asJava, 6)
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)()
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)()
  }

}

object InputMetaDeserializationSpec {
  val sampleKeyFieldTypes: List[TypeInformation[_]] = List(TypeInformation.of(classOf[String]), TypeInformation.of(classOf[Long]))
  val sampleKeyTypeInformation: TypeInformation[SampleKey] = new CaseClassTypeInfo[SampleKey](classOf[SampleKey], Array.empty, sampleKeyFieldTypes, List("partOne", "partTwo")){
    override def createSerializer(config: ExecutionConfig): TypeSerializer[SampleKey] =
      new ScalaCaseClassSerializer[SampleKey](classOf[SampleKey], sampleKeyFieldTypes.map(_.createSerializer(config)).toArray)
  }
}

class SampleKeyTypeInformationCustomisation extends TypingResultAwareTypeInformationCustomisation {

  override def customise(originalDetection: TypeInformationDetection): PartialFunction[typing.TypingResult, TypeInformation[_]] = {
    case a:TypedClass if a.objType.klass == classOf[SampleKey] => sampleKeyTypeInformation
  }
}