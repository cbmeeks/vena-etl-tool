package org.vena.etltool.entities;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName(ETLStreamToStagingTableStepDTO.stepType)
public class ETLStreamToStagingTableStepDTO extends ETLTypedStreamStepDTO{
	protected final static String stepType = "ETLStreamToMSSQLServerStep";

	String tableName;
	List<String> columnNames;

	public String getTableName() {
		return tableName;
	}

	public List<String> getColumnNames() {
		return columnNames;
	}

	void setColumnNames(List<String> columnNames) {
		this.columnNames = columnNames;
	}

	void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public ETLStreamToStagingTableStepDTO(){} // For Morphia
	
	@Override
	public String getName() {
		return "Streaming to staging table";
	}

}