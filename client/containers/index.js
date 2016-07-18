import React from 'react'
import { render } from 'react-dom'
import { Link } from 'react-router'
import configureStore from '../store/configureStore';
import TodoAppRoot from '../containers/TodoAppRoot';

const store = configureStore();


export const App = React.createClass({
    render: function() {
        return (
            <div>
                <aside>
                    <ul className="nav nav-pills nav-stacked">
                        <li><Link to={Process.path}>{Process.header}</Link></li>
                        <li><Link to={Visualization.path}>{Visualization.header}</Link></li>
                        <li><Link to={TodoApp.path}>{TodoApp.header}</Link></li>
                    </ul>
                </aside>
                <main>
                    {this.props.children}
                </main>
            </div>
        )
    }
});
App.title = 'Home'
App.path = '/'

export const Process = () => (
    <div className="Page">
        <h1>{Process.header}</h1>
        <p>Zakladka z procesami</p>
    </div>
);

Process.title = 'Process'
Process.path = '/process'
Process.header = 'Procesy'

export const Visualization = () => (
    <div className="Page">
        <h1>{Visualization.header}</h1>
        <p>Tutaj bedzie wizualizacja procesu</p>
    </div>
)

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