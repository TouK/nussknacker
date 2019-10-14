package pl.touk.nussknacker.restmodel.process

import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

package object repository {
  case class ProcessNotFoundError(id: String) extends Exception(s"No process $id found") with NotFoundError

  case class ProcessAlreadyExists(id: String) extends BadRequestError {
    def getMessage = s"Process $id already exists"
  }

  case class ProcessAlreadyDeployed(id: String) extends BadRequestError {
    def getMessage = s"Process $id is already deployed"
  }

  case class InvalidProcessTypeError(id: String) extends BadRequestError {
    def getMessage = s"Process $id is not GraphProcess"
  }
}
