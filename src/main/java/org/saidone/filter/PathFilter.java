/*
 *  Alfresco Node Vault - archive today, accelerate tomorrow
 *  Copyright (C) 2025 Saidone
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.filter;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.event.sdk.handling.filter.AbstractEventFilter;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Event filter for Alfresco repository events that determines if a node is located at
 * a specific path or matches a given path pattern.
 * <p>
 * This filter provides two configurable modes:
 * - Testing if a node's path exactly matches a provided string.
 * - Testing if a node's path matches a regular expression pattern.
 * <p>
 * The filter relies on the {@link NodesApi} to fetch node details, specifically to
 * retrieve the node's path attribute at event evaluation time. Log messages provide
 * traceability of the filter's decisions for debugging purposes.
 * <p>
 * Typical usage involves obtaining a configured instance via the factory
 * methods exposed by a Spring-managed {@link PathFilter} bean:
 * - {@code pathFilter.of(String path)} to filter by exact path name
 * - {@code pathFilter.of(Pattern pathPattern)} to filter by regular expression
 * <p>
 * Integration with Spring ensures that the required {@link NodesApi} dependency
 * is supplied at runtime. This filter is intended to be used as part of event processing
 * chains, allowing only events whose nodes match the specified path criteria to pass through.
 *
 * @see org.alfresco.event.sdk.handling.filter.AbstractEventFilter
 */
@Component
@Slf4j
public class PathFilter extends AbstractEventFilter {

    private NodesApi nodesApi;
    private String path;
    private Pattern pathPattern;

    @Autowired
    public PathFilter(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    private PathFilter(NodesApi nodesApi, String path) {
        this.nodesApi = nodesApi;
        this.path = path;
    }

    private PathFilter(NodesApi nodesApi, Pattern pathPattern) {
        this.nodesApi = nodesApi;
        this.pathPattern = pathPattern;
    }

    public PathFilter of(final String path) {
        return new PathFilter(nodesApi, path);
    }

    public PathFilter of(final Pattern pathPattern) {
        return new PathFilter(nodesApi, pathPattern);
    }

    @Override
    public boolean test(RepoEvent<DataAttributes<Resource>> repoEvent) {
        val nodeId = ((NodeResource) repoEvent.getData().getResource()).getId();
        val node = Objects.requireNonNull(nodesApi.getNode(nodeId, List.of("path"), null, List.of("path")).getBody()).getEntry();
        val isInPath = Strings.isNotBlank(path) && node.getPath().getName().equals(path) ||
                pathPattern != null && pathPattern.matcher(node.getPath().getName()).matches();
        log.debug("Node {} is in path {} => {}", nodeId, path, isInPath);
        return isInPath;
    }

}