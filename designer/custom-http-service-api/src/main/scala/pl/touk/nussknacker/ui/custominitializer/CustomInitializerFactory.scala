package pl.touk.nussknacker.ui.custominitializer

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.db.DbRef

trait CustomInitializerFactory {

  def name: String

  def create(
      config: Config,
      dbRef: DbRef,
  ): Resource[IO, Unit]

}
