package pl.touk.nussknacker.ui.customhttpservice

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.customhttpservice.CustomHttpServiceProviderFactory.CustomHttpServiceProviderCreator
import pl.touk.nussknacker.ui.customhttpservice.services.{NussknackerDomainServices, NussknackerInfrastructureServices}

trait CustomHttpServiceProviderFactory {

  def name: String

  def creator: CustomHttpServiceProviderCreator

}

object CustomHttpServiceProviderFactory {
  sealed trait CustomHttpServiceProviderCreator

  object CustomHttpServiceProviderCreator {

    trait WithoutDependencies extends CustomHttpServiceProviderCreator {

      def create(
          config: Config,
      ): Resource[IO, CustomHttpServiceProvider]

    }

    trait WithInfrastructureDependencies extends CustomHttpServiceProviderCreator {

      def create(
          config: Config,
          infrastructureServices: NussknackerInfrastructureServices,
      ): Resource[IO, CustomHttpServiceProvider]

    }

    trait WithInfrastructureAndDomainDependencies extends CustomHttpServiceProviderCreator {

      def create(
          config: Config,
          infrastructureServices: NussknackerInfrastructureServices,
          domainServices: NussknackerDomainServices,
      ): Resource[IO, CustomHttpServiceProvider]

    }

  }

}
