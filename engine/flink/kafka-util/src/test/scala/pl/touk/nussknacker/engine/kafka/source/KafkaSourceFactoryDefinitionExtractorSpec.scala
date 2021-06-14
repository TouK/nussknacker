package pl.touk.nussknacker.engine.kafka.source

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedUnion}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin.{SampleKey, SampleValue}

class KafkaSourceFactoryDefinitionExtractorSpec extends KafkaSourceFactoryProcessMixin {

  test("should extract valid type definitions from source based on GenericNodeTransformation with explicit type definitions") {
    val definition = processDefinition.sourceFactories("kafka-jsonKeyJsonValueWithMeta")
    definition.objectDefinition.returnType shouldEqual TypedUnion(Set(
      Typed.typedClass[SampleKey], Typed.typedClass[SampleValue]
    ))
  }

}