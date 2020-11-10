package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.typed.{CustomNodeValidationException, ServiceReturningType}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.concurrent.Future

class CustomValidatedService extends Service with ServiceReturningType {

  @MethodToInvoke
  def invoke(@ParamName("age") age: Int,
             @ParamName("fields") fields: java.util.Map[String, String]): Future[String] = {
    Future.successful(s"name: ${fields.get("name")}, age: $age")
  }

  def returnType(params: Map[String, (TypingResult, Option[Any])]): TypingResult = {
    if (params("age")._2.get.asInstanceOf[Int] < 18) {
      throw CustomNodeValidationException("Too young", Some("age"))
    }
    params("fields")._1 match {
      case TypedObjectTypingResult(fields, _, _) if fields.contains("invalid") =>
        throw CustomNodeValidationException("Service is invalid", None)
      case TypedObjectTypingResult(fields, _, _) if fields.values.exists(_ != Typed.typedClass[String]) =>
        throw CustomNodeValidationException("All of fields values should be strings", Some("fields"))
      case TypedObjectTypingResult(fields, _, _) if !fields.keys.exists(_ == "name") =>
        throw CustomNodeValidationException("Missing name", Some("fields"))
      case _ => Typed.typedClass[String]
    }
  }
}
