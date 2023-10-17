package pl.touk.nussknacker.ui.security.ssl

import java.net.URI

final case class KeyStoreConfig(uri: URI, password: Array[Char])
