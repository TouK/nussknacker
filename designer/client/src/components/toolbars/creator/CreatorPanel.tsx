import type { ModuleUrl } from "@touk/federated-component";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { ErrorBoundary } from "react-error-boundary";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { useUserSettings } from "../../../common/userSettings";
import { EventTrackingSelector, getEventTrackingProps } from "../../../containers/event-tracking";
import { getAdditionalComponents } from "../../../reducers/cloudData";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import { isCloudInstance } from "../../../reducers/selectors/isCloudInstance";
import { RemoteComponent } from "../../RemoteComponent";
import { SearchIcon } from "../../table/SearchFilter";
import type { Focusable } from "../../themed/InputWithIcon";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ToolBox from "./ToolBox";

type CreatorPanelProps = ToolbarPanelProps & {
    additionalParams?: {
        addGroupElement?: ModuleUrl;
    };
};

export const AddGroupElement = <P extends NonNullable<{ url: ModuleUrl; componentGroup: string }>>(props: P) => {
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
    const [filter, setFilter] = useState("");
    const clearFilter = useCallback(() => setFilter(""), []);

    const dispatch = useDispatch();
    const [settings] = useUserSettings();
    const isCloud = useSelector(isCloudInstance);
    useEffect(() => {
        if (isCloud && settings["cloud.showIntegrationsCreators"]) {
            dispatch(getAdditionalComponents());
        }
    }, [dispatch, isCloud, settings]);
    const searchRef = useRef<Focusable>();

    const { componentGroups } = useSelector(getProcessDefinitionData);

    return (
        <ToolbarWrapper {...props} title={t("panels.creator.title", "Creator panel")} onExpand={() => searchRef.current?.focus()}>
            <SearchInputWithIcon
                ref={searchRef}
                onChange={setFilter}
                onClear={clearFilter}
                value={filter}
                placeholder={t("panels.creator.filter.placeholder", "type here to filter...")}
                {...getEventTrackingProps({ selector: EventTrackingSelector.ComponentsInScenario })}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </SearchInputWithIcon>
            <ToolBox
                textFilter={filter}
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
            />
        </ToolbarWrapper>
    );
}
