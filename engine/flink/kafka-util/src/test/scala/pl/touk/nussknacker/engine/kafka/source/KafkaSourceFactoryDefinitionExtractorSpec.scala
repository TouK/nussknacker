package pl.touk.nussknacker.engine.kafka.source

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin.{SampleKey, SampleValue}

class KafkaSourceFactoryDefinitionExtractorSpec extends KafkaSourceFactoryProcessMixin {

  test("should extract valid type definitions from source based on GenericNodeTransformation with explicit type definitions") {
    val extractedTypes = extractTypes(processDefinition)

    extractedTypes should contain allOf (
      ClazzDefinition(TypedClass(classOf[SampleKey],Nil), Map(
        "partOne" -> List(MethodInfo(Nil, Typed[String], None, varArgs = false)),
        "partTwo" -> List(MethodInfo(Nil, Typed[Long], None, varArgs = false)),
        "toString" -> List(MethodInfo(Nil, Typed[String], None, varArgs = false))
      )),
      ClazzDefinition(TypedClass(classOf[SampleValue],Nil), Map(
        "id" -> List(MethodInfo(Nil, Typed[String], None, varArgs = false)),
        "field" -> List(MethodInfo(Nil, Typed[String], None, varArgs = false)),
        "toString" -> List(MethodInfo(Nil, Typed[String], None, varArgs = false))
      ))
    )
  }

}