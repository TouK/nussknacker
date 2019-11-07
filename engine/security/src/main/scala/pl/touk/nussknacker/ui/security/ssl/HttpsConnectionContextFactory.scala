package pl.touk.nussknacker.ui.security.ssl

import java.security.{KeyStore, SecureRandom}

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsConnectionContextFactory {

  def createContext(keyStoreConfig: KeyStoreConfig): HttpsConnectionContext = {
    val ks = KeyStore.getInstance("PKCS12")

    ks.load(keyStoreConfig.uri.toURL.openStream(), keyStoreConfig.password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, keyStoreConfig.password)

    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    ConnectionContext.https(sslContext)
  }

}
