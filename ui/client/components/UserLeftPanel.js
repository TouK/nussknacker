import React, {Component} from 'react';
import {render} from 'react-dom';
import PropTypes from 'prop-types';
import {Panel} from 'react-bootstrap';
import {Scrollbars} from 'react-custom-scrollbars';
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
    isOpened: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired,
    loggedUser: PropTypes.object.isRequired,
    isReady: PropTypes.bool.isRequired
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
              <Panel defaultExpanded header="Creator panel">
                <ToolBox/>
              </Panel> : null
            }
            <Panel defaultExpanded header="Versions">
              <ProcessHistory/>
            </Panel>
            <Panel defaultExpanded header="Comments">
              <ProcessComments/>
            </Panel>
            <Panel defaultExpanded header="Attachments">
              <ProcessAttachments/>
            </Panel>
          </Scrollbars>
        </SpinnerWrapper>
      </div>
    );
  }

}
