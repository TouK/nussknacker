package pl.touk.nussknacker.ui.server

import cats.effect.IO
import cats.effect.kernel.Resource
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.custominitializer.CustomInitializerFactory
import pl.touk.nussknacker.ui.factory.InfrastructureServices

object CustomInitializerLoader {

  def loadCustomInitializer(
      designerConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices
  ): Resource[IO, Unit] = for {
    customInitializerFactory <- Resource.eval(loadCustomInitializerFactories)
    _ <- customInitializerFactory match {
      case Some(value) => value.create(designerConfig.rawConfig, infrastructureServices.dbRef)
      case None        => Resource.unit[IO]
    }
  } yield ()

  private def loadCustomInitializerFactories: IO[Option[CustomInitializerFactory]] = {
    IO {
      Multiplicity(
        ScalaServiceLoader.load[CustomInitializerFactory](getClass.getClassLoader)
      ) match {
        case Empty() =>
          None
        case One(customInitializerFactory) =>
          Some(customInitializerFactory)
        case Many(moreThanOne) =>
          throw new IllegalArgumentException(
            s"There can only be one CustomInitializerFactory, found ${moreThanOne.map(_.name)}"
          )
      }
    }
  }

}
