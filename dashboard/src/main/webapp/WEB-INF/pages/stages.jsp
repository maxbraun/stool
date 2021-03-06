<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<jsp:useBean id="stages" scope="request" type="java.util.List<net.oneandone.stool.dashboard.StageInfo>"/>
<c:forEach var="stage" items="${stages}">
    <tr id="${stage.name}" data-hash="${stage.hash}" data-extractionurl="${stage.extractionUrl}" data-user="${stage.lastModifiedBy}"
        data-status="${stage.running}" data-updateAvailable="${stage.updateAvailable}" class="stage ${stage.category}">

        <td class="status">
            <div class="status label label-${stage.state}">${stage.running}</div>
        </td>
        <td class="name"><span data-container="body" data-toggle="popover" data-placement="bottom" data-content="${stage.extractionUrl}"
                               data-trigger="hover">
                ${stage.name}
        </span></td>
        <td class="links">
            <c:choose>
                <c:when test="${stage.urls != null}">
                    <c:forEach var="url" items="${stage.urls}">
                        <a href="${url.value}" target="_blank">${url.key}</a><br/>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    -
                </c:otherwise>
            </c:choose>

        </td>
        <td class="expire ${stage.expire.isExpired() ? "expired" : ""}">${stage.expire}</td>
        <td class="user">${stage.lastModifiedBy}</td>
        <td class="option refresh">
            <button type="button" data-action="refresh" data-estimate="${stage.stats.avgRefresh}" data-stage="${stage.name}"
                    data-options="build,autorestart" class="btn ${stage.updateAvailable ? "btn-primary" : "btn-default"} btn-xs"
                    <c:if test="${stage.updateAvailable}">data-container="body" data-toggle="popover" data-placement="right"
                    data-title="${stage.changes.exception ? "Warning" : "Update Available"}" data-trigger="hover" data-html="true"
                    data-content="${stage.changes}"</c:if>><span
                    class="glyphicon glyphicon-refresh"></span> Refresh
            </button>
        <td class="option share">
            <a role="button" href="mailto:?subject=Stage&body=${stage.shareText}" ${stage.urls == null ? "disabled=\"disabled\"" :""}
               class="btn btn-default btn-xs share"><span
                    class="glyphicon glyphicon-bullhorn"></span> Share</a>
        </td>
        <td class="option">
            <div class="btn-group">
                <button type="button" class="btn btn-default btn-xs dropdown-toggle" data-toggle="dropdown">Actions <span
                        class="caret"></span></button>
                <ul class="dropdown-menu">
                    <li><a href="#dashboard" data-estimate="${stage.stats.avgStart}" data-action="start"
                           data-stage="${stage.name}">Start</a>
                    </li>
                    <li><a href="#dashboard" data-estimate="${stage.stats.avgStart}" data-action="start" data-options="fitnesse"
                           data-stage="${stage.name}">Start Fitnesse</a>
                    </li>
                    <li><a href="#dashboard" data-estimate="${stage.stats.avgStop}" data-action="stop"
                           data-stage="${stage.name}">Stop</a></li>
                    <li><a href="#dashboard" data-estimate="${stage.stats.avgRestart}" data-action="restart" data-stage="${stage.name}">Restart</a>
                    </li>
                    <li><a href="#dashboard" data-estimate="${stage.stats.avgBuild}" data-action="build" data-stage="${stage.name}">Build</a></li>
                    <li><a href="#dashboard" data-action="refresh" data-options="restore" data-stage="${stage.name}">Rollback</a></li>
                </ul>
            </div>
        </td>
    </tr>
</c:forEach>