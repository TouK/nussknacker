import { Box, lighten } from "@mui/material";
import type { BoxProps } from "@mui/material/Box/Box";
import { getLuminance } from "@mui/system/colorManipulator";
import { cloneDeep } from "lodash";
import React, { useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import "react-treeview/react-treeview.css";
import { useSelector } from "react-redux";
import { useKey } from "rooks";

import { filterComponentsByLabel } from "../../../common/ProcessDefinitionUtils";
import { blendDarken, blendLighten } from "../../../containers/theme/helpers";
import { getProcessDefinitionData } from "../../../reducers/selectors/getProcessDefinitionData";
import type { ComponentGroup } from "../../../types/component";
import type { NodeType } from "../../../types/node";
import NodeUtils from "../../graph/NodeUtils";
import type { ToolProps } from "./Tool";
import Tool from "./Tool";
import { ToolboxComponentGroup } from "./ToolboxComponentGroup";

function StyledToolbox(props: Omit<BoxProps, "sx" | "ref">) {
    const ref = useRef<HTMLDivElement>();
    return (
        <Box
            {...props}
            ref={ref}
            sx={(theme) => {
                const color =
                    (ref.current && getComputedStyle(ref.current).getPropertyValue("--panelColor").trim()) ||
                    theme.palette.background.paper;
                return {
                    fontSize: "14px",
                    fontWeight: "600",
                    padding: 0,
                    minHeight: "2.5em",
                    ".tree-view": {
                        backgroundColor: "var(--panelColor)",
                    },

                    ".tree-view_item": {
                        backgroundColor: getLuminance(color) > 0.5 ? blendDarken(color, 0.04) : blendLighten(color, 0.04),
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
                        backgroundColor: "var(--panelColor)",
                        margin: theme.spacing(0.5, 0, 0.5, 0),
                        "&:hover": {
                            backgroundColor: "var(--panelColor)",
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
                };
            }}
        />
    );
}

export enum ComponentFilter {
    sourcesOnly,
    removeNoInputs,
    removeNoOutputs,
}

export type ToolBoxProps = {
    textFilter: string;
    filters?: ComponentFilter[];
    addTreeElement?: (group: ComponentGroup) => React.ReactElement | null;
    addGroupLabelElement?: (group: ComponentGroup) => React.ReactElement | null;
    data: ComponentGroup[];
    toolSelect?: Pick<ToolProps, "onClick" | "onDragEnd"> & {
        onEnter?: (item: NodeType, event: KeyboardEvent) => void;
    };
};

export default function ToolBox({ data = [], filters = [], ...props }: ToolBoxProps): React.JSX.Element {
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
                                case ComponentFilter.removeNoInputs:
                                    return NodeUtils.hasInputs(component.node);
                                case ComponentFilter.removeNoOutputs:
                                    return NodeUtils.hasOutputs(component.node, definitionData);
                                case ComponentFilter.sourcesOnly:
                                    return (
                                        ["Source", "FragmentInputDefinition"].includes(component.node.type) ||
                                        component.node.additionalFields?.creatorType
                                    );
                            }
                        }),
                    ),
                }))
                .map((group) => filterComponentsByLabel(textFilters)(group))
                .filter((g) => g.components.length > 0),
        [data, definitionData, filters, textFilters],
    );

    useKey(
        "Enter",
        (event) => {
            const { node, label } = groups[0].components[0];
            props.toolSelect.onEnter({ ...cloneDeep(node), id: label }, event);
        },
        { when: props.toolSelect?.onEnter && groups.length === 1 && groups[0].components.length === 1 },
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
                        toolSelect={props.toolSelect}
                    />
                ))
            ) : (
                <Tool nodeModel={null} label={t("panels.creator.filter.noMatch", "no matching components")} disabled />
            )}
        </StyledToolbox>
    );
}
