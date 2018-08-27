import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import { Accordion, Panel } from 'react-bootstrap';
import { Scrollbars } from 'react-custom-scrollbars';
import cn from "classnames";

import ProcessHistory from './ProcessHistory'
import ToolBox from './ToolBox'
import ProcessComments from './ProcessComments'
import ProcessAttachments from './ProcessAttachments'
import Tips from './Tips.js'
import TogglePanel from './TogglePanel';

import '../stylesheets/userPanel.styl';
import SpinnerWrapper from "./SpinnerWrapper";

export default class UserLeftPanel extends Component {

  static propTypes = {
    isOpened: React.PropTypes.bool.isRequired,
    onToggle: React.PropTypes.func.isRequired,
    loggedUser: React.PropTypes.object.isRequired,
    isReady: React.PropTypes.bool.isRequired
  }

  render() {
    const { isOpened, onToggle, isReady } = this.props;

    return (
      <div id="espLeftNav" className={cn("sidenav", { "is-opened": isOpened })}>
        <TogglePanel type="left" isOpened={isOpened} onToggle={onToggle}/>
        <SpinnerWrapper isReady={isReady}>
          <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>} hideTracksWhenNotNeeded={true}>
            <Tips />
            {this.props.capabilities.write ?
              <Panel collapsible defaultExpanded header="Creator panel">
                <ToolBox/>
              </Panel> : null
            }
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
        </SpinnerWrapper>
      </div>
    );
  }

}
