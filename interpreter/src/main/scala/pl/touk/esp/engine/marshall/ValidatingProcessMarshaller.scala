package pl.touk.esp.engine.marshall

import argonaut.PrettyParams
import cats.data._
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessUnmarshallError.ProcessJsonDecodeError
import pl.touk.esp.engine.marshall.ProcessValidationError._

class ValidatingProcessMarshaller(validator: ProcessValidator) {

  import cats.instances.list._

  def toJson(node: EspProcess, prettyParams: PrettyParams) : String =
    ProcessMarshaller.toJson(node, prettyParams)

  def toJson(canonical: CanonicalProcess, prettyParams: PrettyParams): String =
    ProcessMarshaller.toJson(canonical, prettyParams)

  def decode(json: String): Validated[ProcessJsonDecodeError, CanonicalProcess] =
    ProcessMarshaller.decode(json)

  def fromJson(json: String): ValidatedNel[ProcessUnmarshallError, EspProcess] = {
    ProcessMarshaller.decode(json).toValidatedNel[ProcessUnmarshallError, CanonicalProcess] andThen { canonical =>
      validate(canonical).leftMap(_.map[ProcessUnmarshallError](identity))
    }
  }

  def validate(canonical: CanonicalProcess): ValidatedNel[ProcessValidationError, EspProcess] = {
    ProcessCanonizer.uncanonize(canonical).leftMap(_.map[ProcessValidationError](ProcessUncanonizationError)) andThen { unflatten =>
      validator.validate(unflatten).map(_ => unflatten).leftMap(_.map[ProcessValidationError](ProcessCompilationError))
    }
  }

}

object ValidatingProcessMarshaller {

  def default(definition: ProcessDefinition) = {
    new ValidatingProcessMarshaller(ProcessValidator.default(definition))
  }

}