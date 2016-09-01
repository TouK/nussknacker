import React from 'react'
import appConfig from 'appConfig'

export const Metrics = React.createClass({

  render: function() {
    var options = {
      grafanaUrl: appConfig.GRAFANA_URL,
      dashboard: 'touk-esp',
      processName: this.props.params.processId || "All",
      theme: 'light'
    };
    var iframeUrl = options.grafanaUrl + "/dashboard/db/" + options.dashboard + "?var-processName=" + options.processName + "&theme=" + options.theme
    return (
      <div className="Page">
        <iframe ref="metricsFrame" src={iframeUrl} width="100%" height={window.innerHeight} frameBorder="0"></iframe>
      </div>
    )
  }

})


Metrics.title = "Metrics"
Metrics.basePath = "/metrics"
Metrics.path = Metrics.basePath + "(/:processId)"
Metrics.header = "Metrics"