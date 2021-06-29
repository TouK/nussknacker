package pl.touk.nussknacker.genericmodel

import io.circe.generic.JsonCodec
import io.circe.syntax._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.generic.KafkaDelayedSourceFactory.{DelayParameterName, TimestampFieldParamName}
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory.TypeDefinitionParamName
import pl.touk.nussknacker.engine.kafka.generic.sources.{DelayedGenericTypedJsonSourceFactory, FixedRecordFormatterFactoryWrapper, JsonRecordFormatter}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactoryProcessMixin
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactoryProcessMixin.recordingExceptionHandler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForLongs
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

class DelayedGenericItSpec extends FunSuite with FlinkSpec with Matchers with KafkaSpec with KafkaSourceFactoryProcessMixin {

  @JsonCodec
  private case class BasicEvent(id: String, name: String, timestamp: Long)

  private object BasicEvent {
    final val timestampFieldName = "timestamp"

    final val definition = """{"id":"String","name":"String","timestamp":"Long"}"""

    def apply(name: String): BasicEvent = BasicEvent(UUID.randomUUID().toString, name, Instant.now().toEpochMilli)
  }

  override protected lazy val creator: ProcessConfigCreator = new DelayedGenericProcessConfigCreator

  private val now: Long = System.currentTimeMillis()

  private val event: BasicEvent = BasicEvent(id = "123", name = "kafka-generic-delayed-test", timestamp = now)

  test("properly process data using kafka-generic-delayed source") {
    val inputTopic = "topic-all-parameters-valid"
    val process = createProcessWithDelayedSource(inputTopic, BasicEvent.definition, s"'${BasicEvent.timestampFieldName}'", "0L")
    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.sendRawMessage(inputTopic, Array.empty, event.asJson.noSpaces.getBytes(StandardCharsets.UTF_8), timestamp = now).futureValue
    run(process) {
      eventually {
        recordingExceptionHandler.data shouldBe empty
        SinkForLongs.data should have size 1
      }
    }
  }

  test("timestampField and delay param are null") {
    val inputTopic = "topic-empty-parameters"
    val process = createProcessWithDelayedSource(inputTopic, BasicEvent.definition, s"null", s"null")
    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.sendRawMessage(inputTopic, Array.empty, event.asJson.noSpaces.getBytes(StandardCharsets.UTF_8), timestamp = now).futureValue
    run(process) {
      eventually {
        recordingExceptionHandler.data shouldBe empty
        SinkForLongs.data should have size 1
      }
    }
  }

  test("handle not exist timestamp field param") {
    val inputTopic = "topic-invalid-timestamp-field"
    val process = createProcessWithDelayedSource(inputTopic, BasicEvent.definition, s"'unknownField'", s"null")
    intercept[IllegalArgumentException] {
      run(process) {}
    }.getMessage should include ("Field: 'unknownField' doesn't exist in definition: id,name,timestamp.")
  }

  test("handle invalid negative param") {
    val inputTopic = "topic-invalid-delay"
    val process = createProcessWithDelayedSource(inputTopic, BasicEvent.definition, s"'${BasicEvent.timestampFieldName}'", s"-10L")
    intercept[IllegalArgumentException] {
      run(process) {}
    }.getMessage should include ("LowerThanRequiredParameter(This field value has to be a number greater than or equal to 0,Please fill field with proper number,delayInMillis,start)")
  }

  private def createProcessWithDelayedSource(topic: String, definition: String, timestampField: String,  delay: String) = {

    import spel.Implicits._

    EspProcessBuilder.id("kafka-generic-delayed-test")
      .parallelism(1)
      .exceptionHandler()
      .source(
        "start",
        "kafka-generic-delayed",
        s"$TopicParamName" -> s"'${topic}'",
        s"$TypeDefinitionParamName" -> s"${definition}",
        s"$TimestampFieldParamName" -> s"${timestampField}",
        s"$DelayParameterName" -> s"${delay}"
      )
      .sink("out", "T(java.lang.System).currentTimeMillis()", "sinkForStrings")
  }
}

class DelayedGenericProcessConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "kafka-generic-delayed" -> defaultCategory(new DelayedGenericTypedJsonSourceFactory(None, FixedRecordFormatterFactoryWrapper(JsonRecordFormatter), processObjectDependencies))
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sinkForStrings" -> defaultCategory(SinkFactory.noParam(SinkForLongs))
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(_ => recordingExceptionHandler)

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestDelayedSource")
}
