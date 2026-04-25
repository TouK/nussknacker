package pl.touk.nussknacker.engine.management.savepoint

import scala.concurrent.Future

class IdentitySavepointLocator extends FlinkSavepointLocator {

  override def locateSavepoint(path: String): Future[FlinkSavepointLocator.Savepoint] =
    Future.successful(FlinkSavepointLocator.LocalSavepoint(path))

}
