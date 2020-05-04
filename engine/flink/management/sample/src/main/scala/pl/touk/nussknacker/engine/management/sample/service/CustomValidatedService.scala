package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomServiceValidationError
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.typed.{CustomParameterValidationException, CustomServiceValidationException, ServiceReturningType}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.concurrent.Future

class CustomValidatedService extends Service with ServiceReturningType {

  private val templates = Map(
    1 -> "Hello {name}",
    2 -> "Buenos Dias {name}"
  )

  private val param = "\\{(\\w+)}".r

  @MethodToInvoke
  def invoke(@ParamName("index") index: Int,
             @ParamName("fields") fields: java.util.Map[String, String]): Future[String] = {
    Future.successful(param.replaceAllIn(templates(index), { matcher =>
      fields.get(matcher.group(1))
    }))
  }

  def returnType(params: Map[String, (TypingResult, Option[Any])]): TypingResult = {
    val index = params("index")._2.map(_.asInstanceOf[Int]).getOrElse(throw new IllegalArgumentException("Missing index"))
    if (!templates.contains(index)) {
      throw CustomParameterValidationException(s"No template for index = $index", "Not found", "index")
    } else {
      val templateParams = param.findAllMatchIn(templates(index)).map(_.group(1)).toSet
      val (fieldsType, _) = params("fields")
      fieldsType match {
        case TypedObjectTypingResult(fields, _) =>
          val missingFields = templateParams.diff(fields.keys.toSet)
          val nonStringFields = fields.values.filter(_ != Typed.typedClass[String])
          if (missingFields.nonEmpty) {
            throw CustomServiceValidationException("Missing fields: " + missingFields.mkString(", "))
          } else if (nonStringFields.nonEmpty) {
            throw CustomParameterValidationException("Every value should be a string, please fix the following: " + nonStringFields.mkString(", "), "", "fields")
          } else {
            Typed.typedClass[String]
          }
        case TypedObjectTypingResult(fields, _) if fields.values.exists(_ != Typed.typedClass[String]) =>
          throw CustomServiceValidationException("All values of map must be strings")
        case _ =>
          throw new IllegalArgumentException("Unexpected type")
      }
    }
  }
}
