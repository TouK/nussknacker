package pl.touk.nussknacker.ui.security.dummy

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration

import java.net.URI

object DummyAuthenticationConfiguration {

  import AuthenticationConfiguration._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  def create(config: Config): DummyAuthenticationConfiguration =
    config.as[DummyAuthenticationConfiguration](authenticationConfigPath)
}

final case class DummyAuthenticationConfiguration(override val anonymousUserRole: Option[String])
    extends AuthenticationConfiguration {
  override val name: String = "Dummy"

  override def usersFile: URI = throw new IllegalStateException(
    "There is no users file in case of Dummy authentication"
  )

}
