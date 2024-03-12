import { css, darken, Theme } from "@mui/material";
import { CSSProperties } from "react";
import { CSSObjectWithLabel } from "react-select";
import { alpha } from "../containers/theme/helpers";

export const selectStyled = (theme: Theme) => {
    const commonNodeInput = (padding: CSSProperties["padding"]) => css`
        width: 100%;
        padding: 0 ${padding};
        border: none;
        background-color: ${theme.palette.background.paper};
        color: ${theme.custom.colors.secondaryColor};
        font-weight: 400;
        font-size: 14px;
        outline: 1px solid ${alpha(theme.custom.colors.primaryColor, 0.075)};
    `;

    const control = (base: CSSObjectWithLabel, isFocused: boolean, isDisabled: boolean) => css`
        ${base};
        background-color: ${theme.palette.background.paper};
        max-height: 35px;
        min-height: 35px;
        border: 0;
        border-radius: 0;
        color: ${theme.custom.colors.secondaryColor};
        box-shadow: 0;

        ${isFocused &&
        css`
            outline: 2px solid ${theme.custom.colors.cobalt} !important;
            outline-offset: -1px !important;
        `}
        ${isDisabled &&
        css`
            background-color: ${theme.custom.colors.charcoal} !important;
        `}
    `;

    const menuOption = (base: CSSObjectWithLabel, isSelected: boolean, isFocused: boolean) => css`
        ${base}
        ${commonNodeInput("10px")};
        height: 25px;
        line-height: 25px;
        border: 1px;
        border-radius: 0;

        ${isSelected &&
        css`
            background-color: ${theme.custom.colors.cobalt};
        `}

        ${isFocused &&
        css`
            background-color: ${darken(theme.custom.colors.cobalt, 0.5)};
        `}
    &:hover {
            background-color: ${darken(theme.custom.colors.cobalt, 0.5)};
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
        position: absolute;
        outline: none;
        ${isDisabled &&
        css`
            background-color: ${theme.custom.colors.charcoal} !important;
        `}
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
            background: ${theme.custom.colors.doveGray};
        }

        ::-webkit-scrollbar-thumb {
            background: ${theme.custom.colors.gray};
        }

        ::-webkit-scrollbar-thumb:hover {
            background: ${theme.custom.colors.emperor};
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
    `;

    const valueContainer = (base: CSSObjectWithLabel, hasValue: boolean) => css`
        ${base};
        ${hasValue &&
        css`
            background-color: ${theme.palette.background.paper}
            color: ${theme.custom.colors.secondaryColor}
        `}
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
