import React from "react"
import {hot} from "react-hot-loader"
import {withTranslation} from "react-i18next"
import {WithTranslation} from "react-i18next/src"
import {connect} from "react-redux"
import {compose} from "redux"
import {EspActionsProps, mapDispatchWithEspActions} from "../actions/ActionsUtils"
import InlinedSvgs from "../assets/icons/InlinedSvgs"
import HttpService, {HealthCheckResponse} from "../http/HttpService"
import {getFeatureSettings} from "../reducers/selectors/settings"
import styles from "./healthCheck.styl"

type State = {
  healthCheck?: HealthCheckResponse,
}

const stateOk = "ok"

class HealthCheck extends React.Component<Props, State> {
  private intervalId
  state = {healthCheck: null}

  componentDidMount() {
    const {healthCheckInterval = 4000} = this.props
    this.intervalId = setInterval(this.updateState, healthCheckInterval)
  }

  componentWillUnmount() {
    clearInterval(this.intervalId)
  }

  private async updateState() {
    const check = await HttpService.fetchHealthCheckProcessDeployment()
    this.setState({healthCheck: check})
  }

  render() {
    const {t} = this.props
    const {healthCheck} = this.state

    return !healthCheck || healthCheck.state === stateOk ?
      null :
      (
        <div className={styles.healthCheck}>
          <div className={styles.icon} title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>
          <span className={styles.errorText}>{healthCheck.error || t("healthCheck.unknownState", "State unknown")}</span>
        </div>
      )
  }
}

const mapState = state => ({
  healthCheckInterval: getFeatureSettings(state)?.intervalSettings?.healthCheck,
})

type Props = ReturnType<typeof mapState> & WithTranslation

const enhance = compose(
  connect(mapState),
  withTranslation(),
)

export default hot(module)(enhance(HealthCheck))
