package pl.touk.nussknacker.engine.process.source

import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.util.KafkaGenericNodeMixin.{ObjToSerialize, SampleKey, SampleValue}
import pl.touk.nussknacker.engine.process.source.KafkaGenericNodeProcessConfigCreator.SinkForInputMeta

import scala.collection.JavaConverters.mapAsJavaMapConverter

class KafkaGenericNodeSourceIntegrationSpec extends KafkaGenericNodeProcessMixin  {

  private val TestSampleValue = SampleValue("some id", "some field")
  private val TestSampleKey = SampleKey("some key", 123L)
  private val TestSampleHeaders = Map("first" -> "header value", "second" -> null)


  test("should handle input variable with key and metadata provided by consumer record") {
    val topic = "kafka-key-value-meta"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta)
    runAndVerifyResult(topic, process, givenObj)
  }

  test("should raise exception when we provide wrong input variable") {
    val topic = "kafka-key-value-wrong-input-variable"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta, Map("invalid" -> "#input.invalid"))

    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should startWith("Compilation errors: ExpressionParseError(There is no property 'invalid'")
  }

  test("should raise exception when we provide wrong meta variable") {
    val topic = "kafka-key-value-wrong-meta-variable"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta, Map("invalid" -> "#inputMeta.invalid"))

    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should startWith("Compilation errors: ExpressionParseError(There is no property 'invalid'")
  }

  test("should raise exception when we provide wrong key variable") {
    val topic = "kafka-key-value-wrong-key-variable"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta, Map("invalid" -> "#inputMeta.key.invalid"))

    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should startWith("Compilation errors: ExpressionParseError(There is no property 'invalid'")
  }

  test("should fail when expected key is null") {
    val topic = "kafka-key-value-key-null"
    val givenObj = ObjToSerialize(TestSampleValue, null, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta)
    runAndFail(topic, process, givenObj)
  }

  test("source with value only should accept null key") {
    val topic = "kafka-value-with-meta"
    val givenObj = ObjToSerialize(TestSampleValue, null, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonValueWithMeta)
    val result = runAndVerifyResult(topic, process, givenObj)
  }

  test("source with value only should accept given key, fallback to String deserialization") {
    val topic = "kafka-value-with-ignored-key"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonValueWithMeta)
    createTopic(topic)
    pushMessage(objToSerializeSerializationSchema(topic), givenObj, topic, timestamp = constTimestamp)
    run(process) {
      eventually {
        SinkForInputMeta.data shouldBe List(InputMeta("""{"partOne":"some key","partTwo":123}""", topic, 0, 0L, constTimestamp, givenObj.headers.asJava))
      }
    }
  }

}
