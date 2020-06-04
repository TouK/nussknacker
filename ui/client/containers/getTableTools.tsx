import SearchFilter from "../components/table/SearchFilter"
import TableSelect from "../components/table/TableSelect"
import AddProcessButton from "../components/table/AddProcessButton"
import AddProcessDialog from "../components/AddProcessDialog"
import React from "react"
import {Processes} from "./Processes"

export function getTableTools() {
  return (
    <>
      <div id="process-top-bar">
        <SearchFilter
          onChange={this.onSearchChange}
          value={this.state.search}
        />

        <TableSelect
          defaultValue={this.state.selectedCategories}
          options={this.props.filterCategories}
          placeholder={"Select categories.."}
          onChange={this.onCategoryChange}
          isMulti={true}
          isSearchable={true}
        />

        <TableSelect
          defaultValue={this.state.selectedDeployedOption}
          options={this.deployedOptions}
          placeholder="Select deployed info.."
          onChange={this.onDeployedChange}
          isMulti={false}
          isSearchable={false}
        />

        <AddProcessButton
          loggedUser={this.props.loggedUser}
          onClick={() => this.setState({showAddProcess: true})}
        />
      </div>

      <AddProcessDialog
        onClose={() => this.setState({showAddProcess: false})}
        isOpen={this.state.showAddProcess}
        isSubprocess={false}
        visualizationPath={Processes.path}
        message="Create new process"
        clashedNames={this.state.clashedNames}
      />
    </>
  )
}
