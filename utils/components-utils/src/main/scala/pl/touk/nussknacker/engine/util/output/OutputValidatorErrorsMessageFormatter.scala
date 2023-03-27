package pl.touk.nussknacker.engine.util.output

object OutputValidatorErrorsMessageFormatter {

  val TypesSeparator = " | "

  private val ValidationErrorMessageBase = "Provided value does not match scenario output"
  private val ValidationRangeMessage = "Provided value is out of range"
  private val ValidationRedundantFieldsErrorMessage = "Redundant fields"
  private val ValidationMissingFieldsErrorMessage = "Missing fields"
  private val ValidationTypeErrorMessage = "Incorrect type"

  private def makeErrors(messages: List[String], baseMessage: String): List[String] =
    if (messages.nonEmpty) messages.mkString(s"$baseMessage: ", ", ", ".") :: Nil else Nil

  def makeMessage(typeFieldErrors: List[String], missingFieldsError: List[String], redundantFieldsError: List[String], typeFieldRangeErrors: List[String]): String = {
    val messageMissingFieldsError = makeErrors(missingFieldsError, ValidationMissingFieldsErrorMessage)
    val messageRedundantFieldsError = makeErrors(redundantFieldsError, ValidationRedundantFieldsErrorMessage)
    val messageTypeFieldErrors = makeErrors(typeFieldErrors, ValidationTypeErrorMessage)
    val messageTypeFieldRangeErrors = makeErrors(typeFieldRangeErrors, ValidationRangeMessage)
    val messageErrors = messageTypeFieldErrors ::: messageMissingFieldsError ::: messageRedundantFieldsError ::: messageTypeFieldRangeErrors

    messageErrors.mkString(s"$ValidationErrorMessageBase - errors:\n", ", ", "")

  }
}
