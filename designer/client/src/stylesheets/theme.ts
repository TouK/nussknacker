import { PaletteColorOptions, createTheme } from "@mui/material/styles";

declare module "@mui/material/styles" {
    interface Palette {
        ok: PaletteColorOptions;
        inputBorderFocus: string;
        button: {
            background: string;
            hover: string;
            text: string;
            backgroundBlue: string;
            blueTextColor: string;
            border: string;
        };
        panel: {
            panelTitleTextColor: string;
            panelHeaderBackground: string;
            panelBackground: string;
            panelSecondBackground: string;
            panelText: string;
            panelHeaderText: string;
            panelBorder: string;
        };
        tool: {
            toolHoverBkg: string;
            toolHover: string;
        };
        process: {
            processesTableHeaderColor: string;
            processesTableEvenColor: string;
            processesTableOddColor: string;
            processesSearchInput: string;
            processesTopBar: string;
            processesHoverColor: string;
        };
        comment: {
            commentBkgColor: string;
            commentBorderColor: string;
            commentHeaderColor: string;
        };
        modal: {
            modalBkgColor: string;
            modalInputBkgColor: string;
            modalInputTextColor: string;
            modalLabelTextColor: string;
            modalFooterBkgColor: string;
            modalConfirmButtonColor: string;
            modalBorder: string;
        };
        menu: {
            menuButtonBk: string;
            menuBkgColor: string;
            menuTxtColor: string;
            menuButtonBorderColor: string;
            menuButtonActiveBKColor: string;
        };
        scrollbar: string;
        labelBkg: string;
        graphBkg: string;
        defaultText: string;
        docsIconColor: string;
        docsIconColorHover: string;
        testResultsColor: string;
        mark: string;
        listBorder: string;
        focus: string;
        hr: string;
    }
    interface PaletteOptions {
        ok: PaletteColorOptions;
        inputBorderFocus: string;
        button: {
            background: string;
            hover: string;
            text: string;
            backgroundBlue: string;
            blueTextColor: string;
            border: string;
        };
        panel: {
            panelTitleTextColor: string;
            panelHeaderBackground: string;
            panelBackground: string;
            panelSecondBackground: string;
            panelText: string;
            panelHeaderText: string;
            panelBorder: string;
        };
        tool: {
            toolHoverBkg: string;
            toolHover: string;
        };
        process: {
            processesTableHeaderColor: string;
            processesTableEvenColor: string;
            processesTableOddColor: string;
            processesSearchInput: string;
            processesTopBar: string;
            processesHoverColor: string;
        };
        comment: {
            commentBkgColor: string;
            commentBorderColor: string;
            commentHeaderColor: string;
        };
        modal: {
            modalBkgColor: string;
            modalInputBkgColor: string;
            modalInputTextColor: string;
            modalLabelTextColor: string;
            modalFooterBkgColor: string;
            modalConfirmButtonColor: string;
            modalBorder: string;
        };
        menu: {
            menuButtonBk: string;
            menuBkgColor: string;
            menuTxtColor: string;
            menuButtonBorderColor: string;
            menuButtonActiveBKColor: string;
        };
        scrollbar: string;
        labelBkg: string;
        graphBkg: string;
        defaultText: string;
        docsIconColor: string;
        docsIconColorHover: string;
        testResultsColor: string;
        mark: string;
        listBorder: string;
        focus: string;
        hr: string;
    }
}

const commonColors = {
    gray: "#ccc",
    darkGray: "#333",
    deepGray: "#222",
    charcoalGray: "#666",
    mediumDarkGray: "#444",
    slateGray: "#4d4d4d",
    dimGray: "#3e3e3e",
    lightGray: "#b3b3b3",
    lightestGray: "#f6f6f6",
    neutralGray: "#808080",
    silverGray: "#999999",
    white: "#FFF",
    darkBlue: "#0058a9",
    lightGreen: "#8fad60",
};

const theme = createTheme({
    palette: {
        mode: "dark",
        primary: {
            main: commonColors.darkBlue, // Primary color
        },
        info: {
            main: commonColors.gray, // Info color
        },
        warning: {
            main: "#FF9A4D", // Warning color
        },
        error: {
            main: "#f25c6e", // Error color
        },
        success: {
            main: "#64d864", // Success color
        },
        inputBorderFocus: commonColors.darkBlue,
        scrollbar: commonColors.charcoalGray,
        labelBkg: commonColors.darkGray,
        graphBkg: commonColors.lightGray,
        defaultText: commonColors.gray,
        docsIconColor: commonColors.deepGray,
        docsIconColorHover: commonColors.silverGray,
        testResultsColor: commonColors.lightGray,
        mark: commonColors.lightGray,
        listBorder: "#434343",
        focus: commonColors.darkBlue,
        hr: "#777",
        ok: {
            main: commonColors.lightGreen, // Ok color
        },
        background: {
            default: commonColors.slateGray, // Default background color
            paper: commonColors.mediumDarkGray, // Paper background color
        },
        button: {
            background: commonColors.slateGray, // bkgColor
            hover: commonColors.mediumDarkGray, // bkgHover
            text: commonColors.lightestGray,
            backgroundBlue: "#0E9AE0", // blueBkgColor
            blueTextColor: commonColors.white, // blueColor? text
            border: commonColors.charcoalGray,
        },
        panel: {
            panelTitleTextColor: commonColors.neutralGray,
            panelHeaderBackground: commonColors.darkGray,
            panelBackground: commonColors.slateGray,
            panelSecondBackground: commonColors.mediumDarkGray, //panelBkg
            panelText: commonColors.gray,
            panelHeaderText: commonColors.lightGray,
            panelBorder: commonColors.dimGray,
        },
        tool: {
            toolHoverBkg: commonColors.mediumDarkGray,
            toolHover: commonColors.gray,
        },
        process: {
            processesTableHeaderColor: "#E0E0E0",
            processesTableEvenColor: "#F5F5F5",
            processesTableOddColor: commonColors.white,
            processesSearchInput: commonColors.white,
            processesTopBar: commonColors.white,
            processesHoverColor: commonColors.lightGray,
        },
        comment: {
            commentBkgColor: commonColors.darkGray,
            commentBorderColor: commonColors.neutralGray,
            commentHeaderColor: "#afafaf",
        },
        modal: {
            modalBkgColor: commonColors.mediumDarkGray,
            modalInputBkgColor: commonColors.darkGray,
            modalInputTextColor: commonColors.gray,
            modalLabelTextColor: commonColors.silverGray,
            modalFooterBkgColor: "#3a3a3a",
            modalConfirmButtonColor: commonColors.lightGreen,
            modalBorder: commonColors.deepGray,
        },
        menu: {
            menuButtonBk: commonColors.neutralGray,
            menuBkgColor: commonColors.darkGray,
            menuTxtColor: commonColors.white,
            menuButtonBorderColor: commonColors.charcoalGray,
            menuButtonActiveBKColor: "#5d5d5d",
        },
        text: {
            primary: commonColors.lightestGray, // Primary text color
        },
        action: {
            hover: commonColors.mediumDarkGray,
        },
    },
});
export default theme;
