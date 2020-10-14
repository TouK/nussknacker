import _ from "lodash"
import * as queryString from "query-string"
import React from "react"
import {withTranslation} from "react-i18next"
import {WithTranslation} from "react-i18next/src"
import {connect} from "react-redux"
import {RouteComponentProps} from "react-router"
import {withRouter} from "react-router-dom"
import {compose} from "redux"
import {nanoid} from "nanoid"
import * as jwt from "jsonwebtoken"
import ActionsUtils, {EspActionsProps} from "../actions/ActionsUtils"
import api from "../api"
import SystemUtils from "../common/SystemUtils"
import LoaderSpinner from "../components/Spinner"
import HttpService from "../http/HttpService"
import {UnknownRecord} from "../types/common"
import InitializeError from "./errors/InitializeError"
import {AuthenticationSettings} from "../reducers/settings"

type Error = {
  message: string,
  description?: string,
  buttonLabel?: string,
  showButton?: boolean,
  buttonOnClick?: (event) => void,
}

type OwnProps = UnknownRecord

type State = {
  errors: Record<number, Error>,
  error?: Error,
  initialized: boolean,
}

class NussknackerInitializer extends React.Component<Props, State> {
  // eslint-disable-next-line i18next/no-literal-string
  public static OAUTH2_BACKEND = "OAuth2"
  public static HTTP_UNAUTHORIZED_CODE = 401
  public static HTTP_APPLICATION_CODE = 500
  public static ACCESS_TOKEN_CODE = 1024

  redirectToAuthorizeUrl(settings: AuthenticationSettings) {
    SystemUtils.saveNonce(nanoid())
    window.location.replace(`${settings.authorizeUrl}&nonce=${SystemUtils.getNonce()}`)
  }

  handleJwtError(error: jwt.JsonWebTokenError, settings: AuthenticationSettings) {
    console.warn(error)
    if (error.name === "TokenExpiredError")
      this.redirectToAuthorizeUrl(settings)
    else {
      this.setState({error: this.state.errors[NussknackerInitializer.ACCESS_TOKEN_CODE]})
      return Promise.reject()
    }
  }

  state = {
    error: null,
    errors: null,
    initialized: false,
  }

  componentDidMount() {
    const {t} = this.props

    this.setState({
      errors: {
        401: {
          message: t("nussknackerInitializer.errors.401.message", "Unauthorized Error"),
          description: t(
            "nussknackerInitializer.errors.401.description",
            "It seems you are not authenticated... Why not try to authenticate again?",
          ),
          buttonLabel: t("nussknackerInitializer.errors.401.buttonLabel", "Try authenticate again"),
        },
        504: {
          message: t("nussknackerInitializer.errors.504.message", "504 Gateway Timeout Error"),
          description: t(
            "nussknackerInitializer.errors.504.description",
            "It seems server has some problems... Why not to try refreshing your page? Or you can contact with system administrators.",
          ),
        },
        500: {
          message: t("nussknackerInitializer.errors.500.message", "Application Unexpected Error"),
          showButton: false,
        },
        1024: {
          message: t("nussknackerInitializer.errors.accessToken.message", "Authentication Error"),
          buttonOnClick: () => this.redirectToAuthorizeUrl(this.props.authenticationSettings),
          buttonLabel: t("nussknackerInitializer.errors.504.buttonLabel", "Go to authentication page"),
          description: t(
            "nussknackerInitializer.errors.504.description",
            "It seems application has some problem with authentication. Please contact with system administrators.",
          ),
        },
      },
    })

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

  httpErrorHandlerWithoutRedirect = (error: UnknownRecord): void => this.httpErrorHandler(error, false)

  httpErrorHandler = (error: $TodoType, redirect: boolean) => {
    const code: number = _.get(error, "response.status")
    const showError: boolean = code !== NussknackerInitializer.HTTP_UNAUTHORIZED_CODE || !redirect

    this.setState({
      error: showError ? this.state.errors[NussknackerInitializer.HTTP_APPLICATION_CODE] : null,
    })
  }

  authenticationStrategy = (settings): Promise<$TodoType> => {
    // Automatically redirect user when he is not authenticated and backend is OAUTH2
    api.interceptors.response.use(response => response, (error) => {
      if (_.get(error, "response.status") === NussknackerInitializer.HTTP_UNAUTHORIZED_CODE && settings.backend === NussknackerInitializer.OAUTH2_BACKEND) {
        this.redirectToAuthorizeUrl(settings)
      }

      return Promise.reject(error)
    })

    if (settings.backend === NussknackerInitializer.OAUTH2_BACKEND) {
      const queryParams = queryString.parse(this.props.history.location.search)
      if (queryParams.code) {
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

      const queryHashParams = queryString.parse(this.props.history.location.hash)
      if (settings.implicitGrantEnabled === true && queryHashParams.access_token) {
        if (this.verifyTokens(settings, queryHashParams)) {
          SystemUtils.setAuthorizationToken(queryHashParams.access_token)
          this.props.history.replace({hash: null})
        } else
          return Promise.reject()
      }

      if (!SystemUtils.hasAccessToken()) {
        this.redirectToAuthorizeUrl(settings)
        return Promise.reject()
      }
    } else if (SystemUtils.hasAccessToken()) {
      SystemUtils.clearAuthorizationToken()
    }

    return Promise.resolve()
  }

  verifyTokens(settings: AuthenticationSettings, queryHashParams): boolean {
    if (settings.jwtAuthServerPublicKey) {
      const verifyAccessToken = () => {
        try {
          return jwt.verify(queryHashParams.access_token, settings.jwtAuthServerPublicKey) !== null
        } catch(error) {
          this.handleJwtError(error, settings)
          return false
        }
      }
      const accessTokenVerificationResult = verifyAccessToken()
      if (accessTokenVerificationResult) {
        if (queryHashParams.id_token) {
          try {
            return jwt.verify(queryHashParams.id_token, settings.jwtAuthServerPublicKey, {nonce: SystemUtils.getNonce()}) !== null
          } catch (error) {
            this.handleJwtError(error, settings)
            return false
          }
        } else if (settings.jwtIdTokenNonceVerificationRequired === true) {
          // eslint-disable-next-line i18next/no-literal-string
          console.warn("jwt.idTokenNonceVerificationRequired=true but id_token missing in the auth server response")
          this.setState({error: this.state.errors[NussknackerInitializer.ACCESS_TOKEN_CODE]})
          return false
        }
      }
      return accessTokenVerificationResult
    } else
      return true
  }

  render() {
    if (this.state.error != null) {
      return (
        <InitializeError
          error={this.state.error}
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

    return <LoaderSpinner show={this.state.initialized === false}/>
  }
}

function mapState(state) {
  return {
    authenticationSettings: state.settings.authenticationSettings,
  }
}

type Props = OwnProps & ReturnType<typeof mapState> & EspActionsProps &  WithTranslation & RouteComponentProps

const enhance = compose(
  withRouter,
  connect(mapState, ActionsUtils.mapDispatchWithEspActions),
  withTranslation(),
)

export default enhance(NussknackerInitializer)
