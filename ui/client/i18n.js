/* eslint-disable i18next/no-literal-string */
import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import Backend from "i18next-xhr-backend";
import {initReactI18next} from "react-i18next";

i18n
    .use(Backend)
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      fallbackLng: "en",
      keySeparator: false,
      interpolation: {
        escapeValue: false,
      },
      backend: {
          loadPath: "static/assets/locales/{{lng}}/{{ns}}.json",
      }
    });
