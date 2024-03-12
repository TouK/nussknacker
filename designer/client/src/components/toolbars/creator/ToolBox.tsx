import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import "react-treeview/react-treeview.css";
import { filterComponentsByLabel } from "../../../common/ProcessDefinitionUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { ComponentGroup } from "../../../types";
import { ToolboxComponentGroup } from "./ToolboxComponentGroup";
import Tool from "./Tool";
import { useTranslation } from "react-i18next";
import { darken, lighten, styled } from "@mui/material";

const StyledToolbox = styled("div")(({ theme }) => ({
    fontSize: "14px",
    fontWeight: "600",
    padding: 0,
    paddingBottom: "0.5em",
    minHeight: "2.5em",

    ".tree-view": {
        backgroundColor: theme.palette.background.paper,
    },

    ".tree-view_item": {
        backgroundColor: theme.palette.background.paper,
        border: `1px solid ${darken(theme.palette.background.paper, 0.2)}`,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        padding: "0 5px",
        height: "28px",
        lineHeight: "28px",

        "&:hover": {
            backgroundColor: theme.palette.action.hover,
            color: theme.custom.colors.secondaryColor,
        },
    },

    ".tree-view_children": {
        backgroundColor: theme.palette.background.paper,

        "&:hover": {
            backgroundColor: theme.palette.background.paper,
            color: theme.custom.colors.secondaryColor,
        },
    },
    ".tree-view_arrow": {
        cursor: "inherit",
    },

    ".toolWrapper": {
        backgroundColor: theme.palette.background.paper,
        fontWeight: 400,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },

    ".tool": {
        ...theme.typography.body2,
        marginBottom: "-1px",
        height: "28px",
        padding: "0 5px",
        lineHeight: "28px",
        border: `1px solid ${darken(theme.palette.background.paper, 0.2)}`,
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
                backgroundColor: theme.palette.background.paper,
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
