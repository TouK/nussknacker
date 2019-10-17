package pl.touk.nussknacker.ui.security.oauth2

import java.lang.RuntimeException

import scala.util.Random
import scala.util.{Failure, Success, Try}

class OAuth2Service(configuration: OAuth2Configuration) {

  def getAccessToken(grantCode: String): Try[String] = {
    val payload: Map[String, String] = Map(

    ) ++ configuration.authorizeParams

    if (grantCode.equals("exception"))  {
      return Failure(new RuntimeException("Error"))
    }

    Success(Random.alphanumeric.take(16).mkString(""))
  }
}
