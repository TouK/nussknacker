import React, {PropTypes, Component} from "react";
import {render} from "react-dom";
import {Tabs, Tab} from "react-bootstrap";
import Resizable from "react-resizable-box";
import ProcessActions from "./ProcessActions";
import SideNodeDetails from "./SideNodeDetails";

export default class UserRightPanel extends Component {

  static propTypes = {
    isOpened: React.PropTypes.bool.isRequired,
    graphLayout: React.PropTypes.func.isRequired
  }

  renderClassName = () => {
    return this.props.isOpened ? 'rightSidenav is-opened' : 'rightSidenav'
  }

  render() {
    return (
      <div id="espSidenav" className={this.renderClassName()}>
        <Resizable customClass="item" width={280} height={560} isResizable={{top: false, left: true}}>
          <Tabs id="right-panel-tabs">
              <Tab eventKey={1} title="Actions">
                <ProcessActions graphLayout={this.props.graphLayout}/>
              </Tab>
            <Tab eventKey={2} title="Details">
              <SideNodeDetails/>
            </Tab>
            <Tab eventKey={3} title="History">Soon</Tab>
          </Tabs>
        </Resizable>
      </div>
    )
  }
}