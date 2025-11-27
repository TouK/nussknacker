import type { ModuleUrl } from "@touk/federated-component";
import type { g } from "jointjs";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { ErrorBoundary } from "react-error-boundary";
import { useTranslation } from "react-i18next";

import { addNodeConnected, addNodeInject, addNodePlain, addNodeReplace } from "../../../actions/nk/editNode";
import { isDynamic } from "../../../actions/nk/ui/panelSide";
import { getEventTrackingProps } from "../../../containers/event-tracking/helpers";
import { EventTrackingSelector } from "../../../containers/event-tracking/use-register-tracking-events";
import { useNodeCreationHandler } from "../../../containers/NodeCreationHandler";
import { getAdditionalComponents } from "../../../reducers/cloudData";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { isCloudInstance } from "../../../reducers/selectors/isCloudInstance";
import { getUserSettings } from "../../../reducers/selectors/userSettings";
import { addListenerTyped, useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { Edge } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import { RemoteComponent } from "../../RemoteComponent";
import { useSidePanel } from "../../sidePanels/SidePanelsContext";
import { SearchIcon } from "../../table/SearchFilter";
import type { Focusable } from "../../themed/InputWithIcon";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { selectComponent } from "./nodeSelectorActions";
import type { ToolBoxProps } from "./ToolBox";
import ToolBox from "./ToolBox";

type CreatorPanelProps = ToolbarPanelProps & {
    additionalParams?: {
        addGroupElement?: ModuleUrl;
    };
};

const AddGroupElement = <P extends NonNullable<{ url: ModuleUrl; componentGroup: string }>>(props: P) => {
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

export function CreatorPanel({ additionalParams, ...props }: CreatorPanelProps): React.JSX.Element {
    const { t } = useTranslation();
    const [filters, setFilters] = useState<ToolBoxProps["filters"]>([]);
    const [textFilter, setTextFilter] = useState("");
    const clearFilter = useCallback(() => setTextFilter(""), []);

    const dispatch = useAppDispatch();
    const settings = useAppSelector(getUserSettings);
    const isCloud = useAppSelector(isCloudInstance);
    useEffect(() => {
        if (isCloud && settings["cloud.showIntegrationsCreators"]) {
            dispatch(getAdditionalComponents());
        }
    }, [dispatch, isCloud, settings]);
    const searchRef = useRef<Focusable>();

    const { isOpened, toggleCollapse, side } = useSidePanel();

    const intendedCoords = useRef<g.PlainPoint>(null);
    const intendedConnecion = useRef<Edge>(null);
    useEffect(
        () =>
            dispatch(
                addListenerTyped("OPEN_NODE_SELECTOR", (action) => {
                    const { data } = action;
                    if (data.side !== side) return;
                    intendedCoords.current = data.fromPoint;
                    intendedConnecion.current = data.withEdge;
                    setFilters(data.filters || []);
                    setTextFilter("");
                    if (!isOpened) {
                        toggleCollapse();
                    }
                    setTimeout(() => {
                        searchRef.current?.focus();
                    }, 500);
                }),
            ),
        [dispatch, isOpened, side, toggleCollapse],
    );

    const { componentGroups } = useAppSelector(getProcessDefinitionData);

    const selectHandler = useCallback(
        (node?: NodeType, point?: g.PlainPoint, currentNode?: NodeType, currentEdge?: Edge) => {
            const offset = point || intendedCoords.current;
            const edge = currentEdge || intendedConnecion.current;

            dispatch(selectComponent(side, node));

            dispatch((dispatch) => {
                if (currentNode) {
                    return dispatch(addNodeReplace(node, currentNode));
                }
                if (edge?.from && edge?.to) {
                    return dispatch(addNodeInject(node, edge, offset));
                }
                if (edge) {
                    return dispatch(addNodeConnected(node, edge, offset));
                }
                return dispatch(addNodePlain(node, offset));
            });

            intendedCoords.current = null;
            intendedConnecion.current = null;
            searchRef.current.blur();
        },
        [dispatch, side],
    );

    useNodeCreationHandler({ panelSide: side, when: isDynamic(side) });

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
                toolSelect={{
                    onClick: (item) => {
                        if (!isDynamic(side)) return;
                        selectHandler(item);
                    },
                    onDragEnd: (item, monitor) => {
                        if (!monitor.didDrop()) return;
                        const { offset, currentNode, currentEdge } = monitor.getDropResult();
                        selectHandler(item, offset, currentNode, currentEdge);
                    },
                    onEnter: (item) => {
                        if (!searchRef.current.hasFocus) return;
                        selectHandler(item);
                    },
                }}
            />
        </ToolbarWrapper>
    );
}
