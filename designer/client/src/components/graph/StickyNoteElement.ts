import { dia } from "jointjs";
import { Events } from "./types";

export const StickyNoteElement = (defaults?: any, protoProps?: any) =>
    dia.Element.define("stickyNote.StickyNoteElement", defaults, protoProps);

export const StickyNoteElementView = dia.ElementView.extend({
    events: {
        "change textarea": "onChange",
    },

    pointerdblclick: function (evt, x, y) {
        this.model.attr("name/props/disabled", false);
    },
    onChange: function (evt) {
        this.model.trigger(Events.CELL_CONTENT_UPDATED, this.model, evt.target.value);
        this.model.attr("name/props/value", evt.target.value);
    },
});
