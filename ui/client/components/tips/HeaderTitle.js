import React from "react";
import PropTypes from "prop-types";

export default function HeaderTitle(props) {
  const {message} = props

  return (
    <span>{message}</span>
  )
}

HeaderTitle.propTypes = {
  message: PropTypes.string.isRequired
}