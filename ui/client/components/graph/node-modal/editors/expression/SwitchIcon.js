import React from "react"
import switchIcon from "../../../../../assets/img/buttons/switch.svg"
import PropTypes from "prop-types"

export default function SwitchIcon(props) {

  const {switchable, onClick, shouldShowSwitch} = props

  return (
    shouldShowSwitch ?
      <button className={"inlined switch-icon"}
              onClick={onClick}
              disabled={!switchable}
              title={switchable ? "Switch to more intuitive mode" :
                "Expression must be equal to true or false to switch to more intuitive mode"}>
        <img src={switchIcon} alt={"switch icon"}/>
      </button> : null
  )
}

SwitchIcon.propTypes = {
  switchable: PropTypes.bool,
  onClick: PropTypes.func,
  shouldShowSwitch: PropTypes.bool
}