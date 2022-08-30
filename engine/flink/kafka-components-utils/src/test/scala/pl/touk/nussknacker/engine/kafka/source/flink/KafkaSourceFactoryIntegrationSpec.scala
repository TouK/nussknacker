package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.kafka.common.record.TimestampType
import KafkaSourceFactoryMixin.{ObjToSerialize, SampleKey, SampleValue}
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionConsumer
import pl.touk.nussknacker.engine.kafka.serialization
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.SinkForSampleValue

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

  test("should handle invalid expression type for topic") {
    val topic = "kafka-bad-expression-type"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta, topicParamValue = _ => s"123L" )
    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should include ("Bad expression type, expected: String, found: Long")
  }

  test("should handle null value for mandatory parameter") {
    val topic = "kafka-empty-mandatory-field"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta, topicParamValue = _ => s"" )
    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should include ("EmptyMandatoryParameter(This field is mandatory and can not be empty")
  }

  test("should raise exception when we provide wrong input variable") {
    val topic = "kafka-key-value-wrong-input-variable"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta, Map("invalid" -> "#input.invalid"))

    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should startWith("Compilation errors: ExpressionParserCompilationError(There is no property 'invalid'")
  }

  test("should raise exception when we provide wrong meta variable") {
    val topic = "kafka-key-value-wrong-meta-variable"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta, Map("invalid" -> "#inputMeta.invalid"))

    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should startWith("Compilation errors: ExpressionParserCompilationError(There is no property 'invalid'")
  }

  test("should raise exception when we provide wrong key variable") {
    val topic = "kafka-key-value-wrong-key-variable"
    val givenObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta, Map("invalid" -> "#inputMeta.key.invalid"))

    intercept[Exception] {
      runAndVerifyResult(topic, process, givenObj)
    }.getMessage should startWith("Compilation errors: ExpressionParserCompilationError(There is no property 'invalid'")
  }

  test("should handle situation when expected key is null for key value source") {
    val topic = "kafka-key-value-key-null"
    createTopic(topic)
    val objWithoutKey = ObjToSerialize(TestSampleValue, null, TestSampleHeaders)
    val correctObj = ObjToSerialize(TestSampleValue, TestSampleKey, TestSampleHeaders)
    pushMessage(objToSerializeSerializationSchema(topic), objWithoutKey, topic, timestamp = constTimestamp)
    pushMessage(objToSerializeSerializationSchema(topic), correctObj, topic, timestamp = constTimestamp + 1)
    val process = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta)
    run(process) {
      eventually {
        SinkForSampleValue.data shouldBe List(correctObj.value)
        RecordingExceptionConsumer.dataFor(runId) should have size 1
      }
    }
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

  test("error during deserialization") {
    val topic = "kafka-invalid-value"
    val invalidJson = "{asdf@#$"
    val process = createProcess(topic, SourceType.jsonValueWithMeta)
    createTopic(topic)
    pushMessage(new SimpleSerializationSchema[String](topic, identity).asInstanceOf[serialization.KafkaSerializationSchema[Any]], invalidJson, topic, timestamp = constTimestamp)
    val correctObj = ObjToSerialize(TestSampleValue, null, TestSampleHeaders)
    pushMessage(objToSerializeSerializationSchema(topic), correctObj, topic, timestamp = constTimestamp + 1)
    run(process) {
      eventually {
        SinkForSampleValue.data shouldBe List(correctObj.value)
        RecordingExceptionConsumer.dataFor(runId) should have size 1
      }
    }
  }

}
