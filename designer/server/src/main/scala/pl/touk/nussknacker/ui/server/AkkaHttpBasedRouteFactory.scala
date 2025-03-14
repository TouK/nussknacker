package pl.touk.nussknacker.ui.server

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.kernel.Resource
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.factory.{DomainServices, InfrastructureServices}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, AuthManager}
import pl.touk.nussknacker.ui.server.AkkaRoutesFactory.AkkaRoutes
import pl.touk.nussknacker.ui.util._

object AkkaHttpBasedRouteFactory {

  def createRoute(
      designerConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices,
      domainServices: DomainServices
  ): Resource[IO, Route] = {
    import infrastructureServices._
    val authenticationResources =
      AuthenticationResources(
        designerConfig.rawConfig,
        AkkaHttpBasedRouteFactory.getClass.getClassLoader,
        infrastructureServices.futureSttpBackend
      )(executionContextWithIORuntime)
    val authManager = new AuthManager(authenticationResources)(executionContextWithIORuntime)

    for {
      akkaRoutes <- AkkaRoutesFactory.createRoutes(
        designerConfig,
        infrastructureServices,
        domainServices,
        authenticationResources
      )
      nuDesignerApi = TapirHttpServiceFactory.createHttpService(
        designerConfig,
        infrastructureServices,
        domainServices,
        authManager
      )

      akkaHttpServerInterpreter = new NuAkkaHttpServerInterpreterForTapirPurposes()

      appRoute = createAppRoute(
        designerConfig = designerConfig,
        authManager = authManager,
        tapirRelatedRoutes = akkaHttpServerInterpreter.toRoute(nuDesignerApi.allEndpoints) :: Nil,
        akkaRoutes = akkaRoutes,
        developmentMode = designerConfig.development
      )
    } yield appRoute
  }

  private def createAppRoute(
      designerConfig: DesignerConfig,
      authManager: AuthManager,
      tapirRelatedRoutes: List[Route],
      akkaRoutes: AkkaRoutes,
      developmentMode: Boolean
  ): Route = {
    // TODO: In the future will be nice to have possibility to pass authenticator.directive to resource and there us it at concrete path resource
    val webResources = new WebResources(designerConfig.http.publicPath)
    WithDirectives(CorsSupport.cors(developmentMode), SecurityHeadersSupport(), OptionsMethodSupport()) {
      tapirRelatedRoutes.reduce(_ ~ _) ~
        pathPrefixTest(!"api") {
          webResources.route
        } ~ pathPrefix("api") {
          akkaRoutes.routesWithoutAuthentication.reduce(_ ~ _)
        } ~ pathPrefix("api") {
          authManager.authenticate() { authenticatedUser =>
            authManager.authorizeRoute(authenticatedUser) { loggedUser =>
              akkaRoutes.routesWithAuthentication
                .map(_.securedRouteWithErrorHandling(loggedUser))
                .reduce(_ ~ _)
            }
          }
        }
    }
  }

}
