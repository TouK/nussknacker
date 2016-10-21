import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import { Accordion, Panel } from 'react-bootstrap';
import ProcessHistory from './ProcessHistory'
import ToolBox from './ToolBox'

import '../stylesheets/userPanel.styl';

export default class UserLeftPanel extends Component {

  static propTypes = {
    isOpened: React.PropTypes.bool.isRequired
  }

  renderClassName() {
    return this.props.isOpened ? 'sidenav is-opened' : 'sidenav'
  }

  render() {
    return (
      <div id="espSidenav" className={this.renderClassName()}>
        {/*Historia domyslnie otwarta, bo wtedy scrollbar z historii poprawnie sie renderuje, teraz nie wiem jak to lepiej obejsc bez hakow*/}
        <Accordion defaultActiveKey="1">
          <Panel header="Versions" eventKey="1">
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