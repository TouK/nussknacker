import { DropdownButton } from "../../common/DropdownButton";
import { styled } from "@mui/material";
import { espButtonBaseStyle } from "../../button/EspStyle";
import { ButtonWithFocus } from "../../../components/withFocus";

export const PredefinedRangeButton = styled(ButtonWithFocus)`
    ${espButtonBaseStyle};
    min-width: 80px;
    font-size: 12px;
    padding: 5px;
    font-weight: 400;
    margin: 10px;
`;

export const PredefinedDropdownButton = styled(DropdownButton)`
    ${espButtonBaseStyle};
    width: 100%;
    font-size: 12px !important;
    padding: 5px !important;
    font-weight: 400;
    margin: 10px;
`;

export const StyledRangesWrapper = styled("div")`
    flex-wrap: wrap;
    display: flex;
    justify-content: center;
    max-width: 600px;
    margin: 0 -10px;
    button: {
        margin: 10px;
    }
`;
