import React, { PropsWithChildren } from "react";
import { AddButton } from "./buttons/AddButton";
import { css, styled } from "@mui/material";
import { buttonBase } from "../../focusableStyled";

interface FieldsControlProps {
    readOnly?: boolean;
}

const Styled = styled("div")(
    ({ theme }) => css`
        .addRemoveButton {
            ${buttonBase(theme)};
            width: 35px;
            height: 35px;
            font-weight: bold;
            font-size: 20px;
        }

        .fieldName {
            width: 28%;
        }
        .node-value {
            &.fieldName {
                flex-basis: 30%;
                max-width: 20em;
            }
            &.fieldRemove {
                flex: 0;
            }
        }
    `,
);

export function FieldsControl(props: PropsWithChildren<FieldsControlProps>): JSX.Element {
    const { readOnly, children } = props;

    return (
        <Styled>
            {children}
            {!readOnly && <AddButton />}
        </Styled>
    );
}
