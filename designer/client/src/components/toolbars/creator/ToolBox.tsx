import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import "react-treeview/react-treeview.css";
import { filterComponentsByLabel, getCategoryComponentGroups } from "../../../common/ProcessDefinitionUtils";
import { getProcessCategory } from "../../../reducers/selectors/graph";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { ComponentGroup } from "../../../types";
import { ToolboxComponentGroup } from "./ToolboxComponentGroup";
import Tool from "./Tool";
import { useTranslation } from "react-i18next";
import { styled } from "@mui/material";
import { alpha } from "../../../containers/theme/helpers";

const StyledToolbox = styled("div")`
    font-size: 14px;
    font-weight: 600;
    padding: 0;
    padding-bottom: 0.5em;
    min-height: 2.5em;

    .tree-view {
        background-color: #3e3e3e;
    }

    .tree-view_item {
        background-color: #4d4d4d; //TODO: change me to MUI theme
        border: 1px solid ${alpha("#434343", 50)}; //TODO: change me to MUI theme
        cursor: pointer;
        display: flex;
        padding: 0 5px;
        height: 28px;
        line-height: 28px;

        &:hover {
            background-color: #444444; //TODO: change me to MUI theme
            color: #ccc; //TODO: change me to MUI theme
        }
    }

    .tree-view_arrow {
        cursor: inherit;
    }

    .toolWrapper {
        background-color: #464646;
        font-weight: 400;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .tool {
        margin-bottom: -1px;
        height: 28px;
        padding: 0 5px;
        line-height: 28px;
        border: 1px solid #434343; //TODO: change me to MUI theme

        &.disabled {
            opacity: 0.4;
            cursor: not-allowed !important;
        }

        &:not(.disabled) {
            cursor: -moz-grab;
            cursor: -webkit-grab;
            cursor: grab;
            &:active {
                cursor: -moz-grabbing;
                cursor: -webkit-grabbing;
                cursor: grabbing;
            }
            color: #b3b3b3;

            &:hover {
                background-color: darken(toolHoverBkgColor, 7%);
                color: #ccc; //TODO: change me to MUI theme
            }
        }
    }
    .toolIcon {
        height: 16px;
        width: 16px;
        display: inline-flex;
        vertical-align: middle;
        margin-right: 5px;
        margin-bottom: 2px;
    }

    .group-label {
        width: 95%;
    }
`;
export default function ToolBox(props: { filter: string }): JSX.Element {
    const processDefinitionData = useSelector(getProcessDefinitionData);
    const processCategory = useSelector(getProcessCategory);
    const { t } = useTranslation();

    const componentGroups: ComponentGroup[] = useMemo(
        () => getCategoryComponentGroups(processDefinitionData, processCategory),
        [processDefinitionData, processCategory],
    );

    const filters = useMemo(() => props.filter?.toLowerCase().split(/\s/).filter(Boolean), [props.filter]);

    const groups = useMemo(
        () => componentGroups.map(filterComponentsByLabel(filters)).filter((g) => g.components.length > 0),
        [componentGroups, filters],
    );

    return (
        <StyledToolbox id="toolbox">
            {groups.length ? (
                groups.map((componentGroup) => (
                    <ToolboxComponentGroup
                        key={componentGroup.name}
                        componentGroup={componentGroup}
                        highlights={filters}
                        flatten={groups.length === 1}
                    />
                ))
            ) : (
                <Tool nodeModel={null} label={t("panels.creator.filter.noMatch", "no matching components")} disabled />
            )}
        </StyledToolbox>
    );
}
