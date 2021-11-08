import * as React from "react";
import { render } from "react-dom";
import "./settings/i18n";
import { Root } from "./root";

function init() {
    let appRoot = document.getElementById("appRoot");
    if (!appRoot) {
        appRoot = document.createElement("div");
        document.body.appendChild(appRoot);
    }
    render(<Root />, appRoot);
}

init();
