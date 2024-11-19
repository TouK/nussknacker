import { ModuleUrl } from "@touk/federated-component";
import { isEmpty } from "lodash";
import React, { useCallback, useState } from "react";
import { ErrorBoundary } from "react-error-boundary";
import { useTranslation } from "react-i18next";
import { EventTrackingSelector, getEventTrackingProps } from "../../../containers/event-tracking";
import { RemoteComponent } from "../../RemoteComponent";
import { SearchIcon } from "../../table/SearchFilter";
import { SearchInputWithIcon } from "../../themed/SearchInput";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import ToolBox from "./ToolBox";

type CreatorPanelProps = ToolbarPanelProps & {
    additionalParams?: {
        addGroupElement?: ModuleUrl;
    };
};

const RemoteElement = <P extends NonNullable<{ url: ModuleUrl; componentGroup: string }>>(props: P) => (
    <ErrorBoundary fallback={null}>
        <RemoteComponent {...props} />
    </ErrorBoundary>
);

export function CreatorPanel({ additionalParams, ...props }: CreatorPanelProps): JSX.Element {
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
                {...getEventTrackingProps({ selector: EventTrackingSelector.ComponentsInScenario })}
            >
                <SearchIcon isEmpty={isEmpty(filter)} />
            </SearchInputWithIcon>
            <ToolBox
                filter={filter}
                addGroupLabelElement={({ name }) => (
                    <RemoteElement
                        url={additionalParams.addGroupElement}
                        variant="small"
                        componentGroup={name}
                        {...additionalParams}
                        {...props}
                    />
                )}
                addTreeElement={({ name }) => (
                    <RemoteElement
                        url={additionalParams.addGroupElement}
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
