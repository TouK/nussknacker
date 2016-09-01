import React from 'react';
import { render } from 'react-dom';
import ReactDOM from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import HttpService from '../http/HttpService'
import _ from 'lodash';

import '../stylesheets/visualization.styl';

export const Visualization = React.createClass({

  getInitialState: function() {
    return { process: {} , processDetails: {}, timeoutId: null, intervalId: null };
  },

  componentDidMount() {
    this.startPollingForUpdates()
    this.fetchProcessJson();
    this.fetchProcessDetails();
  },

  componentWillUnmount() {
    clearTimeout(this.state.timeoutId)
    clearInterval(this.state.intervalId)
  },

  startPollingForUpdates() {
    var timeoutId = setTimeout(() =>
      this.setState({ intervalId: setInterval(this.fetchProcessDetails, 10000) }),
    2000)
    this.setState({timeoutId: timeoutId})
  },

  fetchProcessJson() {
    HttpService.fetchProcessJson(this.props.params.processId, (processModel) => {
      this.setState({process: processModel})
    })
  },

  fetchProcessDetails() {
    HttpService.fetchProcessDetails(this.props.params.processId, (processDetails) => {
      this.setState({processDetails: processDetails})
    })
  },

  deploy() {
    HttpService.deploy(this.props.params.processId)
  },

  stop() {
    HttpService.stop(this.props.params.processId)
  },

  render: function() {
    return _.isEmpty(this.state.process) || _.isEmpty(this.state.processDetails) ? null :
    (
        <div className="Page">
            <div>
              <div id="esp-action-panel">
                {this.state.processDetails.tags.map(function (tagi, tagIndex) {
                  return <div key={tagIndex} className="tagsBlockVis">{tagi}</div>
                })}
                <button type="button" className="btn btn-success" onClick={this.deploy}>Deploy</button>
                <button type="button" className="btn btn-danger" onClick={this.stop}>Stop</button>
              </div>
              <Graph data={this.state.process} processDetails={this.state.processDetails}/>
            </div>
        </div>
    )
  }
});

Visualization.title = 'Visualization'
Visualization.path = '/visualization/:processId'
Visualization.header = 'Wizualizacja'
