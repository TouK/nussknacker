import '@testing-library/jest-dom/extend-expect'
import React from 'react'
import {render, screen} from '@testing-library/react'
import {errorValidator, mandatoryValueValidator} from "../components/graph/node-modal/editors/Validators"
import ValidationLabels from "../components/modals/ValidationLabels"


describe("displaying validation labels", () => {

  test('display only fe validation when both be and fe validations available for the same error type', () => {
    //given
    const fieldName = "fieldName"
    const emptyValue = ""
    const backendError = {message: "Test", description: "test", typ: "EmptyMandatoryParameter", fieldName: "fieldName"}
    const validators = [
      mandatoryValueValidator,
      errorValidator([backendError], "fieldName")
    ]

    render(<ValidationLabels validators={validators} values={[emptyValue]}/>)

    expect(screen.findAllByRole("span").length).toBe(1)
  })

  it("display validations for different error type", () => {
    //given
    const fieldName = "fieldName"
    const emptyValue = ""
    const backendError = {message: "Test", description: "test", typ: "AnotherErrorType", fieldName: "fieldName"}
    const validators = [
      mandatoryValueValidator,
      errorValidator([backendError], "fieldName")
    ]

    //when
    render(<ValidationLabels validators={validators} values={[emptyValue]}/>)

    //then
    expect(screen.findAllByRole("span").length).toBe(2)
  })
})
