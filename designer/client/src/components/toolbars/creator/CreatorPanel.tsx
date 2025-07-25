import type { ModuleUrl } from "@touk/federated-component";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { ErrorBoundary } from "react-error-boundary";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useKey } from "rooks";

import { useUserSettings } from "../../../common/userSettings";
import { EventTrackingSelector, getEventTrackingProps } from "../../../containers/event-tracking";
import { getAdditionalComponents } from "../../../reducers/cloudData";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { isCloudInstance } from "../../../reducers/selectors/isCloudInstance";
import type { NodeType } from "../../../types";
import { RemoteComponent } from "../../RemoteComponent";
import { isDynamic } from "../../sidePanels/CollapsiblePanel";
import { useSidePanel } from "../../sidePanels/SidePanelsContext";
import { SearchIcon } from "../../table/SearchFilter";
import type { Focusable } from "../../themed/InputWithIcon";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import type { OpenNodeSelectorParams } from "./globalEventBus";
import { globalEventBus } from "./globalEventBus";
import type { ToolBoxProps } from "./ToolBox";
import ToolBox from "./ToolBox";
import { useOutsideInteraction } from "./useOutsideInteraction";

type CreatorPanelProps = ToolbarPanelProps & {
    additionalParams?: {
        addGroupElement?: ModuleUrl;
    };
};

export const AddGroupElement = <
    P extends NonNullable<{
        url: ModuleUrl;
        componentGroup: string;
    }>,
>(
    props: P,
) => {
    const { t } = useTranslation();
    return props.url ? (
        <ErrorBoundary fallback={null}>
            <RemoteComponent
                {...props}
                label={t("panels.creator.addMore", "Add more {{componentGroup}}...", { componentGroup: props.componentGroup })}
            />
        </ErrorBoundary>
    ) : null;
};

export function CreatorPanel({ additionalParams, ...props }: CreatorPanelProps): JSX.Element {
    const { t } = useTranslation();
    const [filters, setFilters] = useState<ToolBoxProps["filters"]>([]);
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

    const { isOpened, toggleCollapse, side, ref } = useSidePanel();

    const dataRef = useRef<OpenNodeSelectorParams>();
    useEffect(() => {
        if (!isDynamic(side)) return;
        return globalEventBus.on("openNodeSelector", (data) => {
            if (data.side !== side) return;
            dataRef.current = data;
            setFilters(data.filters || []);
            setTextFilter("");
            if (!isOpened) {
                toggleCollapse();
            }
            setTimeout(() => {
                searchRef.current?.focus();
            }, 500);
        });
    }, [side, isOpened, toggleCollapse]);

    useEffect(() => {
        if (!isDynamic(side)) return;
        return globalEventBus.on("closeNodeSelector", (data) => {
            if (data.side !== side) return;
            toggleCollapse();
        });
    }, [side, toggleCollapse]);

    const { componentGroups } = useSelector(getProcessDefinitionData);

    const closeHandler = useCallback(
        (item?: NodeType) => {
            globalEventBus.emit("closeNodeSelector", {
                side,
                item,
                point: dataRef.current?.point,
                edge: dataRef.current?.edge,
            });
        },
        [side],
    );

    useOutsideInteraction(ref, () => closeHandler(), isOpened);
    useKey("Escape", () => closeHandler(), { when: isOpened });

    return (
        <ToolbarWrapper {...props} title={t("panels.creator.title", "Creator panel")} onExpand={() => searchRef.current?.focus()}>
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
                onSelect={(item) => {
                    closeHandler(item);
                }}
            />
        </ToolbarWrapper>
    );
}
