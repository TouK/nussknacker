import React from "react"
import PropTypes from "prop-types"

export default function HeaderIcon(props) {
  const {icon, className} = props

  return (
    <div className={className} title="icon" dangerouslySetInnerHTML={{__html: icon}}/>
  )
}

HeaderIcon.propTypes = {
  icon: PropTypes.string.isRequired,
  className: PropTypes.string
}