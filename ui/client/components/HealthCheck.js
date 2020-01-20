import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import InlinedSvgs from "../assets/icons/InlinedSvgs"
import HttpService from "../http/HttpService"
import PeriodicallyReloadingComponent from "./PeriodicallyReloadingComponent"

class HealthCheck extends PeriodicallyReloadingComponent {

  constructor(props) {
    super(props)

    this.state = {
      healthCheck: undefined,
    }
  }

  getIntervalTime() {
    return _.get(this.props, "featuresSettings.intervalSettings.healthCheck", this.intervalTime)
  }

  reload() {
    HttpService.fetchHealthCheck().then((check) => this.setState({healthCheck: check}))
  }

  render() {
    const {healthCheck} = this.state
    if (!healthCheck || healthCheck.state === "ok") {
      return null
    }

    return (
      <div className="healthCheck">
        <div className="icon" title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>
        <span className="errorText">{healthCheck.error || "State unknown"}</span>
      </div>
    )
  }
}

const mapState = state => ({
  featuresSettings: state.settings.featuresSettings,
})

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(HealthCheck)
