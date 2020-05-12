import React from "react"
import {connect} from "react-redux"
import {EspActionsProps, mapDispatchWithEspActions} from "../actions/ActionsUtils"
import InlinedSvgs from "../assets/icons/InlinedSvgs"
import HttpService from "../http/HttpService"
import PeriodicallyReloadingComponent from "./PeriodicallyReloadingComponent"
import {withTranslation} from "react-i18next"
import {WithTranslation} from "react-i18next/src"
import {compose} from "redux"

type HealthCheckResponse = {
  state: string,
  error: string,
}

type State = {
  healthCheck?: HealthCheckResponse,
}

class HealthCheck extends PeriodicallyReloadingComponent<Props, State> {

  // eslint-disable-next-line i18next/no-literal-string
  public static stateOk = "ok"

  getIntervalTime = () => this.props.healthCheckInterval

  reload = () => {
    HttpService.fetchHealthCheckProcessDeployment().then((check: HealthCheckResponse) => this.setState({healthCheck: check}))
  }

  render() {
    const {t} = this.props

    return this.state?.healthCheck && this.state.healthCheck.state !== HealthCheck.stateOk ? (
      <div className="healthCheck">
        <div className="icon" title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>
        <span className="errorText">{this.state?.healthCheck.error || t("healthCheck.unknownState", "State unknown")}</span>
      </div>
    ): null
  }
}

const mapState = state => ({
  healthCheckInterval: state.settings.featuresSettings?.intervalSettings?.healthCheck,
})

type Props = ReturnType<typeof mapState> & EspActionsProps &  WithTranslation

const enhance = compose(
  connect(mapState, mapDispatchWithEspActions),
  withTranslation(),
)

export default enhance(HealthCheck)
