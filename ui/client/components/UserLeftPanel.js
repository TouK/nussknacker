import React, {Component} from "react";
import PropTypes from "prop-types";
import {Panel} from "react-bootstrap";
import {Scrollbars} from "react-custom-scrollbars";
import cn from "classnames";

import ProcessHistory from "./ProcessHistory"
import ToolBox from "./ToolBox"
import ProcessComments from "./ProcessComments"
import ProcessAttachments from "./ProcessAttachments"
import Tips from "./tips/Tips.js"
import TogglePanel from "./TogglePanel";
import "react-treeview/react-treeview.css"

import "../stylesheets/userPanel.styl";
import SpinnerWrapper from "./SpinnerWrapper";

export default class UserLeftPanel extends Component {

  static propTypes = {
    isOpened: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired,
    loggedUser: PropTypes.object.isRequired,
    isReady: PropTypes.bool.isRequired
  }

  render() {
    const {isOpened, onToggle, isReady, processName} = this.props;
    return (
        <div id="espLeftNav" className={cn("sidenav", {"is-opened": isOpened})}>
          <span className={cn("process-name", "left", {"is-opened": isOpened})}>{processName}</span>
            <SpinnerWrapper isReady={isReady}>
                <Scrollbars renderThumbVertical={props => <div {...props} className="thumbVertical"/>} hideTracksWhenNotNeeded={true}>
                    <Tips />
                    {this.props.capabilities.write ?
                        <Panel defaultExpanded>
                            <Panel.Heading><Panel.Title toggle>Creator panel</Panel.Title></Panel.Heading>
                            <Panel.Collapse>
                                <Panel.Body><ToolBox/></Panel.Body>
                            </Panel.Collapse>
                        </Panel> : null
                    }
                    <Panel defaultExpanded>
                        <Panel.Heading><Panel.Title toggle>Versions</Panel.Title></Panel.Heading>
                        <Panel.Collapse>
                            <Panel.Body><ProcessHistory/></Panel.Body>
                        </Panel.Collapse>
                    </Panel>
                    <Panel defaultExpanded>
                        <Panel.Heading><Panel.Title toggle>Comments</Panel.Title></Panel.Heading>
                        <Panel.Collapse>
                            <Panel.Body><ProcessComments/></Panel.Body>
                        </Panel.Collapse>
                    </Panel>
                    <Panel collapse="true" defaultExpanded header="Attachments" id="panel-attachments">
                        <Panel.Heading><Panel.Title toggle>Attachments</Panel.Title></Panel.Heading>
                        <Panel.Collapse>
                            <Panel.Body><ProcessAttachments/></Panel.Body>
                        </Panel.Collapse>
                    </Panel>
                </Scrollbars>
            </SpinnerWrapper>
          <TogglePanel type="left" isOpened={isOpened} onToggle={onToggle}/>
        </div>
    );
  }
}
