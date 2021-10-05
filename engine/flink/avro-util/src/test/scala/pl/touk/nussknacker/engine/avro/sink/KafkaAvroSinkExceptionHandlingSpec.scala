package pl.touk.nussknacker.engine.avro.sink

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, WithCategories}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer
import pl.touk.nussknacker.engine.avro.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.avro.schema.FullNameV1
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.test.{CorrectExceptionHandlingSpec, FlinkSpec, MiniClusterExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.runner.TestFlinkRunner
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class KafkaAvroSinkExceptionHandlingSpec extends FunSuite with FlinkSpec with Matchers with SchemaRegistryMixin with KafkaAvroSinkSpecMixin with CorrectExceptionHandlingSpec {

  private val topic = "topic1"

  override protected def schemaRegistryClient: SchemaRegistryClient = schemaRegistryMockClient

  override protected def registerInEnvironment(env: MiniClusterExecutionEnvironment, modelData: ModelData, scenario: EspProcess): Unit
  = TestFlinkRunner.registerInEnvironmentWithModel(env, modelData)(scenario)

  test("should handle exceptions in kafka sinks") {
    registerSchema(topic, FullNameV1.schema, isKey = false)

    val configCreator = new EmptyProcessConfigCreator {

      override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
        val provider = ConfluentSchemaRegistryProvider(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))
        Map(
          "kafka-avro" -> WithCategories(new KafkaAvroSinkFactoryWithEditor(provider, processObjectDependencies)),
          "kafka-avro-raw" -> WithCategories(new KafkaAvroSinkFactory(provider, processObjectDependencies)),
        )
      }

    }

    checkExceptions(configCreator, prepareConfig) { case (graph, generator) =>
      graph.split("split",
        GraphBuilder.emptySink("avro-raw",
          "kafka-avro-raw",
          KafkaAvroBaseTransformer.TopicParamName -> s"'$topic'",
          KafkaAvroBaseTransformer.SchemaVersionParamName -> "'1'",
          KafkaAvroBaseTransformer.SinkValueParamName -> s"""{first: 'Test', last: (${generator.throwFromString()})}""",
          KafkaAvroBaseTransformer.SinkKeyParamName -> generator.throwFromString(),
          KafkaAvroBaseTransformer.SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'"
        ),
        GraphBuilder.emptySink("avro",
          "kafka-avro",
          KafkaAvroBaseTransformer.TopicParamName -> s"'$topic'",
          KafkaAvroBaseTransformer.SchemaVersionParamName -> "'1'",
          KafkaAvroBaseTransformer.SinkKeyParamName -> generator.throwFromString(),
          "first" -> generator.throwFromString(),
          "last" -> generator.throwFromString()
        ),
      )
    }

  }

}


