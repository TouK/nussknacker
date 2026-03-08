import AccountTreeIcon from "@mui/icons-material/AccountTree";
import CloseIcon from "@mui/icons-material/Close";
import { Box, Divider, IconButton, useTheme } from "@mui/material";
import { getLuminance } from "@mui/system/colorManipulator";
import React from "react";

import nodeAttributes from "../../assets/json/nodeAttributes.json";
import { blendLighten, blendDarken } from "../../containers/theme/helpers";
import type { NodeType } from "../../types/node";
import { IconModalTitle } from "../graph/node-modal/nodeDetails/IconModalTitle";
import { Subtype } from "../graph/node-modal/nodeDetails/Subtype";

interface Props {
    node: NodeType;
    onClose: () => void;
}

export function DataMapperDialogTitle({ node, onClose }: Props): React.JSX.Element {
    const theme = useTheme();
    const iconFill = theme.palette.custom.getNodeStyles(node.type).fill;
    const bg =
        getLuminance(theme.palette.background.paper) > 0.5
            ? blendDarken(theme.palette.background.paper, 0.1)
            : blendLighten(theme.palette.background.paper, 0.1);
    const nodeLabel = (nodeAttributes as Record<string, { name: string }>)[node.type]?.name ?? node.type;

    const icon = (
        <Box
            sx={{
                width: 30,
                height: 30,
                borderRadius: 0.5,
                backgroundColor: iconFill,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
            }}
        >
            <AccountTreeIcon sx={{ color: "white", fontSize: 18 }} />
        </Box>
    );

    return (
        <Box
            sx={{
                display: "flex",
                alignItems: "center",
                backgroundColor: bg,
                px: 1,
                height: 40,
                flexShrink: 0,
                userSelect: "none",
            }}
        >
            <IconModalTitle startIcon={icon} sx={{ textTransform: "lowercase", span: { px: 1.6 } }}>
                data mapper
            </IconModalTitle>

            <Divider orientation="vertical" flexItem sx={{ my: 1 }} />
            <Subtype sx={{ px: 1.6 }}>{nodeLabel}</Subtype>

            <Box sx={{ flex: 1 }} />

            <IconButton size="small" onClick={onClose} sx={{ color: "text.primary" }}>
                <CloseIcon sx={{ fontSize: 18 }} />
            </IconButton>
        </Box>
    );
}
