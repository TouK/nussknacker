import React from 'react'
import {render} from "react-dom";
import {Scrollbars} from "react-custom-scrollbars";
import {connect} from "react-redux";
import {bindActionCreators} from "redux";
import "../stylesheets/processHistory.styl";
import {Accordion, Panel} from "react-bootstrap";
import Tool from "./Tool"
import "../stylesheets/toolBox.styl";


class ToolBox extends React.Component {

  static propTypes = {}

  constructor(props) {
    super(props);
    this.state = {};
  }

  render() {
    return (
      <div id="toolbox">
        <Tool type="Filter"/>
      </div>
    );
  }
}

export default ToolBox;
