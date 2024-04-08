import { css, Theme } from "@mui/material";

export const exportStyled = (theme: Theme) => css`
    .graph-export {
        color: ${theme.palette.common.white};
    }
    .graph-export .link-tools {
        display: none;
    }

    .graph-export [no-export] [noExport],
    .connection-wrap,
    .marker-arrowheads {
        display: none;
    }

    .graph-export .arrow-marker path {
        fill: ${theme.palette.text.secondary};
    }

    .graph-export .joint-theme-default .connection {
        stroke: ${theme.palette.text.secondary};
        fill: none;
    }

    .graph-export .joint-theme-default .connection,
    .joint-port-body,
    &.joint-type-esp-model rect {
        stroke-width: 2px;
    }

    .graph-export .joint-theme-default [disabled="true"] {
        opacity: 1;
        fill: ${theme.palette.text.secondary};
    }
`;
