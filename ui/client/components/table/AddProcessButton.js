import createProcessIcon from "../../assets/img/create-process.svg"
import React from "react";

export default class AddProcessButton extends React.Component {

  render() {
    const {loggedUser, onClick} = this.props

    return (loggedUser.isWriter() ? (
        <button
          type={"button"}
          id="process-add-button"
          className="btn big-blue-button input-group "
          onClick={onClick}
          title={"CREATE NEW PROCESS"}>
          <span>CREATE NEW PROCESS</span>
          <img id="add-icon" src={createProcessIcon} alt={"add process icon"}/>
        </button>
      ) : null
    )
  }
}