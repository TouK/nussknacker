package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.Config
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

trait EnrichDeploymentWithJarDataFactory {
  def apply(config: Config)(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): EnrichDeploymentWithJarData
}

object EnrichDeploymentWithJarDataFactory {
  def noOp: EnrichDeploymentWithJarDataFactory = new EnrichDeploymentWithJarDataFactory {
    override def apply(config: Config)(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): EnrichDeploymentWithJarData = {
      EnrichDeploymentWithJarData.noOp
    }
  }
}
