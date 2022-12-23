import React from "react"
import loadable from "@loadable/component"
import LoaderSpinner from "../Spinner"

const Survey = loadable(() => import("./Survey"), {fallback: <LoaderSpinner show={true}/>})

export const SURVEY_CLOSED_SETTINGS_KEY = "survey-panel.closed"

export function SurveyPanel(): JSX.Element {
  return (
    <Survey
      text="Hey, as announced on August 24, we would like to ask you to fill out the survey again. The results are to help us determine what needs to be done in TouKu. We are also curious how TouK has changed."
      // link="https://forms.gle/QrnTGNpanN2yCGyQA"
      link="https://docs.google.com/forms/d/e/1FAIpQLSfLfihcNrOo57clNg-Zv7FFnHwm_ii4Ww7cCltBFGyHCBaGjQ/viewform?embedded"
    />
  )
}

