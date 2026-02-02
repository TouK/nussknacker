package pl.touk.nussknacker.engine.util.output

object OutputValidatorErrorsMessageFormatter {

  val TypesSeparator = " | "

  private val ValidationErrorMessageBase            = "Provided value does not match scenario output"
  private val ValidationRangeMessage                = "Provided value is out of range"
  private val ValidationRedundantFieldsErrorMessage = "Redundant fields"
  private val ValidationMissingFieldsErrorMessage   = "Missing fields"
  private val ValidationTypeErrorMessage            = "Incorrect type"
  private val ValidationValueErrorMessage           = "Incorrect value"

  private def makeErrors(messages: List[String], baseMessage: String): List[String] =
    if (messages.nonEmpty) messages.mkString(s"$baseMessage: ", ", ", ".") :: Nil else Nil

  def makeMessage(
      outputErrors: OutputErrors,
      detailedErrorDescriptions: Boolean
  ): String = {
    val messageMissingFieldsError = makeErrors(outputErrors.missingFieldNames, ValidationMissingFieldsErrorMessage)
    val messageValueFieldsError =
      makeErrors(outputErrors.valueErrorMessages(detailedErrorDescriptions), ValidationValueErrorMessage)
    val messageRedundantFieldsError =
      makeErrors(outputErrors.redundantFieldNames, ValidationRedundantFieldsErrorMessage)
    val messageTypeFieldErrors =
      makeErrors(outputErrors.typeErrorMessages(detailedErrorDescriptions), ValidationTypeErrorMessage)
    val messageTypeFieldRangeErrors =
      makeErrors(outputErrors.rangeErrorMessages(detailedErrorDescriptions), ValidationRangeMessage)
    val messageErrors =
      messageTypeFieldErrors ::: messageValueFieldsError ::: messageMissingFieldsError ::: messageRedundantFieldsError ::: messageTypeFieldRangeErrors

    val messageBase = if (detailedErrorDescriptions) s"$ValidationErrorMessageBase - errors:\n" else "Errors:\n"

    messageErrors.mkString(messageBase, ", ", "")

  }

}
