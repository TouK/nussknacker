package pl.touk.nussknacker.engine.kafka.source.flink

import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, StaticMethodInfo}
import KafkaSourceFactoryMixin.{SampleKey, SampleValue}
import pl.touk.nussknacker.engine.api.generics.ParameterList

class KafkaSourceFactoryDefinitionExtractorSpec extends KafkaSourceFactoryProcessMixin {

  test("should extract valid type definitions from source based on GenericNodeTransformation with explicit type definitions") {
    val extractedTypes = extractTypes(processDefinition)

    // Here we are checking explicit type extraction for sources based on GenericNodeTransformation
    // with defined explicit type extraction.
    // It is important that SampleKey and SampleValue are used only by source of that kind,
    // and they must not be returned by other services.
    extractedTypes should contain allOf (
      ClazzDefinition(Typed.genericTypeClass(classOf[SampleKey],Nil), Map(
        "partOne" -> List(StaticMethodInfo(ParameterList(Nil, None), Typed[String], "partOne", None)),
        "partTwo" -> List(StaticMethodInfo(ParameterList(Nil, None), Typed[Long], "partTwo", None)),
        "toString" -> List(StaticMethodInfo(ParameterList(Nil, None), Typed[String], "toString", None))
      ), Map.empty),
      ClazzDefinition(Typed.genericTypeClass(classOf[SampleValue],Nil), Map(
        "id" -> List(StaticMethodInfo(ParameterList(Nil, None), Typed[String], "id", None)),
        "field" -> List(StaticMethodInfo(ParameterList(Nil, None), Typed[String], "field", None)),
        "toString" -> List(StaticMethodInfo(ParameterList(Nil, None), Typed[String], "toString", None))
      ), Map.empty)
    )
  }

}
