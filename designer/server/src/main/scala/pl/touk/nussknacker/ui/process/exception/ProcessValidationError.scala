package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.ui.BadRequestError

final case class ProcessValidationError(message: String) extends Exception(message) with BadRequestError
