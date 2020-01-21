import * as queryString from "query-string"
import React from "react"
import {hot} from "react-hot-loader"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import ActionsUtils, {EspActionsProps, mapDispatchWithEspActions} from "../actions/ActionsUtils"
import api from "../api"
import SystemUtils from "../common/SystemUtils"
import LoaderSpinner from "../components/Spinner"
import HttpService from "../http/HttpService"
import InitializeError from "./errors/InitializeError"
import _ from "lodash"
import {$TodoType} from "../actions/migrationTypes"
import {RouteComponentProps} from "react-router"
import {withTranslation} from "react-i18next"
import {WithTranslation} from "react-i18next/src"
import {compose} from "redux"

type Error =  {
  message: string;
  description?: string;
  buttonLabel?: string;
  showButton?: boolean;
  buttonOnClick?: (event) => void;
}

type OwnProps = {}

type State = {
  errors: Record<number, Error>;
  error?: Error;
  initialized: boolean;
}

class NussknackerInitializer extends React.Component<Props, State> {
  // eslint-disable-next-line i18next/no-literal-string
  public static OAUTH2_BACKEND = "OAuth2"
  public static HTTP_UNAUTHORIZED_CODE = 401
  public static HTTP_APPLICATION_CODE = 500
  public static ACCESS_TOKEN_CODE = 1024
  
  redirectToAuthorizeUrl = () => {
    window.location.replace(this.props.authenticationSettings.authorizeUrl)
  }

  state = {
    error: null,
    errors: null,
    initialized: false,
  }

  componentDidMount() {
    const {t} = this.props

    this.setState({errors: {
      401: {
        message: t("nussknackerInitializer.errors.401.message", "Unauthorized Error"),
        description: t("nussknackerInitializer.errors.401.description", "It seems you are not authenticated... Why not try to authenticate again?"),
        buttonLabel: t("nussknackerInitializer.errors.401.buttonLabel", "Try authenticate again"),
      },
      504: {
        message: t("nussknackerInitializer.errors.504.message", "504 Gateway Timeout Error"),
        description: t("nussknackerInitializer.errors.504.description", "It seems server has some problems... Why not to try refreshing your page? Or you can contact with system administrators."),
      },
      500: {
        message: t("nussknackerInitializer.errors.500.message", "Application Unexpected Error"),
        showButton: false,
      },
      1024: {
        message: t("nussknackerInitializer.errors.accessToken.message", "Authentication Error"),
        buttonOnClick: this.redirectToAuthorizeUrl,
        buttonLabel: t("nussknackerInitializer.errors.504.buttonLabel", "Go to authentication page"),
        description: t("nussknackerInitializer.errors.504.description", "It seems application has some problem with authentication. Please contact with system administrators."),
      },
    }})

    //It looks like callback hell.. Can we do it better?
    HttpService.fetchSettings().then(settingsResponse => {
      const settings = settingsResponse.data
      this.props.actions.assignSettings(settings)

      this.authenticationStrategy(settings.authentication).then(response => {
        HttpService.fetchLoggedUser().then(userResponse => {
          this.props.actions.assignUser(userResponse.data)
          this.setState({initialized: true})
        }).catch(err => this.httpErrorHandler(err, settings.authentication.backend === NussknackerInitializer.OAUTH2_BACKEND))
      })
    }).catch(this.httpErrorHandlerWithoutRedirect)
  }

  httpErrorHandlerWithoutRedirect = (error: object): void => this.httpErrorHandler(error, false)

  httpErrorHandler = (error: object, redirect: boolean) => {
    const code: number = _.get(error, "response.status")
    const showError: boolean = (code !== NussknackerInitializer.HTTP_UNAUTHORIZED_CODE || !redirect)

    this.setState({
      error: showError ? this.state.errors[NussknackerInitializer.HTTP_APPLICATION_CODE]: null,
    })
  }

  authenticationStrategy = (settings): Promise<$TodoType> => {
    // Automatically redirect user when he is not authenticated and backend is OAUTH2
    api.interceptors.response.use(response => response, (error) => {
      if ( _.get(error, "response.status") === NussknackerInitializer.HTTP_UNAUTHORIZED_CODE && settings.backend === NussknackerInitializer.OAUTH2_BACKEND) {
        window.location.replace(settings.authorizeUrl)
      }

      return Promise.reject(error)
    })

    const queryParams = queryString.parse(this.props.history.location.search)
    if (settings.backend === NussknackerInitializer.OAUTH2_BACKEND && queryParams.code) {
      return HttpService.fetchOAuth2AccessToken(queryParams.code).then(response => {
        SystemUtils.setAuthorizationToken(response.data.accessToken)
        return Promise.resolve(response)
      }).catch(error => {
        this.setState({error: this.state.errors[NussknackerInitializer.ACCESS_TOKEN_CODE]})
        return Promise.reject(error)
      }).finally(() => {
        this.props.history.replace({search: null})
      })
    }

    if (settings.backend === NussknackerInitializer.OAUTH2_BACKEND && !SystemUtils.hasAccessToken()) {
      window.location.replace(settings.authorizeUrl)
      return Promise.reject()
    } else if (settings.backend !== NussknackerInitializer.OAUTH2_BACKEND && SystemUtils.hasAccessToken()) {
      SystemUtils.clearAuthorizationToken()
    }

    return Promise.resolve()
  }

  render() {
    if (this.state.error != null) {
      return (
          <InitializeError error={this.state.error}
                           buttonLabel={this.state.error.buttonLabel}
                           buttonOnClick={this.state.error.buttonOnClick}
                           message={this.state.error.message}
                           description={this.state.error.description}
                           showButton={this.state.error.showButton}
          />
      )
    }

    if (this.state.initialized) {
      return this.props.children
    }

    return <LoaderSpinner show={this.state.initialized === false} />
  }
}

function mapState(state) {
  return {
    authenticationSettings: state.settings.authenticationSettings,
  }
}

type Props = OwnProps & ReturnType<typeof mapState> & EspActionsProps &  WithTranslation & RouteComponentProps

const enhance = compose(
    hot(module),
    withRouter,
    connect(mapState, ActionsUtils.mapDispatchWithEspActions),
    withTranslation(),
)

export default enhance(NussknackerInitializer)
