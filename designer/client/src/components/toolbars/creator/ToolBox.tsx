import { Box, lighten } from "@mui/material";
import type { BoxProps } from "@mui/material/Box/Box";
import { getLuminance } from "@mui/system/colorManipulator";
import { cloneDeep } from "lodash";
import React, { useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import "react-treeview/react-treeview.css";
import { useKey } from "rooks";

import { blendDarken, blendLighten } from "../../../containers/theme/helpers";
import { useFilteredComponentGroups } from "../../../reducers/selectors/getFilteredComponentGroups";
import type { ComponentGroup } from "../../../types/component";
import type { NodeType } from "../../../types/node";
import type { ToolProps } from "./Tool";
import Tool from "./Tool";
import { ToolboxCommands } from "./ToolboxCommands";
import { ToolboxComponentGroup } from "./ToolboxComponentGroup";

function StyledToolbox(props: Omit<BoxProps, "sx" | "ref">) {
    const ref = useRef<HTMLDivElement>(null);
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
    toolSelect?: Pick<ToolProps, "onClick" | "onDragEnd"> & {
        onEnter?: (item: NodeType, event: KeyboardEvent) => void;
        generic: (item: NodeType) => void;
    };
};

export default function ToolBox({ filters, ...props }: ToolBoxProps): React.JSX.Element {
    const { t } = useTranslation();

    const textFilters = useMemo(() => props.textFilter?.toLowerCase().split(/\s/).filter(Boolean), [props.textFilter]);
    const groups = useFilteredComponentGroups(filters, textFilters);

    useKey(
        "Enter",
        (event) => {
            const { node, label } = groups[0].components[0];
            props.toolSelect.onEnter({ ...cloneDeep(node), id: label, name: label }, event);
        },
        { when: props.toolSelect?.onEnter && groups.length === 1 && groups[0].components.length === 1 },
    );

    return (
        <StyledToolbox id="toolbox">
            <ToolboxCommands onSelect={props.toolSelect.generic} />
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
