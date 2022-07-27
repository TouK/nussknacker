package pl.touk.nussknacker.engine.kafka.source.flink

import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import KafkaSourceFactoryMixin.{SampleKey, SampleValue}

class KafkaSourceFactoryDefinitionExtractorSpec extends KafkaSourceFactoryProcessMixin {

  test("should extract valid type definitions from source based on GenericNodeTransformation with explicit type definitions") {
    val extractedTypes = extractTypes(processDefinition)

    // Here we are checking explicit type extraction for sources based on GenericNodeTransformation
    // with defined explicit type extraction.
    // It is important that SampleKey and SampleValue are used only by source of that kind,
    // and they must not be returned by other services.
    extractedTypes should contain allOf (
      ClazzDefinition(Typed.genericTypeClass(classOf[SampleKey],Nil), Map(
        "partOne" -> List(MethodInfo(Nil, Typed[String], "partOne", None, varArgs = false)),
        "partTwo" -> List(MethodInfo(Nil, Typed[Long], "partTwo", None, varArgs = false)),
        "toString" -> List(MethodInfo(Nil, Typed[String], "toString", None, varArgs = false))
      ), Map.empty),
      ClazzDefinition(Typed.genericTypeClass(classOf[SampleValue],Nil), Map(
        "id" -> List(MethodInfo(Nil, Typed[String], "id", None, varArgs = false)),
        "field" -> List(MethodInfo(Nil, Typed[String], "field", None, varArgs = false)),
        "toString" -> List(MethodInfo(Nil, Typed[String], "toString", None, varArgs = false))
      ), Map.empty)
    )
  }

}
