/* eslint-disable i18next/no-literal-string */
import {RightPanel} from "./RightPanel"
import Switch from "react-switch"
import React from "react"

export function ViewRightPanel({nothingToSave, businessView, onChange}: { nothingToSave: boolean, businessView: boolean, onChange: (checked: boolean) => void }) {
  return (
    <RightPanel title={"view"}>
      <div className="panel-properties">
        <label>
          <Switch
            disabled={!nothingToSave}
            uncheckedIcon={false}
            checkedIcon={false}
            height={14}
            width={28}
            offColor="#333"
            onColor="#333"
            offHandleColor="#999"
            onHandleColor="#8fad60"
            checked={businessView}
            onChange={onChange}
          />
          <span className="business-switch-text">Business View</span>
        </label>
      </div>
    </RightPanel>
  )
}
