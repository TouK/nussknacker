import React from "react";
import HeaderIcon from "./HeaderIcon";
import PropTypes from "prop-types";

export default function ValidTip(props) {
  const {icon, message} = props

  return (
    <div className={"valid-tip"}>
      <HeaderIcon className={"icon"} icon={icon}/>
      <span>{message}</span>
    </div>
  )
}

ValidTip.propTypes = {
  icon: PropTypes.string.isRequired,
  message: PropTypes.string.isRequired
}