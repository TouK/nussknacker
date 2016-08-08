import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import ReactDOM from 'react-dom';
import { Link } from 'react-router';
import { Accordion, Panel } from 'react-bootstrap';

import '../stylesheets/userPanel.styl';

export default class UserPanel extends Component {

  constructor(props) {
      super(props);
      this.state = {
          visible: true
      };
  }

  render() {
    return (
      <div id="mySidenav" className={'sidenav ' + this.props.className}>
        <Accordion defaultActiveKey="1">
          <Panel header="Historia" eventKey="1">
            Testing
          </Panel>
          <Panel header="Panel kreatora" eventKey="2">
            Testing 2
          </Panel>
          <Panel header="Panel atrybutów" eventKey="3">
            Testing 3
          </Panel>
        </Accordion>
      </div>
    );
  }

}
