import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import { Accordion, Panel } from 'react-bootstrap';
import { Scrollbars } from 'react-custom-scrollbars';
import ProcessHistory from './ProcessHistory'
import ToolBox from './ToolBox'
import ProcessComments from './ProcessComments'
import ProcessAttachments from './ProcessAttachments'
import Tips from './Tips.js'

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
        <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>}>
          <Tips />
          <Panel collapsible defaultExpanded header="Creator panel">
            <ToolBox/>
          </Panel>
          <Panel collapsible defaultExpanded header="Versions">
            <ProcessHistory/>
          </Panel>
          <Panel collapsible defaultExpanded header="Comments">
            <ProcessComments/>
          </Panel>
          <Panel collapsible defaultExpanded header="Attachments">
            <ProcessAttachments/>
          </Panel>
       </Scrollbars>
      </div>
    );
  }

}