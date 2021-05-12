import React from "react"
import {Scrollbars} from "react-custom-scrollbars"
import cssVariables from "../../../../stylesheets/_variables.styl"
import ErrorBoundary from "../../../common/ErrorBoundary"

export function ContentWrapper({children}) {
  return (
    <div className="modalContentDark" id="modal-content">
      <Scrollbars
        hideTracksWhenNotNeeded={true}
        autoHeight
        autoHeightMax={cssVariables.modalContentMaxHeight}
        renderThumbVertical={props => <div {...props} className="thumbVertical"/>}
      >
        <ErrorBoundary>
          {children}
        </ErrorBoundary>
      </Scrollbars>
    </div>
  )
}
