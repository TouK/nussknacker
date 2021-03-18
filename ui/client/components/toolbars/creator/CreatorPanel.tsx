import {css} from "emotion"
import {isEmpty} from "lodash"
import React, {useCallback, useState} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {useNkTheme} from "../../../containers/theme"
import {getCapabilities} from "../../../reducers/selectors/other"
import {SearchIcon} from "../../table/SearchFilter"
import {InputWithIcon} from "../../themed/InputWithIcon"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import ToolBox from "./ToolBox"

//TODO: clear button
//TODO: better styles
export function CreatorPanel(): JSX.Element {
  const capabilities = useSelector(getCapabilities)
  const {t} = useTranslation()
  const {theme} = useNkTheme()

  const styles = css({
    borderRadius: 0,
    // background: theme.colors.mutedColor,
    // color: theme.colors.foc,
    border: `none`,
    "&, &:focus": {
      boxShadow: `none`,
    },
  })

  const [filter, setFilter] = useState("")
  const clearFilter = useCallback(() => setFilter(""), [])

  return (
    <CollapsibleToolbar id="CREATOR-PANEL" title={t("panels.creator.title", "Creator panel")} isHidden={!capabilities.write}>
      <InputWithIcon
        className={styles}
        onChange={setFilter}
        onClear={clearFilter}
        value={filter}
        placeholder={t("panels.creator.filter.placeholder", "type here to filter...")}
      >
        <SearchIcon isEmpty={isEmpty(filter)}/>
      </InputWithIcon>
      <ToolBox filter={filter}/>
    </CollapsibleToolbar>
  )
}
