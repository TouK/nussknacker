import React from 'react'
import { render } from 'react-dom'
import ReactDOM from 'react-dom';
import { Link } from 'react-router'
import configureStore from '../store/configureStore';
import TodoAppRoot from '../containers/TodoAppRoot';
import joint from 'jointjs'
import 'jointjs/dist/joint.css'
import _ from 'lodash'

import '../stylesheets/mainMenu.styl'

import { Processes } from './Processes'

const store = configureStore();

export const App = React.createClass({
    render: function() {
        return (
          <div id="app-container">
            <nav id="main-menu" className="navbar navbar-default">
              <div className="container-fluid">
                <div className="navbar-header">
                  <Link id="brand-name" className="navbar-brand" to={App.path}>
                    <span id="app-logo" className="vert-middle">{App.header_a}</span>
                    <span id="app-name" className="vert-middle">{App.header_b}</span>
                  </Link>
                </div>

                <div className="collapse navbar-collapse">
                  <ul id="menu-items" className="nav navbar-nav navbar-right nav-pills nav-stacked">
                    <li><Link to={Home.path}>{Home.header}</Link></li>
                    <li><Link to={Processes.path}>{Processes.header}</Link></li>
                    <li><Link to={TodoApp.path}>{TodoApp.header}</Link></li>
                  </ul>
                </div>
              </div>
            </nav>
            <main>
              {this.props.children}
            </main>
          </div>
        )
    }
});

App.title = 'Home'
App.path = '/'
App.header_a = 'ESP'
App.header_b = 'Event Stream Processing'

export const Home = () => (
  <div className="Page">
      <h1>Welcome to {App.header_b}</h1>
  </div>
);

Home.title = 'Home'
Home.path = '/home'
Home.header = 'Home'


export const TodoApp = () => (

    <div className="Page">
        <TodoAppRoot store={ store }/>
    </div>
)

TodoApp.title = 'TodoApp'
TodoApp.path = '/todoApp'
TodoApp.header = 'TodoApp'
