import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import { Link } from 'react-router';
import { Accordion, Panel } from 'react-bootstrap';
import ProcessHistory from './ProcessHistory'
import ToolBox from './ToolBox'

import '../stylesheets/userPanel.styl';

export default class UserPanel extends Component {

  render() {
    return (
      <div id="espSidenav" className={'sidenav ' + this.props.className}>
        <Accordion defaultActiveKey="2">
          <Panel header="History" eventKey="1">
            <ProcessHistory/>
          </Panel>
          <Panel header="Creator panel" eventKey="2">
            <ToolBox/>
          </Panel>
        </Accordion>
      </div>
    );
  }

}