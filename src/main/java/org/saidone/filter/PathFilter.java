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
 * Event filter that matches repository nodes based on their path.
 * <p>
 * The filter allows to specify either an exact string path or a regular expression pattern.
 * When applied, it checks if the node involved in the event is located in the configured path,
 * or if its path matches the provided pattern.
 * </p>
 *
 * <p>
 * This filter uses the {@link NodesApi} to retrieve node details, including its path, from the repository.
 * It can be instantiated using either a string path or a regular expression pattern via the static factory methods.
 * </p>
 *
 * <p>
 * The {@code test} method logs the result of the path check for each event.
 * </p>
 *
 * @see org.alfresco.event.sdk.handling.filter.AbstractEventFilter
 * @see org.alfresco.core.handler.NodesApi
 */
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
        val nodeId = ((NodeResource) repoEvent.getData().getResource()).getId();
        val node = Objects.requireNonNull(nodesApi.getNode(nodeId, List.of("path"), null, List.of("path")).getBody()).getEntry();
        val isInPath = Strings.isNotBlank(path) && node.getPath().getName().equals(path) ||
                pathPattern != null && pathPattern.matcher(node.getPath().getName()).matches();
        log.debug("Node {} is in path {} => {}", nodeId, path, isInPath);
        return isInPath;
    }

}