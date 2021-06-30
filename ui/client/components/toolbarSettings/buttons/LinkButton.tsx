import React from "react"
import {useDispatch} from "react-redux"
import {reportEvent} from "../../../actions/nk"
import {ReactComponent as FallbackIcon} from "../../../assets/img/toolbarButtons/link.svg"
import {PlainStyleLink} from "../../../containers/plainStyleLink"
import ToolbarButton from "../../toolbarComponents/ToolbarButton"
import UrlIcon from "../../UrlIcon"

export interface LinkButtonProps {
  name: string,
  title?: string,
  url: string,
  icon?: string,
  disabled: boolean,
}

export function LinkButton({url, icon, name, title, disabled}: LinkButtonProps): JSX.Element {
  const dispatch = useDispatch()

  return (
    <PlainStyleLink disabled={disabled} to={url}>
      <ToolbarButton
        name={name}
        title={title || name}
        onClick={() => dispatch(reportEvent({
          category: name,
          action: "custom-link-click",
          name: url,
        }))}
        disabled={disabled}
        icon={<UrlIcon path={icon}><FallbackIcon/></UrlIcon>}
      />
    </PlainStyleLink>
  )
}
