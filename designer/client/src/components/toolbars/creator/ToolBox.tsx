import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import "react-treeview/react-treeview.css";
import { filterComponentsByLabel } from "../../../common/ProcessDefinitionUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { ComponentGroup } from "../../../types";
import { ToolboxComponentGroup } from "./ToolboxComponentGroup";
import Tool from "./Tool";
import { useTranslation } from "react-i18next";
import { css, darken, styled } from "@mui/material";
import { alpha } from "../../../containers/theme/helpers";

const StyledToolbox = styled("div")(
    ({ theme }) => css`
        font-size: 14px;
        font-weight: 600;
        padding: 0;
        padding-bottom: 0.5em;
        min-height: 2.5em;

        .tree-view {
            background-color: ${theme.custom.colors.mineShaft};
        }

        .tree-view_item {
            background-color: ${theme.custom.colors.primaryBackground};
            border: 1px solid ${alpha(theme.custom.colors.arsenic, 50)};
            cursor: pointer;
            display: flex;
            align-items: center;
            padding: 0 5px;
            height: 28px;
            line-height: 28px;

            &:hover {
                background-color: ${theme.custom.colors.arsenic};
                color: ${theme.custom.colors.secondaryColor};
            }
        }

        .tree-view_arrow {
            cursor: inherit;
        }

        .toolWrapper {
            background-color: ${theme.custom.colors.woodCharcoal};
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
            border: 1px solid ${theme.custom.colors.arsenic};
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
                color: ${theme.custom.colors.canvasBackground};

                &:hover {
                    background-color: ${darken(theme.custom.colors.charcoal, 0.07)};
                    color: ${theme.custom.colors.secondaryColor};
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
    `,
);

export default function ToolBox(props: { filter: string }): JSX.Element {
    const processDefinitionData = useSelector(getProcessDefinitionData);
    const { t } = useTranslation();

    const componentGroups: ComponentGroup[] = useMemo(() => processDefinitionData.componentGroups, [processDefinitionData]);

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
