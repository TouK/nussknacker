package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import cats.data.NonEmptyList
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, TopicName}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.{CorrectExceptionHandlingSpec, FlinkSpec}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.FullNameV1
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  MockSchemaRegistryClientFactory,
  UniversalSchemaBasedSerdeProvider
}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.spel.SpelExtension._

class KafkaUniversalSinkExceptionHandlingSpec
    extends AnyFunSuite
    with FlinkSpec
    with Matchers
    with SchemaRegistryMixin
    with KafkaAvroSinkSpecMixin
    with CorrectExceptionHandlingSpec {

  private val topic = TopicName.ForSink("topic1")

  override protected def schemaRegistryClient: SchemaRegistryClient = schemaRegistryMockClient

  override protected def runScenario(
      env: StreamExecutionEnvironment,
      modelData: ModelData,
      scenario: CanonicalProcess
  ): JobExecutionResult = new FlinkScenarioUnitTestJob(modelData).run(scenario, env)

  test("should handle exceptions in kafka sinks") {
    registerSchema(topic.toUnspecialized, FullNameV1.schema, isKey = false)

    val schemaRegistryClientFactory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
    val universalProvider           = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory)
    val kafkaComponent = new UniversalKafkaSinkFactory(
      schemaRegistryClientFactory,
      universalProvider,
      ProcessObjectDependencies.withConfig(config),
      FlinkKafkaUniversalSinkImplFactory
    )

    checkExceptions(List(ComponentDefinition("kafka", kafkaComponent))) { case (graph, generator) =>
      NonEmptyList.one(
        graph.split(
          "split",
          GraphBuilder.emptySink(
            "avro-raw",
            "kafka",
            topicParamName.value              -> s"'${topic.name}'".spel,
            schemaVersionParamName.value      -> "'1'".spel,
            sinkValueParamName.value          -> s"""{first: 'Test', last: (${generator.throwFromString()})}""".spel,
            sinkKeyParamName.value            -> generator.throwFromString().spel,
            sinkRawEditorParamName.value      -> s"true".spel,
            sinkValidationModeParamName.value -> s"'${ValidationMode.strict.name}'".spel
          ),
          GraphBuilder.emptySink(
            "avro",
            "kafka",
            topicParamName.value         -> s"'${topic.name}'".spel,
            schemaVersionParamName.value -> "'1'".spel,
            sinkKeyParamName.value       -> generator.throwFromString().spel,
            sinkRawEditorParamName.value -> s"false".spel,
            "first"                      -> generator.throwFromString().spel,
            "last"                       -> generator.throwFromString().spel
          ),
        )
      )
    }

  }

}
