package pl.touk.nussknacker.engine.management.sample.service

import cats.data.Validated.Valid
import cats.data.{Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.util.service.EnricherContextTransformation

import scala.concurrent.{ExecutionContext, Future}

class CustomValidatedService extends EagerService {

  @MethodToInvoke
  def invoke(
      @ParamName("age") age: Int,
      @ParamName("fields") fields: LazyParameter[java.util.Map[String, String]],
      @OutputVariableName varName: String
  )(implicit nodeId: NodeId): ContextTransformation = {

    def returnType: ValidatedNel[ProcessCompilationError, TypingResult] = {
      if (age < 18) {
        Validated.invalidNel(CustomNodeError("Too young", Some(ParameterName("age"))))
      } else {
        fields.returnType match {
          case TypedObjectTypingResult(fields, _, _) if fields.contains("invalid") =>
            Validated.invalidNel(CustomNodeError("Service is invalid", None))
          case TypedObjectTypingResult(fields, _, _) if fields.values.exists(_ != Typed.typedClass[String]) =>
            Validated.invalidNel(CustomNodeError("All values should be strings", Some(ParameterName("fields"))))
          case _ => Valid(Typed.typedClass[String])
        }
      }
    }

    EnricherContextTransformation(
      varName,
      returnType,
      new ServiceInvoker {
        override def invoke(context: Context)(
            implicit ec: ExecutionContext,
            collector: InvocationCollectors.ServiceInvocationCollector,
            componentUseContext: ComponentUseContext,
        ): Future[Any] = {
          Future.successful(
            s"name: ${fields.evaluate(context).get("name")}, age: $age"
          )
        }
      }
    )
  }

}
