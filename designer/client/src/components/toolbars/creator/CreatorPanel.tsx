import { isEmpty } from "lodash";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { SearchIcon } from "../../table/SearchFilter";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ToolBox from "./ToolBox";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import { EventTrackingSelector, EventTrackingType, getEventTrackingProps } from "../../../containers/event-tracking";

export function CreatorPanel(props: ToolbarPanelProps): JSX.Element {
    const { t } = useTranslation();
    const [filter, setFilter] = useState("");
    const clearFilter = useCallback(() => setFilter(""), []);

    return (
        <ToolbarWrapper {...props} title={t("panels.creator.title", "Creator panel")}>
            <SearchInputWithIcon
                onChange={setFilter}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.creator.filter.placeholder", "type here to filter...")}
                {...getEventTrackingProps({ selector: EventTrackingSelector.ComponentsInScenario, event: EventTrackingType.SEARCH })}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </SearchInputWithIcon>
            <ToolBox filter={filter} />
        </ToolbarWrapper>
    );
}
