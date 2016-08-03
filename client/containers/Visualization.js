import React from 'react';
import { render } from 'react-dom';
import ReactDOM from 'react-dom';
import { Link } from 'react-router';
import Graph from '../components/graph/Graph';
import UserPanel from '../components/UserPanel';

import '../stylesheets/visualization.styl';

export const Visualization = () => {
    //var graphData = {
//TODO
//TODO
    //}

    var graphData = {
//TODO
//TODO
    }


    return (
        <div className="Page">
            <UserPanel />
            <h1>{Visualization.header}</h1>
            <p>Tutaj bedzie wizualizacja procesu</p>
            <Graph data={graphData}/>
        </div>
    )
}

Visualization.title = 'Visualization'
Visualization.path = '/visualization'
Visualization.header = 'Wizualizacja'
