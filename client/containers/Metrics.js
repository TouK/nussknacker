import React from 'react'
import appConfig from 'appConfig'

export const Metrics = React.createClass({

  render: () => {
    var options = {
      grafanaUrl: appConfig.GRAFANA_URL,
      dashboard: 'touk-esp',
      aliasName: '%5B%5Btag_process%5D%5D',
      theme: 'light'
    };
    var iframeUrl = options.grafanaUrl + "/dashboard/db/" + options.dashboard + "?var-aliasName=" + options.aliasName + "&theme=" + options.theme
    return (
      <div className="Page">
        <iframe ref="metricsFrame" src={iframeUrl} width="100%" height={window.innerHeight} frameBorder="0"></iframe>
      </div>
    )
  }

})


Metrics.title = "Metrics"
Metrics.path = "/metrics"
Metrics.header = "Metrics"