package org.saidone.filter;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.event.sdk.handling.filter.AbstractEventFilter;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PathFilter extends AbstractEventFilter {

    private static NodesApi nodesApi;
    private String path;
    private Pattern pathPattern;

    @Autowired
    public PathFilter(NodesApi nodesApi) {
        PathFilter.nodesApi = nodesApi;
    }

    private PathFilter(String path) {
        this.path = path;
    }

    private PathFilter(Pattern pathPattern) {
        this.pathPattern = pathPattern;
    }

    public static PathFilter of(final String path) {
        return new PathFilter(path);
    }

    public static PathFilter of(final Pattern pathPattern) {
        return new PathFilter(pathPattern);
    }

    @Override
    public boolean test(RepoEvent<DataAttributes<Resource>> repoEvent) {
        var node = Objects.requireNonNull(nodesApi.getNode(((NodeResource) repoEvent.getData().getResource()).getId(), List.of("path"), null, List.of("path")).getBody()).getEntry();
        var isInPath = StringUtils.isNotBlank(path) && node.getPath().getName().equals(path) ||
                pathPattern != null && pathPattern.matcher(node.getPath().getName()).matches();
        log.debug("Node {} is in path {} => {}", node.getId(), path, isInPath);
        return isInPath;
    }

}