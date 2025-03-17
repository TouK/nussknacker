package pl.touk.nussknacker.ui.server

import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.factory.{DomainServices, InfrastructureServices}
import pl.touk.nussknacker.ui.security.api.{AuthManager, AuthenticationResources}
import pl.touk.nussknacker.ui.server.PekkoRoutesFactory.PekkoRoutes
import pl.touk.nussknacker.ui.util._

object PekkoHttpBasedRouteFactory {

  def createRoute(
      designerConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices,
      domainServices: DomainServices
  ): Resource[IO, Route] = {
    import infrastructureServices._
    val authenticationResources =
      AuthenticationResources(
        designerConfig.rawConfig,
        PekkoHttpBasedRouteFactory.getClass.getClassLoader,
        infrastructureServices.futureSttpBackend
      )(executionContextWithIORuntime)
    val authManager = new AuthManager(authenticationResources)(executionContextWithIORuntime)

    for {
      pekkoRoutes <- PekkoRoutesFactory.createRoutes(
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

      pekkoHttpServerInterpreter = new NuPekkoHttpServerInterpreterForTapirPurposes()

      appRoute = createAppRoute(
        designerConfig = designerConfig,
        authManager = authManager,
        tapirRelatedRoutes = pekkoHttpServerInterpreter.toRoute(nuDesignerApi.allEndpoints) :: Nil,
        pekkoRoutes = pekkoRoutes,
        developmentMode = designerConfig.development
      )
    } yield appRoute
  }

  private def createAppRoute(
                              designerConfig: DesignerConfig,
                              authManager: AuthManager,
                              tapirRelatedRoutes: List[Route],
                              pekkoRoutes: PekkoRoutes,
                              developmentMode: Boolean
  ): Route = {
    // TODO: In the future will be nice to have possibility to pass authenticator.directive to resource and there us it at concrete path resource
    val webResources = new WebResources(designerConfig.http.publicPath)
    WithDirectives(CorsSupport.cors(developmentMode), SecurityHeadersSupport(), OptionsMethodSupport()) {
      tapirRelatedRoutes.reduce(_ ~ _) ~
        pathPrefixTest(!"api") {
          webResources.route
        } ~ pathPrefix("api") {
          pekkoRoutes.routesWithoutAuthentication.reduce(_ ~ _)
        } ~ pathPrefix("api") {
          authManager.authenticate() { authenticatedUser =>
            authManager.authorizeRoute(authenticatedUser) { loggedUser =>
              pekkoRoutes.routesWithAuthentication
                .map(_.securedRouteWithErrorHandling(loggedUser))
                .reduce(_ ~ _)
            }
          }
        }
    }
  }

}
