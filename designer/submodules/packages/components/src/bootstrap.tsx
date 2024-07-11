import * as React from "react";
import "./settings/i18n";
import { Root } from "./root";
import { createRoot } from "react-dom/client";

function init() {
    let appRoot = document.getElementById("appRoot");
    if (!appRoot) {
        appRoot = document.createElement("div");
        document.body.appendChild(appRoot);
    }

    const root = createRoot(appRoot);
    root.render(<Root />);
}

init();
