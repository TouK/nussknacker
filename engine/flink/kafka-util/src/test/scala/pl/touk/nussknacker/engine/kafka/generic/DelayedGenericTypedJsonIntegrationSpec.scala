package pl.touk.nussknacker.engine.kafka.generic

import io.circe.generic.JsonCodec
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.generic.KafkaDelayedSourceFactory.{DelayParameterName, TimestampFieldParamName}
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory.TypeDefinitionParamName
import pl.touk.nussknacker.engine.kafka.generic.sources.{DelayedGenericTypedJsonSourceFactory, FixedRecordFormatterFactoryWrapper, JsonRecordFormatter}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.JsonSerializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactoryProcessMixin
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactoryProcessMixin.recordingExceptionHandler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForLongs
import pl.touk.nussknacker.engine.process.helpers.SinkForType
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.time.{Duration, Instant}
import java.util.UUID

class DelayedGenericTypedJsonIntegrationSpec extends FunSuite with FlinkSpec with Matchers with KafkaSpec with KafkaSourceFactoryProcessMixin {

  @JsonCodec
  case class BasicEvent(id: String, name: String, timestamp: Option[Long])

  object BasicEvent {
    final val timestampFieldName = "timestamp"

    final val definition = """{"id":"String","name":"String","timestamp":"Long"}"""

    def apply(name: String): BasicEvent = BasicEvent(UUID.randomUUID().toString, name, Some(Instant.now().toEpochMilli))
  }

  private val serializationSchema: String => KafkaSerializationSchema[Any] =
    (topic: String) => new JsonSerializationSchema[BasicEvent](topic).asInstanceOf[KafkaSerializationSchema[Any]]

  override protected lazy val creator: ProcessConfigCreator = new DelayedGenericProcessConfigCreator

  private val now: Long = System.currentTimeMillis()

  private def givenObj(timestamp: Long = now) = BasicEvent(id = "123", name = "kafka-generic-delayed-test", timestamp = Some(timestamp))

  test("properly process data using kafka-generic-delayed source") {
    val largeDelay = Duration.ofHours(10)
    //we want to test that timestamp from event is taken into account, so we set it to 11 hours before now
    val timeBeforeDelay = now - largeDelay.plusHours(1).toMillis

    val topic = "topic-all-parameters-valid"
    val process = createProcessWithDelayedSource(topic, BasicEvent.definition, s"'${BasicEvent.timestampFieldName}'",
      s"${largeDelay.toMillis}L")
    runAndVerify(topic, process, givenObj(timeBeforeDelay))
  }


  test("process data with empty timestampField") {
    val largeDelay = Duration.ofHours(10)
    //we want to test that timestamp from event is taken into account, so we set it to 11 hours before now
    val timeBeforeDelay = now - largeDelay.plusHours(1).toMillis

    val topic = "topic-empty-timestamp"
    val process = createProcessWithDelayedSource(topic, BasicEvent.definition, "null", s"${largeDelay.toMillis}L")
    runAndVerify(topic, process, givenObj(), timeBeforeDelay)
  }

  test("timestampField and delay param are null") {
    val topic = "topic-empty-parameters"
    val process = createProcessWithDelayedSource(topic, BasicEvent.definition, "null", "null")
    runAndVerify(topic, process, givenObj())
  }

  test("handle not exist timestamp field param") {
    val topic = "topic-invalid-timestamp-field"
    val process = createProcessWithDelayedSource(topic, BasicEvent.definition, "'unknownField'", "10L")
    intercept[IllegalArgumentException] {
      runAndVerify(topic, process, givenObj())
    }.getMessage should include ("Field: 'unknownField' doesn't exist in definition: id,name,timestamp.")
  }

  test("handle invalid type of timestamp field") {
    val topic = "topic-invalid-timestamp-type"
    val process = createProcessWithDelayedSource(topic, BasicEvent.definition, "'name'", "10L")
    intercept[IllegalArgumentException] {
      runAndVerify(topic, process, givenObj())
    }.getMessage should include ("Field: 'name' has invalid type: String.")
  }

  test("null timestamp should raise exception") {
    val testProcessObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val sourceFactory = creator.sourceFactories(testProcessObjectDependencies)("kafka-generic-delayed").value.asInstanceOf[DelayedGenericTypedJsonSourceFactory]
    val recordOk = new ConsumerRecord[String, TypedMap]("dummy", 1, 1L, "", TypedMap(Map("msisdn" -> "abc", "ts" -> 456L)))
    sourceFactory.extractTimestampFromField("ts").extractTimestamp(recordOk, 123L) shouldEqual 456L

    val recordWithNull = new ConsumerRecord[String, TypedMap]("dummy", 1, 1L, "", TypedMap(Map("msisdn" -> "abc", "ts" -> null)))
    sourceFactory.extractTimestampFromField("ts").extractTimestamp(recordWithNull, 123L) shouldEqual 0L

  }

  test("handle invalid negative param") {
    val topic = "topic-invalid-delay"
    val process = createProcessWithDelayedSource(topic, BasicEvent.definition, s"'${BasicEvent.timestampFieldName}'", "-10L")
    intercept[IllegalArgumentException] {
      runAndVerify(topic, process, givenObj())
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
      .emptySink("out", "sinkForLongs", "value" -> "T(java.time.Instant).now().toEpochMilli()")
  }

  private def runAndVerify(topic: String, process: EspProcess, givenObj: AnyRef, timestamp: Long = now): Unit = {
    createTopic(topic)
    pushMessage(serializationSchema(topic), givenObj, topic, timestamp = timestamp)
    run(process) {
      eventually {
        recordingExceptionHandler.data shouldBe empty
        SinkForLongs.data should have size 1
      }
    }
  }

}

class DelayedGenericProcessConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "kafka-generic-delayed" -> defaultCategory(new DelayedGenericTypedJsonSourceFactory(FixedRecordFormatterFactoryWrapper(JsonRecordFormatter), processObjectDependencies, None))
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sinkForLongs" -> defaultCategory(SinkForLongs.toSourceFactory)
    )
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(_ => recordingExceptionHandler)

  protected def defaultCategory[T](obj: T): WithCategories[T] = WithCategories(obj, "TestDelayedSource")

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    super.expressionConfig(processObjectDependencies).copy(additionalClasses = List(classOf[Instant]))
  }
}
