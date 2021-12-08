package pl.touk.nussknacker.engine.requestresponse.api

import cats.Monad
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteSource
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition

import scala.language.higherKinds

//TODO: this is a bit clumsy, we should think about:
//- responseEncoder in sourceFactory
//- passing path in request-response parameters and not through source
trait RequestResponseSourceFactory extends SourceFactory

trait RequestResponseGetSource[T] extends RequestResponseSource[T] {

  def parse(parameters: Map[String, List[String]]): T

}

trait RequestResponsePostSource[T] extends RequestResponseSource[T] {

  def parse(parameters: Array[Byte]): T

}

// TODO: Some smarter type in Input than Context?
trait RequestResponseSource[T] extends LiteSource[Context] {

  def responseEncoder: Option[ResponseEncoder[T]] = None

  def openApiDefinition: Option[OpenApiSourceDefinition] = None

  override def createTransformation[F[_] : Monad](evaluateLazyParameter: customComponentTypes.CustomComponentContext[F]): Context => ValidatedNel[ErrorType, Context] = ctx =>
    Valid(ctx)

}
