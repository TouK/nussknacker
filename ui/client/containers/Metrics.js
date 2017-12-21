import React from 'react'
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import HttpService from "../http/HttpService";

class Metrics extends React.Component {

  static propTypes = {
      settings: React.PropTypes.object.isRequired,
  }

  componentDidMount() {
    HttpService.fetchProcessDetails(this.props.params.processId).then(details => {
        this.setState({processingType: details.processingType})
    })
  }

  render() {
    if (!this.props.settings.url || !this.state || !this.state.processingType) {
      return (<div/>)
    }
    const processingType = this.state.processingType;
    const processingTypeToDashboard = this.props.settings.processingTypeToDashboard;

    var options = {
      grafanaUrl: this.props.settings.url,
      dashboard: (processingTypeToDashboard && processingTypeToDashboard[processingType]) || this.props.settings.defaultDashboard,
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