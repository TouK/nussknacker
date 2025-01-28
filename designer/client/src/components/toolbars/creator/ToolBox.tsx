import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import "react-treeview/react-treeview.css";
import { filterComponentsByLabel } from "../../../common/ProcessDefinitionUtils";
import { getProcessDefinitionData, getStickyNotesSettings } from "../../../reducers/selectors/settings";
import { ComponentGroup } from "../../../types";
import { ToolboxComponentGroup } from "./ToolboxComponentGroup";
import Tool from "./Tool";
import { useTranslation } from "react-i18next";
import { lighten, styled } from "@mui/material";

import { blendDarken, blendLighten } from "../../../containers/theme/helpers";
import { getLuminance } from "@mui/system/colorManipulator";
import { isPristine } from "../../../reducers/selectors/graph";
import { concat } from "lodash";
import { stickyNoteComponentGroup } from "./StickyNoteComponent";

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
        "&.disabled": {
            opacity: 0.4,
            cursor: "not-allowed !important",
        },
        "&:not(.disabled)": {
            cursor: "grab",
            "&:active": {
                cursor: "grabbing",
            },

            "&:hover": {
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

export default function ToolBox(props: { filter: string }): JSX.Element {
    const processDefinitionData = useSelector(getProcessDefinitionData);
    const stickyNotesSettings = useSelector(getStickyNotesSettings);
    const pristine = useSelector(isPristine);
    const { t } = useTranslation();

    const componentGroups: ComponentGroup[] = useMemo(() => processDefinitionData.componentGroups ?? [], [processDefinitionData]);
    const filters = useMemo(() => props.filter?.toLowerCase().split(/\s/).filter(Boolean), [props.filter]);
    const stickyNoteToolGroup = useMemo(() => stickyNoteComponentGroup(pristine), [pristine]);
    const groups = useMemo(() => {
        const stickyNotesEnabled = stickyNotesSettings ? stickyNotesSettings.enabled : false;
        const allComponentGroups = stickyNotesEnabled ? concat(componentGroups, stickyNoteToolGroup) : componentGroups;
        return allComponentGroups.map(filterComponentsByLabel(filters)).filter((g) => g.components.length > 0);
    }, [componentGroups, filters, stickyNoteToolGroup, stickyNotesSettings]);

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
