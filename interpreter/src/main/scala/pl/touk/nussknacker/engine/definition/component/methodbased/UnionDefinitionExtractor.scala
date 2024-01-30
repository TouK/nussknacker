package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}

import java.lang.reflect.Method

private[definition] class UnionDefinitionExtractor[T](seq: List[MethodDefinitionExtractor[T]])
    extends MethodDefinitionExtractor[T] {

  override def extractMethodDefinition(
      obj: T,
      methodToInvoke: Method,
      parametersConfig: Map[String, ParameterConfig]
  ): Either[String, MethodDefinition] = {
    val extractorsWithDefinitions = for {
      extractor  <- seq
      definition <- extractor.extractMethodDefinition(obj, methodToInvoke, parametersConfig).toOption
    } yield (extractor, definition)
    extractorsWithDefinitions match {
      case Nil =>
        Left(s"Missing method to invoke for object: " + obj)
      case head :: _ =>
        val (_, definition) = head
        Right(definition)
      case moreThanOne =>
        Left(s"More than one extractor: " + moreThanOne.map(_._1) + " handles given object: " + obj)
    }
  }

}
