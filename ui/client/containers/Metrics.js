import PropTypes from "prop-types"
import React from "react"
import {connect} from "react-redux"
import {withRouter} from "react-router"
import {nkPath} from "../config"
import HttpService from "../http/HttpService"
import {Page} from "./Page"
import {getFeatureSettings} from "../reducers/selectors/settings"

export class Metrics extends React.Component {

  static propTypes = {
    settings: PropTypes.object.isRequired,
  }

  constructor(props, context) {
    super(props, context)

    this.state = {
      processingType: null,
    }
  }

  componentDidMount() {
    if (this.props.match.params.processId) {
      HttpService.fetchProcessDetails(this.props.match.params.processId).then(response => {
        this.setState({processingType: response.data.processingType})
      })
    } else {
      this.setState({processingType: ""})
    }
  }

  render() {
    if (!this.props.settings.url) {
      return (<div/>)
    }

    const url = this.props.settings.url
    //TODO: this is still a bit grafana specific...
    const dashboard = this.getDashboardName()
    const processName = this.props.match.params.processId || "All"
    const finalIframeUrl = url.replace("$dashboard", dashboard).replace("$process", processName)

    return (
      <Page>
        <iframe
          ref="metricsFrame"
          src={finalIframeUrl}
          width="100%"
          height={window.innerHeight}
          frameBorder="0"
        />
      </Page>
    )
  }

  getDashboardName() {
    const processingType = this.state.processingType
    const processingTypeToDashboard = this.props.settings.processingTypeToDashboard
    return processingTypeToDashboard && processingTypeToDashboard[processingType] || this.props.settings.defaultDashboard
  }
}

Metrics.basePath = `${nkPath}/metrics`
Metrics.path = `${Metrics.basePath  }/:processId?`
Metrics.pathForProcess = (processId) => `${Metrics.basePath}/${processId}`
Metrics.header = "Metrics"

function mapState(state) {
  return {
    settings: getFeatureSettings(state).metrics || {},
  }
}

function mapDispatch() {
  return {
    actions: {},
  }
}

export default withRouter(connect(mapState, mapDispatch)(Metrics))
