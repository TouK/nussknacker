import _ from 'lodash'
import React from 'react';
import InlinedSvgs from "../../assets/icons/InlinedSvgs"

class ModalRenderUtils {

  renderWarning = (title) => {
    return (<div className="node-tip" title={title} dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}} />);
  }

  renderInfo = (title) => {
    return (<div className="node-tip" title={title} dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsInfo}} />);
  }

  renderOtherErrors = (errors, errorMessage) => {
    return (!_.isEmpty(errors) ?
      <div className="node-table-body">
        <div className="node-label">{this.renderWarning(errorMessage)}</div>
        <div className="node-value">
          <div>
            {errors.map((error, index) =>
              (<div className="node-error" key={index} title={error.description}>{error.message + (error.fieldName ? ` (field: ${error.fieldName})` : '')}</div>)
            )}
          </div>

        </div>
      </div> : null)
  }


}
//TODO this pattern is not necessary, just export every public function as in actions.js
export default new ModalRenderUtils()
