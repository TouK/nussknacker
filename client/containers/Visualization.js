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
    return { userPanelOpened: false, process: {} };
  },

  componentDidMount() {
    $.get(appConfig.API_URL + '/processes/' + this.props.params.processId + '/json', (processModel) => {
      this.setState({process: processModel})
    })

  },

  toggleUserPanel: function() {
    this.setState({ userPanelOpened: !this.state.userPanelOpened });
  },

  render: function() {
    var userPanelOpenedClass = classNames({
      'is-opened': this.state.userPanelOpened,
      'is-closed': !this.state.userPanelOpened
    })
    return _.isEmpty(this.state.process) ? null :
    (
        <div className="Page">
            <UserPanel className={userPanelOpenedClass}/>
            <div id="toggle-user-panel" className={userPanelOpenedClass} onClick={this.toggleUserPanel}>
              <Glyphicon glyph={this.state.userPanelOpened ? 'remove' : 'menu-hamburger'}/>
            </div>
            <div id="working-area" className={userPanelOpenedClass}>
              <Graph data={this.state.process}/>
            </div>
        </div>
    )
  }
});

Visualization.title = 'Visualization'
Visualization.path = '/visualization/:processId'
Visualization.header = 'Wizualizacja'
