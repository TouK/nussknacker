import React from 'react';
import { render } from 'react-dom';
import ReactDOM from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import _ from 'lodash';
import $ from 'jquery';
import appConfig from 'appConfig'

import '../stylesheets/visualization.styl';

export const Visualization = React.createClass({

  getInitialState: function() {
    return { process: {} , processDetails: {}, intervalId: null };
  },

  componentDidMount() {
    this.startPollingForUpdates()
    this.fetchProcessJson();
    this.fetchProcessDetails();
  },

  componentWillUnmount() {
    clearInterval(this.state.intervalId)
  },

  startPollingForUpdates() {
    setTimeout(() =>
      this.setState({ intervalId: setInterval(this.fetchProcessDetails, 10000) }),
    2000)
  },

  fetchProcessJson() {
    $.get(appConfig.API_URL + '/processes/' + this.props.params.processId + '/json', (processModel) => {
      this.setState({process: processModel})
    })
  },

  fetchProcessDetails() {
    $.get(appConfig.API_URL + '/processes/' + this.props.params.processId, (processDetails) => {
      this.setState({processDetails: processDetails})
    })
  },

  deploy() {
    $.post(appConfig.API_URL + '/processManagement/deploy/' + this.props.params.processId)
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
                <button type="button" className="btn btn-danger" onClick={this.deploy}>Deploy</button>
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
