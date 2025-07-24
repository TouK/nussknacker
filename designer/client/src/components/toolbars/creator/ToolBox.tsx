import { lighten, styled } from "@mui/material";
import { getLuminance } from "@mui/system/colorManipulator";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import "react-treeview/react-treeview.css";
import { useSelector } from "react-redux";

import { filterComponentsByLabel } from "../../../common/ProcessDefinitionUtils";
import { blendDarken, blendLighten } from "../../../containers/theme/helpers";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import type { ComponentGroup } from "../../../types";
import NodeUtils from "../../graph/NodeUtils";
import type { ToolProps } from "./Tool";
import Tool from "./Tool";
import { ToolboxComponentGroup } from "./ToolboxComponentGroup";

const StyledToolbox = styled("div")(({ theme }) => ({
    fontSize: "14px",
    fontWeight: "600",
    padding: 0,
    minHeight: "2.5em",
    ".tree-view": {
        backgroundColor: theme.palette.background.paper,
    },

    ".tree-view_item": {
        backgroundColor:
            getLuminance(theme.palette.background.paper) > 0.5
                ? blendDarken(theme.palette.background.paper, 0.04)
                : blendLighten(theme.palette.background.paper, 0.04),
        border: "none",
        borderLeft: 0,
        borderRight: 0,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        padding: theme.spacing(0, 2),
        height: "28px",
        lineHeight: "28px",

        "&:hover": {
            backgroundColor: theme.palette.action.hover,
            color: theme.palette.text.primary,
        },
    },

    ".tree-view_children": {
        backgroundColor: theme.palette.background.paper,
        margin: theme.spacing(0.5, 0, 0.5, 0),
        "&:hover": {
            backgroundColor: theme.palette.background.paper,
            color: theme.palette.text.primary,
        },
        "&-collapsed": {
            margin: 0,
        },
    },
    ".tree-view_arrow": {
        cursor: "inherit",
        transform: "rotate(-90deg)",
        marginRight: 0,
        position: "absolute",
        "&:after": {
            content: "'‹'",
        },
        "&-collapsed": {
            transform: "rotate(-180deg)",
        },
    },
    ".toolWrapper": {
        fontWeight: 400,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    ".tool": {
        ...theme.typography.body2,
        paddingLeft: theme.spacing(4),
        padding: theme.spacing(0.75, 0.5, 0.75, 4),
        border: "none",
        borderRight: 0,
        userSelect: "none",
        "&.disabled": {
            opacity: 0.4,
            cursor: "not-allowed !important",
        },
        "&:not(.disabled)": {
            cursor: "grab",
            "&:active": {
                cursor: "grabbing",
            },

            "&:hover, &:focus-within": {
                backgroundColor: theme.palette.action.hover,
                color: lighten(theme.palette.text.primary, 0.2),
            },
        },
    },
    ".toolIcon": {
        height: "16px",
        width: "16px",
        display: "inline-flex",
        verticalAlign: "middle",
        marginRight: "5px",
        marginBottom: "2px",
    },
}));

type ComponentFilter = "removeNoInputs" | "removeNoOutputs";
export type ToolBoxProps = {
    textFilter: string;
    filters?: ComponentFilter[];
    addTreeElement?: (group: ComponentGroup) => React.ReactElement | null;
    addGroupLabelElement?: (group: ComponentGroup) => React.ReactElement | null;
    data: ComponentGroup[];
    onSelect?: ToolProps["onClick"];
};

export default function ToolBox({ data = [], filters = [], ...props }: ToolBoxProps): JSX.Element {
    const { t } = useTranslation();
    const definitionData = useSelector(getProcessDefinitionData);

    const textFilters = useMemo(() => props.textFilter?.toLowerCase().split(/\s/).filter(Boolean), [props.textFilter]);

    const groups = useMemo(
        () =>
            data
                .map((group) => ({
                    ...group,
                    components: group.components.filter((component) =>
                        filters.every((f) => {
                            switch (f) {
                                case "removeNoInputs":
                                    return NodeUtils.hasInputs(component.node);
                                case "removeNoOutputs":
                                    return NodeUtils.hasOutputs(component.node, definitionData);
                            }
                        }),
                    ),
                }))
                .map((group) => filterComponentsByLabel(textFilters)(group))
                .filter((g) => g.components.length > 0),
        [data, definitionData, filters, textFilters],
    );

    return (
        <StyledToolbox id="toolbox">
            {groups.length ? (
                groups.map((componentGroup) => (
                    <ToolboxComponentGroup
                        key={componentGroup.name}
                        componentGroup={componentGroup}
                        highlights={textFilters}
                        flatten={groups.length === 1}
                        addTreeElement={props.addTreeElement?.(componentGroup)}
                        addGroupLabelElement={props.addGroupLabelElement?.(componentGroup)}
                        onSelect={props.onSelect}
                    />
                ))
            ) : (
                <Tool
                    nodeModel={null}
                    label={t("panels.creator.filter.noMatch", "no matching components")}
                    onClick={props.onSelect}
                    disabled
                />
            )}
        </StyledToolbox>
    );
}
