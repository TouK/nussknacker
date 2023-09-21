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
`;

export const PredefinedDropdownButton = styled(DropdownButton)`
    ${espButtonBaseStyle};
    min-width: 80px;
    width: 100%;
    font-size: 12px;
    padding: 5px;
    font-weight: 400;
`;

export const StyledRangesWrapper = styled("div")`
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    max-width: 600;
    margin: 0 -10px;
    button: {
        margin: 10px;
    }
`;
