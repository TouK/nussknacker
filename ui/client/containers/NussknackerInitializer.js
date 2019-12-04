import React from 'react'
import {withRouter} from 'react-router-dom'
import {connect} from "react-redux"
import {hot} from "react-hot-loader";
import ActionsUtils from "../actions/ActionsUtils";
import HttpService from "../http/HttpService";
import InitializeError from "./errors/InitializeError";
import api from "../api";
import * as queryString from "query-string";
import LoaderSpinner from "../components/Spinner";
import SystemUtils from "../common/SystemUtils";

const OAUTH2_BACKEND = "OAuth2"
const HTTP_UNAUTHORIZED_CODE = 401
const HTTP_APPLICATION_CODE = 500
const ACCESS_TOKEN_CODE = "accessToken"

class NussknackerInitializer extends React.Component {
  redirectToAuthorizeUrl = () => {
    window.location.replace(this.props.authenticationSettings.authorizeUrl)
  }

  errors = {
    401: {
      message: "Unauthorized Error",
      description: "It seems you are not authenticated... Why not try to authenticate again?",
      buttonLabel: "Try authenticate again"
    },
     504: {
       message: "504 Gateway Timeout Error",
       description: "It seems server has some problems... Why not to try refreshing your page? Or you can contact with system administrators."
     },
    500: {
      message: "Application Unexpected Error",
      showButton: false
    },
    "accessToken": {
      message: "Authentication Error",
      buttonOnClick: this.redirectToAuthorizeUrl,
      buttonLabel: "Go to authentication page",
      description: "It seems application has some problem with authentication. Please contact with system administrators."
    }
  }

  constructor(props) {
    super(props)

    this.state = {
      error: null,
      initialized: false
    }
  }

  componentDidMount() {
    //It looks like callback hell.. Can we do it better?
    HttpService.fetchSettings().then(settingsResponse => {
      const settings = settingsResponse.data
      this.props.actions.assignSettings(settings)

      this.authenticationStrategy(settings.authentication).then(response => {
        HttpService.fetchLoggedUser().then(userResponse => {
          this.props.actions.assignUser(userResponse.data)
          this.setState({initialized: true})
        }).catch(err => this.httpErrorHandler(err, settings.authentication.backend === OAUTH2_BACKEND))
      })
    }).catch(this.httpErrorHandler)
  }

  httpErrorHandler = (error, redirect) => {
    const code = _.get(error, 'response.status')
    const showError = (code !== HTTP_UNAUTHORIZED_CODE || !redirect)
    this.setState({
      error: showError ? _.get(this.errors, code, this.errors[HTTP_APPLICATION_CODE]) : null
    })
  }

  authenticationStrategy = (settings) => {
    // Automatically redirect user when he is not authenticated and backend is OAUTH2
    api.interceptors.response.use(response => response, (error) => {
      if (error.response.status === HTTP_UNAUTHORIZED_CODE && settings.backend === OAUTH2_BACKEND) {
        window.location.replace(settings.authorizeUrl)
      }

      return Promise.reject(error)
    })

    const queryParams = queryString.parse(this.props.history.location.search)
    if (settings.backend === OAUTH2_BACKEND && queryParams.code) {
      return HttpService.fetchOAuth2AccessToken(queryParams.code).then(response => {
        SystemUtils.setAuthorizationToken(response.data.accessToken)
        return Promise.resolve(response)
      }).catch(error => {
        this.setState({error: this.errors[ACCESS_TOKEN_CODE]})
        return Promise.reject(error)
      }).finally(() => {
        this.props.history.replace({search: null})
      })
    }

    if (settings.backend === OAUTH2_BACKEND && !SystemUtils.hasAccessToken()) {
      window.location.replace(settings.authorizeUrl)
      return Promise.reject()
    } else if (settings.backend !== OAUTH2_BACKEND && SystemUtils.hasAccessToken()) {
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
    authenticationSettings: state.settings.authenticationSettings
  }
}

export default hot(module)(withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(NussknackerInitializer)))
