package pl.touk.nussknacker.ui.security.oauth2.jwt

import pdi.jwt.JwtUtils
import pdi.jwt.exceptions.JwtLengthException
import pl.touk.nussknacker.engine.util.SensitiveDataMasker

case class RawJwtToken(token: String) {

  // claim can have some personal data like e-mail and signature can be used for session hijacking
  def masked: String = (List(header, SensitiveDataMasker.mask(claim)) ::: sigOpt.map(SensitiveDataMasker.mask) :: Nil).mkString(".")

  lazy val (header, claim, sigOpt) = {
    val parts = JwtUtils.splitString(token, '.')

    val signature = parts.length match {
      case 2 => None
      case 3 => Some(parts(2))
      case _ =>
        throw new JwtLengthException(
          s"Expected token [$token] to be composed of 2 or 3 parts separated by dots."
        )
    }

    (parts(0), parts(1), signature)
  }

}
