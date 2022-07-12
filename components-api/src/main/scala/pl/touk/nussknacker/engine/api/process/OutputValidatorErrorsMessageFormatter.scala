package pl.touk.nussknacker.engine.api.process

object OutputValidatorErrorsMessageFormatter {

  val TypesSeparator = " | "

  private val ValidationErrorMessageBase = "Provided value does not match scenario output"
  private val ValidationRedundantFieldsErrorMessage = "Redundant fields"
  private val ValidationMissingFieldsErrorMessage = "Missing fields"
  private val ValidationTypeErrorMessage = "Type validation"

  private def makeErrors(messages: List[String], baseMessage: String): List[String] =
    if (messages.nonEmpty) messages.mkString(s"$baseMessage: ", ", ", ".") :: Nil else Nil

  def makeMessage(typeFieldErrors: List[String], missingFieldsError: List[String], redundantFieldsError: List[String]): String = {
    val messageMissingFieldsError = makeErrors(missingFieldsError, ValidationMissingFieldsErrorMessage)
    val messageRedundantFieldsError = makeErrors(redundantFieldsError, ValidationRedundantFieldsErrorMessage)
    val messageTypeFieldErrors = makeErrors(typeFieldErrors, ValidationTypeErrorMessage)
    val messageErrors = messageTypeFieldErrors ::: messageMissingFieldsError ::: messageRedundantFieldsError

    messageErrors.mkString(s"$ValidationErrorMessageBase - errors:\n", ", ", "")

  }
}
