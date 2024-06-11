package pl.touk.nussknacker.test

import io.restassured.RestAssured

trait RestAssuredVerboseLoggingIfValidationFails {

  RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
}
