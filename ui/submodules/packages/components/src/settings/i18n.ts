/* eslint-disable i18next/no-literal-string */
import i18next, { Module } from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import intervalPlural from "i18next-intervalplural-postprocessor";
import { DateTime } from "luxon";
import { initReactI18next } from "react-i18next";

interface ImportBackend extends Module {
    type: "backend";

    read(language: string, namespace: string, callback: (errorValue: unknown, translations: unknown) => void): void;
}

const importBackend: ImportBackend = {
    type: "backend",
    read: async (language, namespace, callback) => {
        try {
            const url = await import(`../../translations/${language}/${namespace}.json`);
            const response = await fetch(url.default);
            const json = await response.json();
            callback(null, json);
        } catch (error) {
            callback(error, null);
        }
    },
};

const i18n = i18next.use(intervalPlural).use(importBackend).use(LanguageDetector).use(initReactI18next);

i18n.init({
    ns: "main",
    defaultNS: "main",
    fallbackLng: "en",
    supportedLngs: ["en"],
});

i18n.services.formatter.add("relativeDate", (value, lng, options) => {
    if (value instanceof Date) {
        return DateTime.fromJSDate(value).toRelative({ locale: lng });
    }
    if (value instanceof DateTime) {
        return value.toRelative({ locale: lng });
    }
    return value;
})

export default i18n;
