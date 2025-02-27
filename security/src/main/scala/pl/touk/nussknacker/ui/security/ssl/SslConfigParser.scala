package pl.touk.nussknacker.ui.security.ssl

import com.typesafe.config.Config

import java.io.File

object SslConfigParser {

  private val SslPath      = "ssl"
  private val EnabledKey   = "enabled"
  private val KeyStorePath = "keyStore"
  private val LocationKey  = "location"
  private val PasswordKey  = "password"

  def sslEnabled(config: Config): Option[KeyStoreConfig] = {
    for {
      _ <- Some(())
      if config.hasPath(SslPath)
      sslConfig = config.getConfig(SslPath)
      if sslConfig.hasPath(EnabledKey) && sslConfig.getBoolean(EnabledKey)
    } yield {
      if (!sslConfig.hasPath(KeyStorePath))
        throw new IllegalArgumentException("You need to specify key store location and password to enable SSL")
      val keyStoreConfig = sslConfig.getConfig(KeyStorePath)
      if (!keyStoreConfig.hasPath(LocationKey))
        throw new IllegalArgumentException("You need to specify key store location to enable SSL")
      if (!keyStoreConfig.hasPath(PasswordKey))
        throw new IllegalArgumentException("You need to specify key store password to enable SSL")
      KeyStoreConfig(
        new File(keyStoreConfig.getString(LocationKey)).toURI,
        keyStoreConfig.getString(PasswordKey).toCharArray
      )
    }
  }

}
