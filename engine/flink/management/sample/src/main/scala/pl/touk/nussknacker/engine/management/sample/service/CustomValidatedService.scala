package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, OutputVar}
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api._

import scala.concurrent.{ExecutionContext, Future}

class CustomValidatedService extends EagerService {

  @MethodToInvoke
  def invoke(@ParamName("age") age: Int,
             @ParamName("fields") fields: LazyParameter[java.util.Map[String, String]],
             @OutputVariableName varName: String)(implicit nodeId: NodeId): ContextTransformation = {
    def returnType: TypingResult = {
      if (age < 18) {
        throw CustomNodeValidationException("Too young", Some("age"))
      }
      fields.returnType match {
        case TypedObjectTypingResult(fields, _, _) if fields.contains("invalid") =>
          throw CustomNodeValidationException("Service is invalid", None)
        case TypedObjectTypingResult(fields, _, _) if fields.values.exists(_ != Typed.typedClass[String]) =>
          throw CustomNodeValidationException("All of fields values should be strings", Some("fields"))
        case TypedObjectTypingResult(fields, _, _) if !fields.keys.exists(_ == "name") =>
          throw CustomNodeValidationException("Missing name", Some("fields"))
        case _ => Typed.typedClass[String]
      }
    }

    ContextTransformation.definedBy(_.withVariable(OutputVar.variable(varName), returnType)).implementedBy(new ServiceInvoker {
      override def invokeService(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                           collector: InvocationCollectors.ServiceInvocationCollector,
                                                           contextId: ContextId,
                                                           runMode: RunMode): Future[Any] = {
        Future.successful(s"name: ${params("fields").asInstanceOf[java.util.Map[String, String]].get("name")}, age: $age")
      }
    })
  }


}
