package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, SinkFactory, WithCategories}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.encode.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.schemedkafka.schema.FullNameV1
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.test.{CorrectExceptionHandlingSpec, FlinkSpec, MiniClusterExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.runner.TestFlinkRunner
import pl.touk.nussknacker.engine.spel.Implicits._

class KafkaUniversalSinkExceptionHandlingSpec extends FunSuite with FlinkSpec with Matchers with SchemaRegistryMixin with KafkaAvroSinkSpecMixin with CorrectExceptionHandlingSpec {

  private val topic = "topic1"

  override protected def schemaRegistryClient: SchemaRegistryClient = schemaRegistryMockClient

  override protected def registerInEnvironment(env: MiniClusterExecutionEnvironment, modelData: ModelData, scenario: EspProcess): Unit
  = TestFlinkRunner.registerInEnvironmentWithModel(env, modelData)(scenario)

  test("should handle exceptions in kafka sinks") {
    registerSchema(topic, FullNameV1.schema, isKey = false)

    val configCreator = new EmptyProcessConfigCreator {

      override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
        val schemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient)
        val universalProvider = ConfluentSchemaBasedSerdeProvider.universal(schemaRegistryClientFactory)
        Map(
          "kafka" -> WithCategories(new UniversalKafkaSinkFactory(schemaRegistryClientFactory, universalProvider, processObjectDependencies, FlinkKafkaUniversalSinkImplFactory)),
        )
      }
    }

    checkExceptions(configCreator) { case (graph, generator) =>
      graph.split("split",
        GraphBuilder.emptySink("avro-raw",
          "kafka",
          TopicParamName -> s"'$topic'",
          SchemaVersionParamName -> "'1'",
          SinkValueParamName -> s"""{first: 'Test', last: (${generator.throwFromString()})}""",
          SinkKeyParamName -> generator.throwFromString(),
          SinkRawEditorParamName -> s"true",
          SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'"
        ),
        GraphBuilder.emptySink("avro",
          "kafka",
          TopicParamName -> s"'$topic'",
          SchemaVersionParamName -> "'1'",
          SinkKeyParamName -> generator.throwFromString(),
          SinkRawEditorParamName -> s"false",
          "first" -> generator.throwFromString(),
          "last" -> generator.throwFromString()
        ),
      )
    }

  }

}


