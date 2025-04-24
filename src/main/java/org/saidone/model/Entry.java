package org.saidone.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.alfresco.core.model.Node;

@AllArgsConstructor
@Data
public class Entry {

    @JsonProperty("entry")
    private Node node;

}
