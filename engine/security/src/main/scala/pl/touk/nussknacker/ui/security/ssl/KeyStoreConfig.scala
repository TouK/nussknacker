package pl.touk.nussknacker.ui.security.ssl

import java.net.URI

case class KeyStoreConfig(uri: URI, password: Array[Char])