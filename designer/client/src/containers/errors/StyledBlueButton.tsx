import { lighten, styled } from "@mui/material";
import { variables } from "../../stylesheets/variables";

export const SyledBlueButton = styled("button")`
    margin: 45px 50px;
    border: none;
    width: 360px;
    min-width: 300px;
    height: ${variables.formControlHeight};
    background-color: ${variables.buttonBlueBkgColor};
    color: ${variables.buttonBlueColor};
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: "Open Sans";
    font-size: 21px;
    font-weight: 600;
    border-radius: 0;
    &:hover {
        color: ${variables.buttonBlueColor};
        background: ${lighten(variables.buttonBlueBkgColor, 0.25)};
    }
    &:focus {
        color: ${variables.buttonBlueColor};
        #add-icon {
            width: 20px;
            margin-left: 20px;
            &:hover {
                background: ${variables.processesHoverColor};
            }
        }
    }
`;
