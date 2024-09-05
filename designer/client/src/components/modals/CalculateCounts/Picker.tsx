import { Moment } from "moment";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { DTPicker } from "../../common/DTPicker";
import moment from "moment/moment";
import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import { nodeInput, nodeInputWithError } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";

const dateFormat = "YYYY-MM-DD";
const timeFormat = "HH:mm:ss";

export type PickerInput = Moment | string;

type PickerProps = { label: string; onChange: (date: PickerInput) => void; value: PickerInput };

const DTPickerWrapper = styled("div")`
    margin: 0;
`;

export function Picker({ label, onChange, value }: PickerProps): JSX.Element {
    const { i18n } = useTranslation();
    const isValid = useMemo(() => moment(value).isValid(), [value]);
    const datePickerStyle = useMemo(
        () => ({
            // TODO: styled
            className: cx({ [nodeInputWithError]: !isValid, [nodeInput]: true }),
        }),
        [isValid],
    );

    return (
        <>
            <p>{label}</p>
            <DTPickerWrapper>
                <DTPicker
                    dateFormat={dateFormat}
                    timeFormat={timeFormat}
                    inputProps={datePickerStyle}
                    onChange={onChange}
                    value={value}
                    locale={i18n.language}
                />
            </DTPickerWrapper>
        </>
    );
}
