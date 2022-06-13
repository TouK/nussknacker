package pl.touk.nussknacker.ui.security.oauth2.jwt

import pdi.jwt.{JwtClaim, JwtHeader}
import pl.touk.nussknacker.engine.util.SensitiveDataMasker
import pl.touk.nussknacker.engine.util.SensitiveDataMasker.mask

case class ParsedJwtToken(header: JwtHeader, claim: JwtClaim, signature: String) {

  // claim can have some personal data like e-mail and signature can be used for session hijacking
  def masked = s"${getClass.getSimpleName}($header, ${SensitiveDataMasker.placeholder}, ${mask(signature)})"

}