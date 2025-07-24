import type { ModuleUrl } from "@touk/federated-component";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useKey } from "rooks";

import { useUserSettings } from "../../../common/userSettings";
import { EventTrackingSelector, getEventTrackingProps } from "../../../containers/event-tracking";
import { getAdditionalComponents } from "../../../reducers/cloudData";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { isCloudInstance } from "../../../reducers/selectors/isCloudInstance";
import type { NodeType } from "../../../types";
import { useSidePanel } from "../../sidePanels/SidePanelsContext";
import { SearchIcon } from "../../table/SearchFilter";
import type { Focusable } from "../../themed/InputWithIcon";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { AddGroupElement } from "./CreatorPanel";
import { globalEventBus } from "./globalEventBus";
import type { ToolBoxProps } from "./ToolBox";
import ToolBox from "./ToolBox";
import { useOutsideInteractionRef } from "./useOutsideInteractionRef";

type CreatorPanelProps2 = ToolbarPanelProps & {
    additionalParams?: {
        addGroupElement?: ModuleUrl;
    };
};

export function CreatorPanel2({ additionalParams, ...props }: CreatorPanelProps2): JSX.Element {
    const { t } = useTranslation();
    const [textFilter, setTextFilter] = useState("");
    const clearFilter = useCallback(() => setTextFilter(""), []);

    const dispatch = useDispatch();
    const [settings] = useUserSettings();
    const isCloud = useSelector(isCloudInstance);
    useEffect(() => {
        if (isCloud && settings["cloud.showIntegrationsCreators"]) {
            dispatch(getAdditionalComponents());
        }
    }, [dispatch, isCloud, settings]);
    const searchRef = useRef<Focusable>();

    const { isOpened, toggleCollapse, side } = useSidePanel();

    const [filters, setFilters] = useState<ToolBoxProps["filters"]>([]);
    useEffect(() => {
        return globalEventBus.on("openNodeSelector", (filters) => {
            setFilters(filters);
            if (!isOpened) {
                toggleCollapse();
            }
            setTimeout(() => {
                searchRef.current?.focus();
            }, 500);
        });
    }, [isOpened, toggleCollapse]);

    useEffect(() => {
        return globalEventBus.on("closeNodeSelector", () => {
            toggleCollapse();
        });
    }, [toggleCollapse]);

    useEffect(() => {
        if (!isOpened) {
            setFilters([]);
        }
    }, [isOpened]);

    const { componentGroups } = useSelector(getProcessDefinitionData);

    const closeHandler = useCallback(
        <E extends Event>(event: E, item?: NodeType) => {
            globalEventBus.emit("closeNodeSelector", { side, event, item });
        },
        [side],
    );

    const [interactionRef] = useOutsideInteractionRef(closeHandler, isOpened);
    useKey("Escape", closeHandler, { when: isOpened });

    return (
        <div ref={interactionRef}>
            <ToolbarWrapper {...props} onExpand={() => searchRef.current?.focus()}>
                <SearchInputWithIcon
                    ref={searchRef}
                    onChange={setTextFilter}
                    onClear={clearFilter}
                    value={textFilter}
                    placeholder={t("panels.creator.filter.placeholder", "type here to filter...")}
                    {...getEventTrackingProps({ selector: EventTrackingSelector.ComponentsInScenario })}
                >
                    <SearchIcon isEmpty={isEmpty(textFilter)} />
                </SearchInputWithIcon>
                <ToolBox
                    textFilter={textFilter}
                    filters={filters}
                    data={componentGroups}
                    addGroupLabelElement={({ name }) => (
                        <AddGroupElement
                            url={additionalParams?.addGroupElement}
                            variant="small"
                            componentGroup={name}
                            {...additionalParams}
                            {...props}
                        />
                    )}
                    addTreeElement={({ name }) => (
                        <AddGroupElement
                            url={additionalParams?.addGroupElement}
                            variant="big"
                            className="tool"
                            componentGroup={name}
                            {...additionalParams}
                            {...props}
                        />
                    )}
                    onSelect={(event, item) => {
                        closeHandler(event.nativeEvent, item);
                    }}
                />
            </ToolbarWrapper>
        </div>
    );
}
