import React from 'react';
import { render } from 'react-dom';
import ReactDOM from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import UserPanel from '../components/UserPanel';
import classNames from 'classnames';
import { Glyphicon } from 'react-bootstrap';
import _ from 'lodash';
import $ from 'jquery';
import appConfig from 'appConfig'

import '../stylesheets/visualization.styl';

export const Visualization = React.createClass({

  getInitialState: function() {
    return { userPanelOpened: false, process: {} , processDetails: {}, intervalId: null };
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

  toggleUserPanel: function() {
    this.setState({ userPanelOpened: !this.state.userPanelOpened });
  },

  deploy() {
    $.post(appConfig.API_URL + '/processManagement/deploy/' + this.props.params.processId)
  },

  render: function() {
    var userPanelOpenedClass = classNames({
      'is-opened': this.state.userPanelOpened,
      'is-closed': !this.state.userPanelOpened
    })

    return _.isEmpty(this.state.process) || _.isEmpty(this.state.processDetails) ? null :
    (
        <div className="Page">
            <UserPanel className={userPanelOpenedClass}/>
            <div id="toggle-user-panel" className={userPanelOpenedClass} onClick={this.toggleUserPanel}>
              <Glyphicon glyph={this.state.userPanelOpened ? 'remove' : 'menu-hamburger'}/>
            </div>
            <div id="working-area" className={userPanelOpenedClass}>
              <div>
                {this.state.processDetails.tags.map(function (tagi, tagIndex) {
                  return <div key={tagIndex} className="tagsBlockVis">{tagi}</div>
                })}
                <button type="button" className="btn btn-danger pull-right" onClick={this.deploy}>Deploy</button>
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
