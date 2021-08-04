package pl.touk.nussknacker.ui.security.dummy

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration

object DummyAuthenticationConfiguration {

  import AuthenticationConfiguration._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  def create(config: Config): DummyAuthenticationConfiguration = config.as[DummyAuthenticationConfiguration](authenticationConfigPath)
}

case class DummyAuthenticationConfiguration(anonymousUserRole: String)