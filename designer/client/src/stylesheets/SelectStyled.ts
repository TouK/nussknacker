import { alpha, css, Theme } from "@mui/material";
import { CSSProperties } from "react";
import { CSSObjectWithLabel } from "react-select";
import { blendLighten, getBorderColor } from "../containers/theme/helpers";

export const selectStyled = (theme: Theme) => {
    const commonNodeInput = (padding: CSSProperties["padding"]) => css`
        width: 100%;
        padding: 0 ${padding};
        border: none;
        background-color: ${theme.palette.background.paper};
        color: ${theme.palette.text.secondary};
        font-weight: 400;
        font-size: 14px;
        outline: 1px solid ${blendLighten(theme.palette.background.paper, 0.05)};
    `;

    const control = (base: CSSObjectWithLabel, isFocused: boolean, isDisabled: boolean) => css`
        ${base};
        background-color: ${isDisabled ? theme.palette.action.disabledBackground : theme.palette.background.paper};
        color: ${theme.palette.action.disabled} !important;
        max-height: 35px;
        min-height: 35px;
        border: none;
        border-radius: 0;
        box-shadow: 0;
        outline: 1px solid ${isFocused ? theme.palette.primary.main : getBorderColor(theme)} !important;
    `;

    const menuOption = (base: CSSObjectWithLabel, isSelected: boolean, isDisabled: boolean) => css`
        ${base}
        ${commonNodeInput("10px")};
        height: 25px;
        line-height: 25px;
        border: 1px;
        border-radius: 0;
        background-color: ${isSelected
            ? blendLighten(theme.palette.background.paper, 0.15)
            : isDisabled
            ? "none"
            : theme.palette.background.paper};
        color: ${isDisabled && theme.palette.action.disabled};
        &:hover {
            color: inherit;
            background-color: ${!isDisabled && theme.palette.action.hover};
        }
    `;

    const input = (base: CSSObjectWithLabel) => css`
        ${base};
        ${commonNodeInput("10px")}; //TODO input hides partially due to padding...
        outline: none;
    `;

    const singleValue = (base: CSSObjectWithLabel, isDisabled: boolean) => css`
        ${base};
        ${commonNodeInput("0")}; //TODO input hides partially due to padding...
        background-color: ${isDisabled ? "inherit" : theme.palette.background.paper};
        position: absolute;
        outline: none;
    `;

    const menuList = (base: CSSObjectWithLabel) => css`
        ${base};
        padding-top: 0;
        padding-bottom: 0;

        ::-webkit-scrollbar {
            width: 5px;
            height: 0;
        }

        ::-webkit-scrollbar-track {
            background: ${blendLighten(theme.palette.background.paper, 0.5)};
        }

        ::-webkit-scrollbar-thumb {
            background: ${alpha(theme.palette.background.paper, 0.85)};
        }

        ::-webkit-scrollbar-thumb:hover {
            background: ${theme.palette.action.hover};
        }
    `;

    const menuPortal = (base: CSSObjectWithLabel) => css`
        ${base};
        z-index: 2000;
    `;

    const menu = (base: CSSObjectWithLabel) => css`
        ${base};
        z-index: 2;
        border-radius: 0;
        background-color: ${theme.palette.background.paper};
    `;

    const valueContainer = (base: CSSObjectWithLabel) => css`
        ${base};
    `;

    return {
        control,
        menu,
        menuList,
        menuOption,
        menuPortal,
        input,
        singleValue,
        valueContainer,
    };
};
