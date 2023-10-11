import React from "react";
import { ButtonWithFocus } from "../../../withFocus";
import TuneIcon from "@mui/icons-material/Tune";

interface SettingsButton {
    isOpen: boolean;
    openSettingMenu: () => void;
}

export default function SettingsButton({ isOpen, openSettingMenu }) {
    return (
        <ButtonWithFocus
            style={{
                justifyContent: "center",
                alignItems: "center",
                display: "flex",
                marginRight: 5,
                backgroundColor: isOpen && "#444444",
            }}
            className="addRemoveButton"
            title={""}
            onClick={openSettingMenu}
        >
            <TuneIcon />
        </ButtonWithFocus>
    );
}
