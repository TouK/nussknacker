import React from 'react'
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import HttpService from "../http/HttpService";

class Metrics extends React.Component {

  static propTypes = {
      settings: React.PropTypes.object.isRequired,
  }

  componentDidMount() {
    if (this.props.params.processId) {
      HttpService.fetchProcessDetails(this.props.params.processId).then(details => {
          this.setState({processingType: details.processingType})
      })
    } else {
      this.setState({processingType: ""})
    }
  }

  render() {
    if (!this.props.settings.url || !this.state) {
      return (<div/>)
    }

    var options = {
      grafanaUrl: this.props.settings.url,
      dashboard: this.getDashboardName(),
      processName: this.props.params.processId || "All",
      theme: 'dark',
      env: this.props.settings.env
    };
    var iframeUrl = options.grafanaUrl + "/dashboard/db/" + options.dashboard + "?var-processName=" + options.processName
      + "&theme=" + options.theme + "&var-env=" + options.env
    return (
      <div className="Page">
        <iframe ref="metricsFrame" src={iframeUrl} width="100%" height={window.innerHeight} frameBorder="0"></iframe>
      </div>
    )
  }

  getDashboardName() {
    const processingType = this.state.processingType;
    const processingTypeToDashboard = this.props.settings.processingTypeToDashboard;
    return (processingTypeToDashboard && processingTypeToDashboard[processingType]) || this.props.settings.defaultDashboard
  }

}

Metrics.basePath = "/metrics"
Metrics.path = Metrics.basePath + "(/:processId)"
Metrics.header = "Metrics"

function mapState(state) {
  return {
    settings: state.settings.featuresSettings.metrics || {}
  };
}

function mapDispatch() {
  return {
    actions: {}
  };
}

export default connect(mapState, mapDispatch)(Metrics);