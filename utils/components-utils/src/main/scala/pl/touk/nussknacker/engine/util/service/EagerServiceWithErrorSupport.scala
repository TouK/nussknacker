package pl.touk.nussknacker.engine.util.service

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedValuesParameterEditor,
  Parameter,
  WithExplicitTypesToExtract
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

class EagerServiceWithErrorSupport[T: ClassTag] extends EagerService with WithExplicitTypesToExtract {

  protected def responseTypeWithError(successResponseType: typing.TypingResult): typing.TypingResult = Typed.record(
    Map(
      "error"           -> Typed.typedClass(classOf[Boolean]),
      "errorResponse"   -> Typed.typedClass(classOf[String]),
      "statusCode"      -> Typed.typedClass(classOf[java.lang.Integer]),
      "successResponse" -> successResponseType,
    )
  )

  override def typesToExtract: List[typing.TypingResult] =
    List(Typed[T], Typed.typedClass[ServiceResponseWithError[T]])
}

object EagerServiceWithErrorSupport {
  // Internal name avoids collisions with real component/API params, while UI still presents it as `handleErrors`.
  final val HandleErrorsParamName     = ParameterName("nkHandleErrors")
  private final val HandleErrorsLabel = "handleErrors"

  val handleErrorsParam: Parameter = Parameter[Boolean](HandleErrorsParamName).copy(
    defaultValue = Some(Expression.spel("false")),
    editors = List(
      FixedValuesParameterEditor(
        List(
          FixedExpressionValue("false", "false"),
          FixedExpressionValue("true", "true")
        )
      )
    ),
    labelOpt = Some(HandleErrorsLabel)
  )

}

sealed trait ReturnErrors[X] {
  type Out
}

object ReturnErrors {

  sealed class WithErrors[X] extends ReturnErrors[X] {
    override type Out = ServiceResponseWithError[X]
    def asOut(x: ServiceResponseWithError[X]): Out = x
  }

  sealed class NoErrors[X] extends ReturnErrors[X] {
    override type Out = X
    def asOut(x: X): Out = x
  }

  def fromBoolean[X](returnErrors: Boolean): ReturnErrors[X] = {
    if (returnErrors) new WithErrors[X]
    else new NoErrors[X]
  }

  def handle[T](
      returnErrors: ReturnErrors[T],
      invokeResult: Future[T],
      errorDescription: Throwable => String = defaultErrorDescription,
      errorStatusCode: Throwable => Option[java.lang.Integer] = (_: Throwable) => None
  )(
      implicit ec: ExecutionContext
  ): Future[ReturnErrors[T]#Out] =
    handleWithStatus(
      returnErrors = returnErrors,
      invokeResult = invokeResult.map(_ -> None),
      errorDescription = errorDescription,
      errorStatusCode = errorStatusCode
    )

  def handleWithStatus[T](
      returnErrors: ReturnErrors[T],
      invokeResult: Future[(T, Option[java.lang.Integer])],
      errorDescription: Throwable => String = defaultErrorDescription,
      errorStatusCode: Throwable => Option[java.lang.Integer] = (_: Throwable) => None
  )(
      implicit ec: ExecutionContext
  ): Future[ReturnErrors[T]#Out] = {
    returnErrors match {
      case noErrors: NoErrors[T] =>
        invokeResult.map { case (result, _) => noErrors.asOut(result) }
      case withErrors: WithErrors[T] =>
        invokeResult
          .map { case (result, statusCode) => ServiceResponseWithError.success[T](result, statusCode = statusCode) }
          .recoverWith {
            case NonFatal(error) =>
              Future.successful(
                ServiceResponseWithError.error[T](
                  errorMessage = errorDescription(error),
                  statusCode = errorStatusCode(error)
                )
              )
            case other => Future.failed(other)
          }
          .map(withErrors.asOut)
    }
  }

  private def defaultErrorDescription(error: Throwable): String = {
    if (error.getCause == null) {
      s"error: ${error.getMessage}"
    } else {
      s"error: ${error.getMessage}. ${error.getCause.getMessage}"
    }
  }

}

case class ServiceResponseWithError[R] private (
    error: Boolean,
    errorResponse: Option[String],
    successResponse: Option[R],
    statusCode: Option[java.lang.Integer]
)

object ServiceResponseWithError {

  def error[R](
      errorMessage: String,
      statusCode: Option[java.lang.Integer] = None
  ): ServiceResponseWithError[R] =
    ServiceResponseWithError(error = true, Some(errorMessage), None, statusCode = statusCode)

  def success[R](response: R, statusCode: Option[java.lang.Integer] = None): ServiceResponseWithError[R] =
    ServiceResponseWithError(error = false, None, Some(response), statusCode = statusCode)

}
