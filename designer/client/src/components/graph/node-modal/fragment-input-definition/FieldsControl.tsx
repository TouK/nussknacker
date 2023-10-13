import React, { PropsWithChildren } from "react";
import { AddButton } from "./AddButton";
import { styled } from "@mui/material";
import { buttonBase } from "../../../../stylesheets/styledGraphWrapper";

interface FieldsControlProps {
    readOnly?: boolean;
}

const Styled = styled('div')`
  .addRemoveButton {
    ${buttonBase};
    width: 35px;
    height: 35px;
    font-weight: bold;
    font-size: 20px;
  }

  .fieldName {
    width: 28%;
  }
  .handle-bars {
    height: 35px;
    width: 12px;
    margin-left: 6px;
    cursor: grab;
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
`

export function FieldsControl(props: PropsWithChildren<FieldsControlProps>): JSX.Element {
    const { readOnly, children } = props;
    console.log('works')
    return (
        <Styled>
            {children}
            {!readOnly && <AddButton />}
        </Styled>
    );
}
