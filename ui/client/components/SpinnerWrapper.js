import React from "react"
import PropTypes from "prop-types";
import LoaderSpinner from "./Spinner"
import "../stylesheets/spinner.styl"

export default class SpinnerWrapper extends React.Component {
  static propTypes = {
    isReady: PropTypes.bool.isRequired,
    children: PropTypes.element.isRequired
  };

  render() {
    const spinner = <LoaderSpinner show={true} />;
    return this.props.isReady ? this.props.children : spinner;
  }
}
