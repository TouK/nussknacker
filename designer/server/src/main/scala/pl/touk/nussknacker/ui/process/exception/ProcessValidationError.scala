package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.ui.BadRequestError

case class ProcessValidationError(message: String) extends Exception(message) with BadRequestError

