import React from 'react'
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

class Metrics extends React.Component {

  static propTypes = {
      settings: React.PropTypes.object.isRequired,
  }

  render() {
    if (!this.props.settings.url) {
      return (<div/>)
    }
    var options = {
      grafanaUrl: this.props.settings.url,
      dashboard: this.props.settings.dashboard,
      processName: this.props.params.processId || "All",
      theme: 'light',
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
    settings: state.settings.grafanaSettings
  };
}

function mapDispatch() {
  return {
    actions: {}
  };
}

export default connect(mapState, mapDispatch)(Metrics);