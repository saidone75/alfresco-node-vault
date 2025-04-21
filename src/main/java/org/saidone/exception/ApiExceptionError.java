package org.saidone.exception;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Data;

@Data
@JsonRootName(value = "error")
public class ApiExceptionError {

    private String errorKey;
    private String statusCode;
    private String briefSummary;
    private String stackTrace;
    private String descriptionURL;
    private String logId;

}
