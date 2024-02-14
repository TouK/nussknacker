package pl.touk.nussknacker.ui.server

import sttp.tapir.{EndpointInput, EndpointOutput}

import java.io.InputStream

trait TapirStreamEndpointProvider {
  def streamBodyEndpointInput: EndpointInput[InputStream]
  def streamBodyEndpointOutput: EndpointOutput[InputStream]
}
