import React from 'react';
import { render } from 'react-dom';
import ReactDOM from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import UserPanel from '../components/UserPanel';
import classNames from 'classnames';
import { Glyphicon } from 'react-bootstrap';

import '../stylesheets/visualization.styl';

export const Visualization = React.createClass({

  getInitialState: function() {
    return { userPanelOpened: true };
  },

  toggleUserPanel: function() {
    this.setState({ userPanelOpened: !this.state.userPanelOpened });
  },

  render: function() {

    //var graphData = {
//TODO
//TODO
    //}
    //
    var graphData = {
//TODO
//TODO
    }

    var userPanelOpenedClass = classNames({
      'is-opened': this.state.userPanelOpened,
      'is-closed': !this.state.userPanelOpened
    })

    return (
        <div className="Page">
            <UserPanel className={userPanelOpenedClass}/>
            <div id="toggle-user-panel" className={userPanelOpenedClass} onClick={this.toggleUserPanel}>
              <Glyphicon glyph={this.state.userPanelOpened ? 'remove' : 'menu-hamburger'}/>
            </div>
            <div id="working-area" className={userPanelOpenedClass}>
              <Graph data={graphData}/>
            </div>
        </div>
    )
  }
});

Visualization.title = 'Visualization'
Visualization.path = '/visualization'
Visualization.header = 'Wizualizacja'
