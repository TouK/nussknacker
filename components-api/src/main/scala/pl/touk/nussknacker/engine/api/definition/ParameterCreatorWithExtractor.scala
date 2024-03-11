package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.definition.ParameterDeclaration.ParamType._
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

sealed trait ParameterCreator[DEPENDENCY] {
  def createParameter: DEPENDENCY => Parameter
}

sealed trait ParameterCreatorWithNoDependency extends ParameterCreator[Unit]

sealed trait ParameterExtractor[+PARAMETER_VALUE_TYPE] extends Serializable {
  def parameterName: ParameterName
  def extractValue(params: Params): PARAMETER_VALUE_TYPE

  private[definition] def createBase: Parameter
}

object ParameterExtractor {

  final class MandatoryParamExtractor[PARAMETER_VALUE_TYPE: TypeTag: NotNothing] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[PARAMETER_VALUE_TYPE] {

    override def extractValue(params: Params): PARAMETER_VALUE_TYPE =
      params.extractMandatory[PARAMETER_VALUE_TYPE](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[PARAMETER_VALUE_TYPE](parameterName)
  }

  final class MandatoryBranchParamExtractor[PARAMETER_VALUE_TYPE: TypeTag: NotNothing] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Map[String, PARAMETER_VALUE_TYPE]] {

    override def extractValue(params: Params): Map[String, PARAMETER_VALUE_TYPE] =
      params.extractMandatory[Map[String, PARAMETER_VALUE_TYPE]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[Map[String, PARAMETER_VALUE_TYPE]](parameterName)
  }

  final class MandatoryLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag: NotNothing] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[LazyParameter[PARAMETER_VALUE_TYPE]] {

    override def extractValue(params: Params): LazyParameter[PARAMETER_VALUE_TYPE] =
      params.extractMandatory[LazyParameter[PARAMETER_VALUE_TYPE]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[PARAMETER_VALUE_TYPE](parameterName).copy(isLazyParameter = true)
  }

  final class MandatoryBranchLazyParamExtractor[
      PARAMETER_VALUE_TYPE <: AnyRef: TypeTag: NotNothing
  ] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]] {

    override def extractValue(params: Params): Map[String, LazyParameter[PARAMETER_VALUE_TYPE]] =
      params.extractMandatory[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]](parameterName)
        .copy(isLazyParameter = true, branchParam = true)

  }

  final class OptionalParamExtractor[PARAMETER_VALUE_TYPE: TypeTag: NotNothing] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Option[PARAMETER_VALUE_TYPE]] {
    override def extractValue(params: Params): Option[PARAMETER_VALUE_TYPE] =
      params.extract[PARAMETER_VALUE_TYPE](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter.optional[PARAMETER_VALUE_TYPE](parameterName)
  }

}

object ParameterDeclaration {

  def mandatory[T: TypeTag: NotNothing](name: ParameterName): Builder[Mandatory[T]] =
    new Builder(Mandatory[T](name))

  def branchMandatory[T <: AnyRef: TypeTag: NotNothing](name: ParameterName): Builder[BranchMandatory[T]] =
    new Builder(BranchMandatory[T](name))

  def lazyMandatory[T <: AnyRef: TypeTag: NotNothing](name: ParameterName): Builder[LazyMandatory[T]] =
    new Builder(LazyMandatory[T](name))

  def branchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](name: ParameterName): Builder[BranchLazyMandatory[T]] =
    new Builder(BranchLazyMandatory[T](name))

  def optional[T: TypeTag: NotNothing](name: ParameterName): Builder[Optional[T]] = {
    new Builder(Optional[T](name))
  }

  sealed abstract class ParamType {
    type EXTRACTED_VALUE_TYPE
    def extractor: ParameterExtractor[EXTRACTED_VALUE_TYPE]
  }

  object ParamType {

    final case class Mandatory[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = T
      override lazy val extractor: ParameterExtractor[EXTRACTED_VALUE_TYPE] =
        new MandatoryParamExtractor[T](parameterName)
    }

    final case class LazyMandatory[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = LazyParameter[T]
      override lazy val extractor: ParameterExtractor[EXTRACTED_VALUE_TYPE] =
        new MandatoryLazyParamExtractor[T](parameterName)
    }

    final case class BranchMandatory[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Map[String, T]
      override lazy val extractor: ParameterExtractor[Map[String, T]] = new MandatoryBranchParamExtractor(parameterName)
    }

    final case class BranchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName)
        extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Map[String, LazyParameter[T]]
      override lazy val extractor: ParameterExtractor[Map[String, LazyParameter[T]]] =
        new MandatoryBranchLazyParamExtractor[T](parameterName)
    }

    final case class Optional[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Option[T]
      override lazy val extractor: ParameterExtractor[Option[T]] = new OptionalParamExtractor[T](parameterName)
    }

    final case class LazyOptional[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Option[LazyParameter[T]]

      override def extractor: ParameterExtractor[Option[LazyParameter[T]]] = ???
    }

    final case class BranchOptional[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Option[Map[String, T]]

      override def extractor: ParameterExtractor[Option[Map[String, T]]] = ???
    }

    final case class BranchLazyOptional[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName)
        extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Option[Map[String, LazyParameter[T]]]

      override def extractor: ParameterExtractor[Option[Map[String, LazyParameter[T]]]] = ???
    }

  }

  class Builder[T <: ParamType] private[ParameterDeclaration] (paramType: T) {

    def withCreator(
        modify: Parameter => Parameter = identity
    ): ParameterCreatorWithNoDependency with ParameterExtractor[T#EXTRACTED_VALUE_TYPE] = {
      parameterExtractorWithCreator(paramType.extractor, (_: Unit) => modify)
        .asInstanceOf[ParameterCreatorWithNoDependency with ParameterExtractor[T#EXTRACTED_VALUE_TYPE]]
    }

    def withCreator[DEPENDENCY](
        create: DEPENDENCY => Parameter => Parameter
    ): ParameterCreator[DEPENDENCY] with ParameterExtractor[T#EXTRACTED_VALUE_TYPE] = {
      parameterExtractorWithCreator(paramType.extractor, create)
    }

    private def parameterExtractorWithCreator[V, DEPENDENCY](
        underlying: ParameterExtractor[V],
        create: DEPENDENCY => Parameter => Parameter
    ): ParameterExtractor[V] with ParameterCreator[DEPENDENCY] = {
      new ParameterExtractor[V] with ParameterCreator[DEPENDENCY] {
        override def parameterName: ParameterName = underlying.parameterName

        override def extractValue(params: Params): V = underlying.extractValue(params)

        override def createParameter: DEPENDENCY => Parameter = dependency => create(dependency)(underlying.createBase)

        override def createBase: Parameter = underlying.createBase
      }
    }

  }

}

//object ParameterCreatorWithExtractor {
//
//  def mandatory[PARAMETER_VALUE_TYPE: TypeTag: NotNothing, DATA](
//      name: ParameterName,
//      create: DATA => Parameter => Parameter
//  ): ParameterCreatorWithExtractor[PARAMETER_VALUE_TYPE, DATA] = {
//    new MandatoryParameterCreatorWithExtractor(
//      name,
//      data => create(data)(Parameter[PARAMETER_VALUE_TYPE](name))
//    )
//  }
//
//  def lazyMandatory[T <: AnyRef: TypeTag: NotNothing, S](
//      name: ParameterName,
//      create: S => Parameter => Parameter
//  ): ParameterCreatorWithExtractor[LazyParameter[T]] = {
//    new MandatoryLazyParameterCreatorWithExtractor[T, S](
//      name,
//      s => create(s)(Parameter[T](name))
//    )
////    MandatoryLazyParameterCreatorWithExtractor.create[T](modify(Parameter[T](name).copy(isLazyParameter = true)))
//  }
//
//  def branchMandatory[T: TypeTag: NotNothing](
//      name: ParameterName,
//      modify: Parameter => Parameter = identity
//  ): ParameterCreatorWithExtractor[Map[String, T]] = {
//    MandatoryBranchParameterCreatorWithExtractor.create(modify(Parameter[T](name)))
//  }
//
//  def branchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](
//      name: ParameterName,
//      modify: Parameter => Parameter = identity
//  ): ParameterCreatorWithExtractor[Map[String, LazyParameter[T]]] = {
//    MandatoryBranchLazyParameterCreatorWithExtractor.create(modify(Parameter[T](name).copy(isLazyParameter = true)))
//  }
//
//  def optional[T: TypeTag: NotNothing](
//      name: ParameterName,
//      modify: Parameter => Parameter = identity
//  ): ParameterCreatorWithExtractor[Option[T]] = {
//    // todo: optional should be moved to OptionalParameterWithExtractor
//    OptionalParameterCreatorWithExtractor.create[T](modify(Parameter.optional[T](name)))
//  }
//
//  def lazyOptional[T <: AnyRef: TypeTag: NotNothing](
//      name: ParameterName,
//      modify: Parameter => Parameter = identity
//  ): ParameterCreatorWithExtractor[Option[LazyParameter[T]]] = {
//    OptionalLazyParameterCreatorWithExtractor.create[T](
//      modify(Parameter.optional[T](name).copy(isLazyParameter = true))
//    )
//  }
//
//  def branchOptional[T: TypeTag: NotNothing](
//      name: ParameterName,
//      modify: Parameter => Parameter = identity
//  ): ParameterCreatorWithExtractor[Option[Map[String, T]]] = {
//    // todo: optional should be moved to OptionalParameterWithExtractor
//    OptionalBranchParameterCreatorWithExtractor.create[T](modify(Parameter.optional[T](name)))
//  }
//
//  def branchLazyOptional[T <: AnyRef: TypeTag: NotNothing](
//      name: ParameterName,
//      modify: Parameter => Parameter = identity
//  ): ParameterCreatorWithExtractor[Option[Map[String, LazyParameter[T]]]] = {
//    // todo: optional should be moved to OptionalParameterWithExtractor
//    OptionalBranchLazyParameterCreatorWithExtractor.create[T](
//      modify(Parameter.optional[T](name).copy(isLazyParameter = true))
//    )
//  }
//
//}
//
//private final class MandatoryParameterCreatorWithExtractor[T, S](
//    override val parameterName: ParameterName,
//    override val createParameter: S => Parameter
//) extends ParameterCreatorWithExtractor[T] {
//
//  override def extractValue(params: Params): T = params.extractMandatory[T](parameterName)
//
//}
//
//private final class MandatoryLazyParameterCreatorWithExtractor[T <: AnyRef, S](
//    override val parameterName: ParameterName,
//    c: S => Parameter
//) extends ParameterCreatorWithExtractor[LazyParameter[T]] {
//
//  override def extractValue(params: Params): LazyParameter[T] =
//    params.extractMandatory[LazyParameter[T]](parameterName)
//
//  override type DATA = S
//
//  override def createParameter: DATA => Parameter = c
//}
//
//private final class MandatoryBranchParameterCreatorWithExtractor[T] private (override val createParameter: Parameter)
//    extends ParameterCreatorWithExtractor[Map[String, T]] {
//
//  override def extractValue(params: Params): Map[String, T] =
//    params.extractMandatory[Map[String, T]](createParameter.name)
//}
//
//private object MandatoryBranchParameterCreatorWithExtractor {
//
//  def create[T](parameter: Parameter): MandatoryBranchParameterCreatorWithExtractor[T] = {
//    new MandatoryBranchParameterCreatorWithExtractor[T](parameter.copy(branchParam = true))
//  }
//
//}
//
//private final class MandatoryBranchLazyParameterCreatorWithExtractor[T <: AnyRef] private (
//    override val createParameter: Parameter
//) extends ParameterCreatorWithExtractor[Map[String, LazyParameter[T]]] {
//
//  override def extractValue(params: Params): Map[String, LazyParameter[T]] =
//    params.extractMandatory[Map[String, LazyParameter[T]]](createParameter.name)
//}
//
//private object MandatoryBranchLazyParameterCreatorWithExtractor {
//
//  def create[T <: AnyRef](parameter: Parameter): MandatoryBranchLazyParameterCreatorWithExtractor[T] = {
//    new MandatoryBranchLazyParameterCreatorWithExtractor[T](parameter.copy(branchParam = true, isLazyParameter = true))
//  }
//
//}
//
//private final class OptionalParameterCreatorWithExtractor[T] private (override val createParameter: Parameter)
//    extends ParameterCreatorWithExtractor[Option[T]] {
//
//  override def extractValue(params: Params): Option[T] = params.extract[T](createParameter.name)
//}
//
//private object OptionalParameterCreatorWithExtractor {
//
//  def create[T](parameter: Parameter): OptionalParameterCreatorWithExtractor[T] = {
//    new OptionalParameterCreatorWithExtractor[T](parameter)
//  }
//
//}
//
//private final class OptionalLazyParameterCreatorWithExtractor[T <: AnyRef](override val createParameter: Parameter)
//    extends ParameterCreatorWithExtractor[Option[LazyParameter[T]]] {
//
//  override def extractValue(params: Params): Option[LazyParameter[T]] =
//    params.extract[LazyParameter[T]](createParameter.name)
//}
//
//private object OptionalLazyParameterCreatorWithExtractor {
//
//  def create[T <: AnyRef](parameter: Parameter): OptionalLazyParameterCreatorWithExtractor[T] = {
//    new OptionalLazyParameterCreatorWithExtractor[T](parameter.copy(isLazyParameter = true))
//  }
//
//}
//
//private final class OptionalBranchParameterCreatorWithExtractor[T] private (override val createParameter: Parameter)
//    extends ParameterCreatorWithExtractor[Option[Map[String, T]]] {
//
//  override def extractValue(params: Params): Option[Map[String, T]] =
//    params.extract[Map[String, T]](createParameter.name)
//}
//
//private object OptionalBranchParameterCreatorWithExtractor {
//
//  def create[T](parameter: Parameter): OptionalBranchParameterCreatorWithExtractor[T] = {
//    new OptionalBranchParameterCreatorWithExtractor[T](parameter.copy(branchParam = true))
//  }
//
//}
//
//private final class OptionalBranchLazyParameterCreatorWithExtractor[T <: AnyRef] private (
//    override val createParameter: Parameter
//) extends ParameterCreatorWithExtractor[Option[Map[String, LazyParameter[T]]]] {
//
//  override def extractValue(params: Params): Option[Map[String, LazyParameter[T]]] =
//    params.extract[Map[String, LazyParameter[T]]](createParameter.name)
//}
//
//private object OptionalBranchLazyParameterCreatorWithExtractor {
//
//  def create[T <: AnyRef](parameter: Parameter): OptionalBranchLazyParameterCreatorWithExtractor[T] = {
//    new OptionalBranchLazyParameterCreatorWithExtractor[T](parameter.copy(branchParam = true, isLazyParameter = true))
//  }
//
//}
