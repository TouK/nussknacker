package pl.touk.nussknacker.test

import io.restassured.RestAssured
import io.restassured.filter.log.{RequestLoggingFilter, ResponseLoggingFilter}

trait RestAssuredVerboseLoggingIfValidationFails {
//
//  RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
  RestAssured.filters(new RequestLoggingFilter, new ResponseLoggingFilter)
}
