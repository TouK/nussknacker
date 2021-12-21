package pl.touk.nussknacker.ui.security.ssl

import java.security.{KeyStore, SecureRandom}

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsConnectionContextFactory {

  def createServerContext(keyStoreConfig: KeyStoreConfig): HttpsConnectionContext = {
    val sslContext = prepareSSLContext(keyStoreConfig)
    ConnectionContext.httpsServer(sslContext)
  }

  def createClientContext(keyStoreConfig: KeyStoreConfig): HttpsConnectionContext = {
    val sslContext = prepareSSLContext(keyStoreConfig)
    ConnectionContext.httpsClient(sslContext)
  }

  private def prepareSSLContext(keyStoreConfig: KeyStoreConfig) = {
    val ks = KeyStore.getInstance("PKCS12")

    ks.load(keyStoreConfig.uri.toURL.openStream(), keyStoreConfig.password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, keyStoreConfig.password)

    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    sslContext
  }

}
