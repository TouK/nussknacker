import { css } from "@mui/material";

export const exportStyled = css`
    .graph-export {
        color: white;
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
        fill: #b3b3b3;
    }

    .graph-export .joint-theme-default .connection {
        stroke: #b3b3b3;
        fill: none;
    }

    .graph-export .joint-theme-default .connection,
    .joint-port-body,
    &.joint-type-esp-model rect {
        stroke-width: 2px;
    }

    .graph-export .joint-theme-default [disabled="true"] {
        opacity: 1;
        fill: gray;
    }
`;
