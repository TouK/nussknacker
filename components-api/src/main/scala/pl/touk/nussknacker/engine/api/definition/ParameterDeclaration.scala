package pl.touk.nussknacker.engine.api.definition

import cats.implicits._
import cats.{Functor, Id}
import pl.touk.nussknacker.engine.api.definition.ParameterDeclarationBuilder.ParamType
import pl.touk.nussknacker.engine.api.definition.ParameterDeclarationBuilder.ParamType._
import pl.touk.nussknacker.engine.api.definition.ParameterExtractor._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.{LazyParameter, Params}
import pl.touk.nussknacker.engine.api.util.NotNothing

import scala.reflect.runtime.universe._

/**
 * It is helper class that holds runtime value type next to definition of parameter.
 * It reduce boilerplate defining `DynamicComponent` and reduce risk that definition of parameter
 * will desynchronize with implementation code using values
 */
object ParameterDeclaration {

  def mandatory[T: TypeTag: NotNothing](name: ParameterName): ParameterDeclarationBuilder[Id, Mandatory[T]] =
    new ParameterDeclarationBuilder(Mandatory[T](name))

  def branchMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[Id, BranchMandatory[T]] =
    new ParameterDeclarationBuilder(BranchMandatory[T](name))

  def lazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[Id, LazyMandatory[T]] =
    new ParameterDeclarationBuilder(LazyMandatory[T](name))

  def branchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[Id, BranchLazyMandatory[T]] =
    new ParameterDeclarationBuilder(BranchLazyMandatory[T](name))

  def optional[T: TypeTag: NotNothing](name: ParameterName): ParameterDeclarationBuilder[IsPresent, Optional[T]] = {
    new ParameterDeclarationBuilder(Optional[T](name))
  }

  def branchOptional[T: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[IsPresent, BranchOptional[T]] =
    new ParameterDeclarationBuilder(BranchOptional[T](name))

  def lazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[IsPresent, LazyOptional[T]] =
    new ParameterDeclarationBuilder(LazyOptional[T](name))

  def branchLazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[IsPresent, BranchLazyOptional[T]] =
    new ParameterDeclarationBuilder(BranchLazyOptional[T](name))

}

sealed trait ParameterCreator[DEPENDENCY] extends Serializable {
  def createParameter: DEPENDENCY => Parameter
}

sealed trait ParameterCreatorWithNoDependency extends Serializable {
  def createParameter(): Parameter
}

sealed abstract class ParameterExtractor[F[_], PARAMETER_VALUE_TYPE] extends Serializable {

  def parameterName: ParameterName

  def extractValue(params: Params): F[Option[PARAMETER_VALUE_TYPE]]

  def extractValueUnsafe(params: Params): F[PARAMETER_VALUE_TYPE] = {
    extractValue(params)
      .map(
        _.getOrElse(
          throw new IllegalArgumentException(s"Parameter [${parameterName.value}] doesn't expect to be null!")
        )
      )
  }

  private[definition] implicit def fFunctor: Functor[F]
  private[definition] def createBase: Parameter
}

object ParameterExtractor {

  final class MandatoryParamExtractor[PARAMETER_VALUE_TYPE: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Id, PARAMETER_VALUE_TYPE] {

    override private[definition] implicit val fFunctor: Functor[Id] = cats.catsRepresentableForId.F

    override def extractValue(params: Params): Option[PARAMETER_VALUE_TYPE] =
      params.extract[PARAMETER_VALUE_TYPE](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[PARAMETER_VALUE_TYPE](parameterName)
  }

  final class MandatoryBranchParamExtractor[PARAMETER_VALUE_TYPE: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Id, Map[String, PARAMETER_VALUE_TYPE]] {

    override private[definition] implicit val fFunctor: Functor[Id] = cats.catsRepresentableForId.F

    override def extractValue(params: Params): Option[Map[String, PARAMETER_VALUE_TYPE]] =
      params.extract[Map[String, PARAMETER_VALUE_TYPE]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[Map[String, PARAMETER_VALUE_TYPE]](parameterName).copy(branchParam = true)
  }

  final class MandatoryLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Id, LazyParameter[PARAMETER_VALUE_TYPE]] {

    override private[definition] implicit val fFunctor: Functor[Id] = cats.catsRepresentableForId.F

    override def extractValue(params: Params): Option[LazyParameter[PARAMETER_VALUE_TYPE]] =
      params.extract[LazyParameter[PARAMETER_VALUE_TYPE]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[PARAMETER_VALUE_TYPE](parameterName).copy(isLazyParameter = true)
  }

  final class MandatoryBranchLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Id, Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]] {

    override private[definition] implicit val fFunctor: Functor[Id] = cats.catsRepresentableForId.F

    override def extractValue(params: Params): Option[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]] =
      params.extract[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]](parameterName)
        .copy(isLazyParameter = true, branchParam = true)

  }

  sealed trait IsPresent[+T]

  object IsPresent {
    final case class Yes[T](value: T) extends IsPresent[T]
    case object No                    extends IsPresent[Nothing]

    def when[A](cond: Boolean)(a: => A): IsPresent[A] =
      if (cond) IsPresent.Yes(a) else IsPresent.No

    implicit val functor: Functor[IsPresent] = new Functor[IsPresent] {

      override def map[A, B](fa: IsPresent[A])(f: A => B): IsPresent[B] = {
        fa match {
          case Yes(value) => Yes(f(value))
          case No         => No
        }
      }

    }

    implicit class ToOption[T](val isPresent: IsPresent[T]) extends AnyVal {

      def toOption: Option[T] = isPresent match {
        case Yes(value) => Some(value)
        case No         => None
      }

    }

  }

  final class OptionalParamExtractor[PARAMETER_VALUE_TYPE: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[IsPresent, PARAMETER_VALUE_TYPE] {

    override private[definition] implicit val fFunctor: Functor[IsPresent] = IsPresent.functor

    override def extractValue(params: Params): IsPresent[Option[PARAMETER_VALUE_TYPE]] = {
      IsPresent.when(params.isPresent(parameterName)) {
        params.extract[PARAMETER_VALUE_TYPE](parameterName)
      }
    }

    override private[definition] def createBase: Parameter =
      Parameter.optional[PARAMETER_VALUE_TYPE](parameterName)
  }

  final class OptionalLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[IsPresent, LazyParameter[PARAMETER_VALUE_TYPE]] {

    override private[definition] implicit val fFunctor: Functor[IsPresent] = IsPresent.functor

    override def extractValue(params: Params): IsPresent[Option[LazyParameter[PARAMETER_VALUE_TYPE]]] =
      IsPresent.when(params.isPresent(parameterName)) {
        params.extract[LazyParameter[PARAMETER_VALUE_TYPE]](parameterName)
      }

    override private[definition] def createBase: Parameter =
      Parameter.optional[PARAMETER_VALUE_TYPE](parameterName).copy(isLazyParameter = true)
  }

  final class OptionalBranchParamExtractor[PARAMETER_VALUE_TYPE: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[IsPresent, Map[String, PARAMETER_VALUE_TYPE]] {

    override private[definition] implicit val fFunctor: Functor[IsPresent] = IsPresent.functor

    override def extractValue(params: Params): IsPresent[Option[Map[String, PARAMETER_VALUE_TYPE]]] =
      IsPresent.when(params.isPresent(parameterName)) {
        params.extract[Map[String, PARAMETER_VALUE_TYPE]](parameterName)
      }

    override private[definition] def createBase: Parameter =
      Parameter.optional[PARAMETER_VALUE_TYPE](parameterName).copy(branchParam = true)
  }

  final class OptionalBranchLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[IsPresent, Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]] {

    override private[definition] implicit val fFunctor: Functor[IsPresent] = IsPresent.functor

    override def extractValue(params: Params): IsPresent[Option[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]]] =
      IsPresent.when(params.isPresent(parameterName)) {
        params.extract[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]](parameterName)
      }

    override private[definition] def createBase: Parameter = {
      Parameter
        .optional[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]](parameterName)
        .copy(isLazyParameter = true, branchParam = true)
    }

  }

}

class ParameterDeclarationBuilder[F[_], T <: ParamType[F]] private[definition] (paramType: T) {

  def withCreator(
      modify: Parameter => Parameter = identity
  ): ParameterExtractor[F, T#EXTRACTED_VALUE_TYPE] with ParameterCreatorWithNoDependency = {
    val underlying: ParameterExtractor[F, T#EXTRACTED_VALUE_TYPE] =
      paramType.extractor.asInstanceOf[ParameterExtractor[F, T#EXTRACTED_VALUE_TYPE]]
    new ParameterExtractor[F, T#EXTRACTED_VALUE_TYPE] with ParameterCreatorWithNoDependency {
      override def parameterName: ParameterName                                    = underlying.parameterName
      override def extractValue(params: Params): F[Option[T#EXTRACTED_VALUE_TYPE]] = underlying.extractValue(params)
      override def createParameter(): Parameter                                    = modify(underlying.createBase)
      override private[definition] implicit def fFunctor: Functor[F]               = underlying.fFunctor
      override private[definition] def createBase: Parameter                       = underlying.createBase
    }
  }

  def withAdvancedCreator[DEPENDENCY](
      create: DEPENDENCY => Parameter => Parameter
  ): ParameterExtractor[F, T#EXTRACTED_VALUE_TYPE] with ParameterCreator[DEPENDENCY] = {
    val underlying: ParameterExtractor[F, T#EXTRACTED_VALUE_TYPE] =
      paramType.extractor.asInstanceOf[ParameterExtractor[F, T#EXTRACTED_VALUE_TYPE]]
    new ParameterExtractor[F, T#EXTRACTED_VALUE_TYPE] with ParameterCreator[DEPENDENCY] {
      override def parameterName: ParameterName                                    = underlying.parameterName
      override def extractValue(params: Params): F[Option[T#EXTRACTED_VALUE_TYPE]] = underlying.extractValue(params)
      override def createParameter: DEPENDENCY => Parameter = dependency => create(dependency)(underlying.createBase)
      override private[definition] implicit def fFunctor: Functor[F] = underlying.fFunctor
      override private[definition] def createBase: Parameter         = underlying.createBase
    }
  }

}

object ParameterDeclarationBuilder {

  sealed abstract class ParamType[F[_]] {
    type EXTRACTED_VALUE_TYPE

    def extractor: ParameterExtractor[F, EXTRACTED_VALUE_TYPE]
  }

  object ParamType {

    final case class Mandatory[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType[Id] {
      override type EXTRACTED_VALUE_TYPE = T
      override lazy val extractor: ParameterExtractor[Id, EXTRACTED_VALUE_TYPE] =
        new MandatoryParamExtractor[T](parameterName)
    }

    final case class LazyMandatory[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName)
        extends ParamType[Id] {
      override type EXTRACTED_VALUE_TYPE = LazyParameter[T]
      override lazy val extractor: ParameterExtractor[Id, EXTRACTED_VALUE_TYPE] =
        new MandatoryLazyParamExtractor[T](parameterName)
    }

    final case class BranchMandatory[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType[Id] {
      override type EXTRACTED_VALUE_TYPE = Map[String, T]

      override lazy val extractor: ParameterExtractor[Id, Map[String, T]] = new MandatoryBranchParamExtractor(
        parameterName
      )

    }

    final case class BranchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName)
        extends ParamType[Id] {
      override type EXTRACTED_VALUE_TYPE = Map[String, LazyParameter[T]]
      override lazy val extractor: ParameterExtractor[Id, Map[String, LazyParameter[T]]] =
        new MandatoryBranchLazyParamExtractor[T](parameterName)
    }

    final case class Optional[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType[IsPresent] {
      override type EXTRACTED_VALUE_TYPE = T
      override lazy val extractor: ParameterExtractor[IsPresent, T] = new OptionalParamExtractor[T](parameterName)
    }

    final case class LazyOptional[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName)
        extends ParamType[IsPresent] {
      override type EXTRACTED_VALUE_TYPE = LazyParameter[T]
      override lazy val extractor: ParameterExtractor[IsPresent, LazyParameter[T]] =
        new OptionalLazyParamExtractor[T](parameterName)
    }

    final case class BranchOptional[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType[IsPresent] {
      override type EXTRACTED_VALUE_TYPE = Map[String, T]

      override lazy val extractor: ParameterExtractor[IsPresent, Map[String, T]] =
        new OptionalBranchParamExtractor(parameterName)

    }

    final case class BranchLazyOptional[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName)
        extends ParamType[IsPresent] {
      override type EXTRACTED_VALUE_TYPE = Map[String, LazyParameter[T]]
      override lazy val extractor: ParameterExtractor[IsPresent, Map[String, LazyParameter[T]]] =
        new OptionalBranchLazyParamExtractor[T](parameterName)
    }

  }

}
