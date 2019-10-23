package pl.touk.nussknacker.ui.security.basicauth

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.{AuthenticationBackend, AuthenticationConfiguration}


case class ConfiguredUser(id: String,
                          password: Option[String],
                          encryptedPassword: Option[String],
                          categoryPermissions: Map[String, Set[Permission]] = Map.empty,
                          globalPermissions: List[GlobalPermission] = Nil,
                          isAdmin: Boolean = false)

class BasicAuthConfiguration(backend: AuthenticationBackend.Value, usersFile: String) extends AuthenticationConfiguration {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  val usersConfigurationPath = "users"

  override def getBackend(): AuthenticationBackend.Value = backend

  def loadUsers(): List[ConfiguredUser] = loadUsersConfig().as[List[ConfiguredUser]](usersConfigurationPath)

  def loadUsersConfig(): Config = ConfigFactory.parseFile(new File(usersFile))
}
