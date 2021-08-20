import {css, cx} from "emotion"
import React from "react"
import ErrorBoundary from "../../../common/ErrorBoundary"

export function ContentWrapper({children}) {
  return (
    <div
      className={cx("modalContentDark", css({padding: "1.0em 0.5em 1.5em", display: "flex", ">div": {width: "750px", flex: 1}}))}
      id="modal-content"
    >
      <div>
        {/*<Scrollbars*/}
        {/*  hideTracksWhenNotNeeded={true}*/}
        {/*  autoHeight*/}
        {/*  autoHeightMax={cssVariables.modalContentMaxHeight}*/}
        {/*  renderThumbVertical={props => <div {...props} className="thumbVertical"/>}*/}
        {/*>*/}
        <ErrorBoundary>
          {children}
        </ErrorBoundary>
        {/*</Scrollbars>*/}
      </div>
    </div>
  )
}
