import React from "react";
import { useTranslation } from "react-i18next";

import { importFiles } from "../../../../actions/nk";
import Icon from "../../../../assets/img/toolbarButtons/import.svg";
import { getProcessName } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/configureStore";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import type { ToolbarButtonProps } from "../../types";

type Props = ToolbarButtonProps;

function ImportButton(props: Props) {
    const { disabled, type } = props;
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const processName = useAppSelector(getProcessName);

    return (
        <CapabilitiesToolbarButton
            write
            name={t("panels.actions.process-import.button", "import")}
            icon={<Icon />}
            disabled={disabled}
            onDrop={(files) => dispatch(importFiles(processName, files))}
            type={type}
        />
    );
}

export default ImportButton;
