/* eslint-disable i18next/no-literal-string */
import { css } from "@emotion/css";

export const isClipboardEvent = (event: Event): event is ClipboardEvent => {
    return !!event["clipboardData"];
};

export async function readText(event?: Event): Promise<string> {
    if (event && isClipboardEvent(event)) {
        return event.clipboardData.getData("text");
    } else {
        try {
            return await navigator.clipboard.readText();
        } catch (error) {
            return Promise.reject(error.message);
        }
    }
}

interface WriteText {
    (text: string): Promise<string>;
}

// We could have used navigator.clipboard.writeText but it is not defined for
// content delivered via HTTP. The workaround is to create a hidden element
// and then write text into it. After that copy command is used to replace
// clipboard's contents with given text. What is more the hidden element is
// assigned with given id to be able to differentiate between artificial
// copy event and the real one triggered by user.
// Based on https://techoverflow.net/2018/03/30/copying-strings-to-the-clipboard-using-pure-javascript/
const fallbackWriteText: WriteText = (text) => {
    return new Promise((resolve) => {
        const el = document.createElement("textarea");
        el.value = text;
        el.setAttribute("readonly", "");
        el.className = css({
            position: "absolute",
            left: "-9999px",
        });
        el.oncopy = (e) => {
            // Skip event triggered by writing selection to the clipboard.
            e.stopPropagation();
            resolve(text);
        };
        document.body.appendChild(el);
        el.select();
        document.execCommand("copy");
        document.body.removeChild(el);
    });
};

export const writeText: WriteText = (text) => {
    if (navigator.clipboard?.writeText) {
        return navigator.clipboard.writeText(text).then(() => text);
    }
    return fallbackWriteText(text);
};
