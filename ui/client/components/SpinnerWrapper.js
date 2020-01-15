import PropTypes from "prop-types"
import React from "react"
import "../stylesheets/spinner.styl"
import LoaderSpinner from "./Spinner"

export default class SpinnerWrapper extends React.Component {
  static propTypes = {
    isReady: PropTypes.bool.isRequired,
    children: PropTypes.element.isRequired,
  };

  render() {
    const spinner = <LoaderSpinner show={true}/>
    return this.props.isReady ? this.props.children : spinner
  }
}
