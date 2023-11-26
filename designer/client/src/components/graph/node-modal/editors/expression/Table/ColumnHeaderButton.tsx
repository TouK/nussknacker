import { styled } from "@mui/material";

export const ColumnHeaderButton = styled("button")`
    border: none;
    outline: none;
    height: calc(37px);
    width: calc(37px);
    font-size: 24px;
    background-color: var(--gdg-bg-header);
    color: var(--gdg-text-header);
    border-bottom: 1px solid var(--gdg-border-color);
    transition: background-color 200ms;
    cursor: pointer;

    &:hover {
        background-color: var(--gdg-bg-header-hovered);
    }
`;
