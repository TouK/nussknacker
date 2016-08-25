package pl.touk.esp.engine.marshall

import argonaut.PrettyParams
import cats.data._
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessUnmarshallError._

class ValidatingProcessMarshaller(validator: ProcessValidator) {

  import cats.instances.list._

  def toJson(node: EspProcess, prettyParams: PrettyParams) : String =
    ProcessMarshaller.toJson(node, prettyParams)

  def toJson(canonical: CanonicalProcess, prettyParams: PrettyParams): String =
    ProcessMarshaller.toJson(canonical, prettyParams)

  def decode(json: String): Validated[ProcessJsonDecodeError, CanonicalProcess] =
    ProcessMarshaller.fromJson(json)

  def fromJson(json: String): ValidatedNel[ProcessUnmarshallError, CanonicalProcess] = {
    ProcessMarshaller.fromJson(json).toValidatedNel[ProcessUnmarshallError, CanonicalProcess] andThen { canonical =>
      validator.validate(canonical).leftMap(_.map[ProcessUnmarshallError](ProcessCompilationError)).map { _ =>
        canonical
      }
    }
  }

}

object ValidatingProcessMarshaller {

  def default(definition: ProcessDefinition) = {
    new ValidatingProcessMarshaller(ProcessValidator.default(definition))
  }

}