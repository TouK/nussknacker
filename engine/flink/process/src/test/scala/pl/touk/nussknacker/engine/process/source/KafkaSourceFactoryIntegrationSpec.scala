package pl.touk.nussknacker.engine.process.source

import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin.{ObjToSerialize, SampleKey, SampleValue}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForInputMeta

import scala.collection.JavaConverters.mapAsJavaMapConverter

class KafkaSourceFactoryIntegrationSpec extends KafkaSourceFactoryProcessMixin  {

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
        SinkForInputMeta.data shouldBe List(InputMeta("""{"partOne":"some key","partTwo":123}""", topic, 0, 0L, constTimestamp, TimestampType.CREATE_TIME, givenObj.headers.asJava, 0))
      }
    }
  }

  test("source with two input topics") {
    val topicOne = "kafka-multitopic-one"
    val topicTwo = "kafka-multitopic-two"
    val topic = s"$topicOne, $topicTwo"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonValueWithMeta)
    createTopic(topicOne)
    createTopic(topicTwo)
    pushMessage(objToSerializeSerializationSchema(topicOne), givenObj, topicOne, timestamp = constTimestamp)
    pushMessage(objToSerializeSerializationSchema(topicTwo), givenObj, topicTwo, timestamp = constTimestamp)
    run(process) {
      eventually {
        SinkForInputMeta.data.map(_.topic).toSet shouldEqual Set(topicOne, topicTwo)
      }
    }
  }

  test("source with exception within prepareInitialParameters") {
    val topic = "kafka-source-with-exception"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonValueWithMetaWithException)

    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should include ("Checking scenario: fetch topics from external source")
  }

}
