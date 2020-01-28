import React from "react"
import {connect} from "react-redux"
import ActionsUtils, {EspActionsProps} from "../actions/ActionsUtils"
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

type OwnProps = {}

type State = {
  healthCheck?: HealthCheckResponse,
}

class HealthCheck extends PeriodicallyReloadingComponent<Props, State> {

  public static STATE_OK = "ok"

  onMount(): void {
    //Empty implementation for abstract class
  }

  getIntervalTime = () => this.props.healthCheckInterval

  reload = () => {
    HttpService.fetchHealthCheck().then((check: HealthCheckResponse) => this.setState({healthCheck: check}))
  }

  render() {
    const {t} = this.props

    if (this.state?.healthCheck.state === HealthCheck.STATE_OK) {
      return null
    }

    return (
      <div className="healthCheck">
        <div className="icon" title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>
        <span className="errorText">{this.state?.healthCheck.error || t("healthCheck.unknownState", "State unknown")}</span>
      </div>
    )
  }
}

const mapState = state => ({
  healthCheckInterval: state.settings.featuresSettings?.intervalSettings?.healthCheck,
})

type Props = OwnProps & ReturnType<typeof mapState> & EspActionsProps &  WithTranslation

const enhance = compose(
  connect(mapState, ActionsUtils.mapDispatchWithEspActions),
  withTranslation(),
)

export default enhance(HealthCheck)
