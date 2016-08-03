import React, { PropTypes, Component } from 'react';
import { render } from 'react-dom';
import ReactDOM from 'react-dom';
import { Link } from 'react-router';
import { Accordion, Panel, Glyphicon } from 'react-bootstrap';
import classNames from 'classnames';

import '../stylesheets/accordion.styl';

export default class UserPanel extends Component {

  constructor(props) {
      super(props);
      this.state = {
          visible: true
      };
  }

  toggleNav = () => {
    this.setState({ visible: !this.state.visible });
  }

  render() {
    var openCloseBtnClass = classNames({
      'is-opened': this.state.visible,
      'is-closed': !this.state.visible
    })
    var sideNavClasses = classNames({
      'sidenav': true,
      'is-opened': this.state.visible,
      'is-closed': !this.state.visible
    });
    return (
      <div>
        <div id="open-close-accordion" className={openCloseBtnClass}>
          <a href="javascript:void(0)" className="closebtn"
          onClick={this.toggleNav}>
            <Glyphicon id="closing-arrow" glyph={this.state.visible ? 'triangle-left' : 'triangle-right'}/>
          </a>
        </div>
        <div id="mySidenav" className={sideNavClasses}>
          <Accordion defaultActiveKey="1" expanded="true">
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
      </div>
    );
  }

}
