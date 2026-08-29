import EditIcon from "@mui/icons-material/Edit";
import { Box, Chip, IconButton, Tooltip, Typography, alpha, useTheme } from "@mui/material";
import i18next from "i18next";
import React, { useCallback } from "react";

import { getScenario, getSelectionState } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import { useWindows } from "../../../windowManager/useWindows";
import { getComponentsDefinition } from "../../graph/node-modal/NodeDetailsContent/selectors";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { getNodeSummaryItems } from "./nodeDetailsUtils";

export function NodeDetailsPanel(props: ToolbarPanelProps): React.JSX.Element {
    const theme = useTheme();
    const selectionState = useAppSelector(getSelectionState);
    const scenario = useAppSelector(getScenario);
    const components = useAppSelector(getComponentsDefinition);
    const { openNodeWindow } = useWindows();

    const selectedIds = selectionState ?? [];
    const nodes = scenario?.scenarioGraph?.nodes ?? [];
    const edges = scenario?.scenarioGraph?.edges ?? [];
    const selectedNode = selectedIds.length === 1 ? nodes.find((n) => n.id === selectedIds[0]) : undefined;

    const handleEdit = useCallback(() => {
        if (selectedNode && scenario) {
            openNodeWindow(selectedNode, scenario);
        }
    }, [selectedNode, scenario, openNodeWindow]);

    const items = selectedNode ? getNodeSummaryItems(selectedNode, components, { edges, nodes }) : [];

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.nodeDetails.title", "Node details")}>
            <Box px={1} pb={1}>
                {selectedIds.length === 0 && (
                    <Typography variant="body2" color="text.secondary" sx={{ fontStyle: "italic", mt: 0.5 }}>
                        {i18next.t("panels.nodeDetails.noSelection", "No node selected")}
                    </Typography>
                )}

                {selectedIds.length > 1 && (
                    <Typography variant="body2" color="text.secondary" sx={{ fontStyle: "italic", mt: 0.5 }}>
                        {i18next.t("panels.nodeDetails.multiSelection", "{{count}} nodes selected", { count: selectedIds.length })}
                    </Typography>
                )}

                {selectedNode && (
                    <>
                        <Box display="flex" alignItems="center" justifyContent="space-between" mt={0.5} mb={1.5} gap={1}>
                            <Box display="flex" alignItems="center" gap={1} minWidth={0}>
                                <Tooltip title={selectedNode.name}>
                                    <Typography variant="subtitle2" noWrap fontWeight={600}>
                                        {selectedNode.name}
                                    </Typography>
                                </Tooltip>
                                {selectedNode.isDisabled && (
                                    <Chip label={i18next.t("panels.nodeDetails.disabled", "disabled")} size="small" color="warning" />
                                )}
                            </Box>
                            <Tooltip title={i18next.t("panels.nodeDetails.edit", "Edit node")}>
                                <IconButton
                                    size="small"
                                    onClick={handleEdit}
                                    sx={{ flexShrink: 0 }}
                                    aria-label={i18next.t("panels.nodeDetails.edit", "Edit node")}
                                >
                                    <EditIcon fontSize="small" />
                                </IconButton>
                            </Tooltip>
                        </Box>

                        {items.length > 0 && (
                            <Box display="flex" flexDirection="column" gap={0.5}>
                                {items.map((item) => (
                                    <Box
                                        key={item.label}
                                        sx={{
                                            borderLeft: `3px solid ${theme.palette.primary.main}`,
                                            pl: 1,
                                            py: 0.25,
                                            borderRadius: "0 4px 4px 0",
                                            backgroundColor: alpha(theme.palette.primary.main, 0.06),
                                        }}
                                    >
                                        <Typography
                                            variant="caption"
                                            color="text.secondary"
                                            display="block"
                                            sx={{ lineHeight: 1.4, textTransform: "uppercase", fontSize: "0.65rem", letterSpacing: 0.4 }}
                                        >
                                            {item.label}
                                        </Typography>
                                        <Tooltip title={item.value} enterDelay={600} placement="left">
                                            <Typography
                                                variant="body2"
                                                sx={{
                                                    fontFamily: "monospace",
                                                    fontSize: "0.72rem",
                                                    overflow: "hidden",
                                                    textOverflow: "ellipsis",
                                                    whiteSpace: "nowrap",
                                                    cursor: "default",
                                                    color: theme.palette.text.primary,
                                                }}
                                            >
                                                {item.value}
                                            </Typography>
                                        </Tooltip>
                                    </Box>
                                ))}
                            </Box>
                        )}
                    </>
                )}
            </Box>
        </ToolbarWrapper>
    );
}
