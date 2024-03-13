package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import cats.data.NonEmptyList
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.{CorrectExceptionHandlingSpec, FlinkSpec, MiniClusterExecutionEnvironment}
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.FullNameV1
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  MockSchemaRegistryClientFactory,
  UniversalSchemaBasedSerdeProvider
}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.spel.Implicits._

class KafkaUniversalSinkExceptionHandlingSpec
    extends AnyFunSuite
    with FlinkSpec
    with Matchers
    with SchemaRegistryMixin
    with KafkaAvroSinkSpecMixin
    with CorrectExceptionHandlingSpec {

  private val topic = "topic1"

  override protected def schemaRegistryClient: SchemaRegistryClient = schemaRegistryMockClient

  override protected def registerInEnvironment(
      env: MiniClusterExecutionEnvironment,
      modelData: ModelData,
      scenario: CanonicalProcess
  ): Unit = UnitTestsFlinkRunner.registerInEnvironmentWithModel(env, modelData)(scenario)

  test("should handle exceptions in kafka sinks") {
    registerSchema(topic, FullNameV1.schema, isKey = false)

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
            TopicParamName.value              -> s"'$topic'",
            SchemaVersionParamName.value      -> "'1'",
            SinkValueParamName.value          -> s"""{first: 'Test', last: (${generator.throwFromString()})}""",
            SinkKeyParamName.value            -> generator.throwFromString(),
            SinkRawEditorParamName.value      -> s"true",
            SinkValidationModeParamName.value -> s"'${ValidationMode.strict.name}'"
          ),
          GraphBuilder.emptySink(
            "avro",
            "kafka",
            TopicParamName.value         -> s"'$topic'",
            SchemaVersionParamName.value -> "'1'",
            SinkKeyParamName.value       -> generator.throwFromString(),
            SinkRawEditorParamName.value -> s"false",
            "first"                      -> generator.throwFromString(),
            "last"                       -> generator.throwFromString()
          ),
        )
      )
    }

  }

}
