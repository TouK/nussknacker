import React from 'react'
import { render } from 'react-dom'
import ReactDOM from 'react-dom';
import { Link } from 'react-router'
import configureStore from '../store/configureStore';
import TodoAppRoot from '../containers/TodoAppRoot';
import Graph from '../components/graph/Graph'
import joint from 'jointjs'
import 'jointjs/dist/joint.css'
import _ from 'lodash'

import '../stylesheets/mainMenu.styl'

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
                    <li><Link to={Process.path}>{Process.header}</Link></li>
                    <li><Link to={Visualization.path}>{Visualization.header}</Link></li>
                    <li><Link to={TodoApp.path}>{TodoApp.header}</Link></li>
                  </ul>
                </div>
              </div>
            </nav>
            <main>
              <div>
                {this.props.children}
              </div>
            </main>
          </div>
        )
    }
});

App.title = 'Home'
App.path = '/'
App.header_a = 'ESP'
App.header_b = 'Event Stream Processing'

export const Process = () => (
    <div className="Page">
        <h1>{Process.header}</h1>
        <p>Zakladka z procesami</p>
    </div>
);

Process.title = 'Process'
Process.path = '/process'
Process.header = 'Procesy'

export const Visualization = () => {
    var graphData = {
//TODO
//TODO
    }

    return (
        <div className="Page">
            <h1>{Visualization.header}</h1>
            <p>Tutaj bedzie wizualizacja procesu</p>
            <Graph data={graphData}/>
        </div>
    )
}

Visualization.title = 'Visualization'
Visualization.path = '/visualization'
Visualization.header = 'Wizualizacja'

export const TodoApp = () => (

    <div className="Page">
        <TodoAppRoot store={ store }/>
    </div>
)

TodoApp.title = 'TodoApp'
TodoApp.path = '/todoApp'
TodoApp.header = 'TodoApp'
