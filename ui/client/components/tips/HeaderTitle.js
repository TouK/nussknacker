import React from "react";
import PropTypes from "prop-types";

export default class HeaderTitle extends React.Component {

  static propTypes = {
    message: PropTypes.string.isRequired
  }

  render() {
    const {message} = this.props
    return (
      <span>{message}</span>
    )
  }
}