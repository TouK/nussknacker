import { styled } from "@mui/material";
import { MODAL_HEADER_HEIGHT } from "../../../../stylesheets/variables";
import { ComponentIcon } from "../../../toolbars/creator/ComponentIcon";

export const ComponentIconStyled = styled(ComponentIcon)`
    width: 18px;
    height: 24px;
`;

export const NodeDetailsModalTitle = styled("div")`
    height: ${MODAL_HEADER_HEIGHT}px;
    display: flex;
    padding-left: 7px;
    padding-right: 10px;
    align-items: center;
    svg {
        width: 18px;
        height: 18px;
    }
`;

export const ModalHeader = styled("div")`
    text-transform: lowercase;
    font-size: 14px !important;
    font-weight: 600;
    height: ${MODAL_HEADER_HEIGHT}px;
`;

export const ModalTitleContainer = styled("div")`
    height: ${MODAL_HEADER_HEIGHT}px;
    float: left;
    &:hover {
        cursor: grab;
    }
    &:active {
        cursor: grabbing;
    }
`;
