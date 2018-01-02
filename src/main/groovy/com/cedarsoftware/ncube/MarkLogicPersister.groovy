package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.formatters.NCubeTestReader
import com.cedarsoftware.util.ArrayUtilities
import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.IOUtilities
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.UniqueIdGenerator
import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.JsonWriter
import com.fasterxml.jackson.databind.JsonNode
import com.google.common.collect.Sets
import com.marklogic.client.DatabaseClient
import com.marklogic.client.Transaction
import com.marklogic.client.document.DocumentPage
import com.marklogic.client.document.DocumentPatchBuilder
import com.marklogic.client.document.DocumentRecord
import com.marklogic.client.document.JSONDocumentManager
import com.marklogic.client.eval.EvalResult
import com.marklogic.client.eval.EvalResultIterator
import com.marklogic.client.eval.ServerEvaluationCall
import com.marklogic.client.io.Format
import com.marklogic.client.io.InputStreamHandle
import com.marklogic.client.io.JacksonHandle
import com.marklogic.client.io.StringHandle
import com.marklogic.client.io.ValuesHandle
import com.marklogic.client.io.marker.DocumentPatchHandle
import com.marklogic.client.query.CountedDistinctValue
import com.marklogic.client.query.QueryManager
import com.marklogic.client.query.RawCombinedQueryDefinition
import com.marklogic.client.query.ValuesDefinition
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Timestamp
import java.util.Map.Entry
import java.util.zip.GZIPInputStream

import static com.cedarsoftware.ncube.NCubeConstants.*

/**
 * MarkLogic Persister for n-cubes.  Manages all reads and writes of n-cubes to a MarkLogic database.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com), Josh Snyder (joshsnyder@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class MarkLogicPersister
{
    private static final Logger LOG = LoggerFactory.getLogger(MarkLogicPersister.class)
    private static DatabaseClient client
    private static QueryManager queryManager
    private static JSONDocumentManager documentManager

    MarkLogicPersister(DatabaseClient client)
    {
        this.client = client
        queryManager = client.newQueryManager()
        documentManager = client.newJSONDocumentManager()
    }

    static List<NCubeInfoDto> search(ApplicationID appId, String cubeNamePattern, String searchContent, Map<String, Object> options)
    {
        List<NCubeInfoDto> list = []
        // TODO keep building this dynamically

        boolean activeRecordsOnly = toBoolean(options[SEARCH_ACTIVE_RECORDS_ONLY])
        boolean deletedRecordsOnly = toBoolean(options[SEARCH_DELETED_RECORDS_ONLY])
        if (activeRecordsOnly && deletedRecordsOnly)
        {
            throw new IllegalArgumentException("activeRecordsOnly and deletedRecordsOnly are mutually exclusive options and cannot both be 'true'.")
        }

        String termQuery = StringUtilities.hasContent(searchContent) ? """{"term-query": {"json-property": "json", "text": "${searchContent}"}},""" : ''
        StringBuilder sb = new StringBuilder(termQuery)

        Map<String, Object> jsonProperties = [tenant: appId.tenant, app: appId.app, version: appId.version, status: appId.status, branch: appId.branch, tip: true] as Map
        if (activeRecordsOnly)
        {
            jsonProperties.active = true
        }
        else if (deletedRecordsOnly)
        {
            jsonProperties.active = false
        }

        if (StringUtilities.hasContent(cubeNamePattern))
        {
            jsonProperties.name = convertPattern(buildName(cubeNamePattern))
        }
        String searchQueries = createAndQueries(jsonProperties)
        sb.append(searchQueries)
        String queries = sb.toString()

        String query = """{
"search": {
    "query": {
        "queries": [${queries}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)

        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())
            NCubeInfoDto dto = new NCubeInfoDto()
            String uri = document.uri
            dto.id = getIdFromUri(uri)
            dto.tenant = jsonObj.tenant
            dto.app = jsonObj.app
            dto.version = jsonObj.version
            dto.status = jsonObj.status
            dto.branch = jsonObj.branch
            dto.name = jsonObj.name
            dto.sha1 = jsonObj.sha1
            dto.headSha1 = jsonObj.headSha1
            dto.revision = convertToDtoRev((boolean)jsonObj.active, (long)jsonObj.revision)
            dto.createDate = (Date)Converter.convert(jsonObj.createDate, Date.class)
            dto.createHid = jsonObj.createHid
            dto.changed = jsonObj.changed
            if (options[SEARCH_INCLUDE_NOTES])
            {
                dto.notes = jsonObj.notes
            }
            if (options[SEARCH_INCLUDE_CUBE_DATA])
            {
                // TODO there might be a better way to do this just using search
                InputStream is = getCubeStreamFromUri(uri)
                dto.bytes = IOUtilities.inputStreamToBytes(is)
            }
            if (options[SEARCH_INCLUDE_TEST_DATA])
            {
                dto.testData = JsonWriter.objectToJson(jsonObj.testData)
            }

            if (SYS_INFO != dto.name || options[SEARCH_ALLOW_SYS_INFO])
            {
                list.add(dto)
            }
        }

        return list
    }

    static void createCube(NCube ncube, String username)
    {
        // TODO research unique constraint on n_cube_nm, tenant_cd, app_cd, version_no_cd, branch_id, revision_number
        ApplicationID appId = ncube.applicationID
        Map<String, Object> options = [(SEARCH_EXACT_MATCH_NAME): true,
                                       (METHOD_NAME) : 'createCube'] as Map

        List<NCubeInfoDto> dtos = search(appId, ncube.name, '', options)
        if (dtos.size() > 0)
        {
            throw new IllegalArgumentException("Unable to create cube: ${ncube.name} in app: ${appId}, cube already exists (it may need to be restored)")
        }

        Timestamp now = nowAsTimestamp()
        String mlFormat = """{
"tenant": "${appId.tenant}",
"app": "${appId.app}",
"version": "${appId.version}",
"status": "${appId.status}",
"branch": "${appId.branch}",
"name": "${ncube.name}",
"sha1": "${ncube.sha1()}",
"headSha1": null,
"revision": 0,
"active": true,
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, 'created')}",
"changed": true,
"json" : "",
"testData": ${ncube.metaProperties[NCube.METAPROPERTY_TEST_DATA] ?: null}
}"""

        writeDocument(mlFormat, ncube)
        createSysInfoCube(appId, username)
    }

    static void updateCube(NCube ncube, String username)
    {
        ApplicationID appId = ncube.applicationID
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true,
                name: ncube.name
        ] as Map

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)

        if (documents.size() < 1)
        {
            throw new IllegalArgumentException("Unable to update cube: ${ncube.name} in app: ${appId}, cube does not exist")
        }
        if (documents.size() > 1)
        {
            throw new IllegalStateException("Whoops, too many 'tip' records.") // TODO remove this or make it proper
        }

        DocumentRecord prevDoc = documents.next()
        StringHandle sHandle = prevDoc.getContent(new StringHandle())
        Map jsonObj = JsonReader.jsonToMaps(sHandle.get())
        boolean cubeActive = (boolean)jsonObj.active
        String headSha1 = (String)jsonObj.headSha1
        String oldSha1 = (String)jsonObj.sha1
        String testData = JsonWriter.objectToJson(jsonObj.testData)

        if (ncube.metaProperties.containsKey(NCube.METAPROPERTY_TEST_DATA))
        {
            String updatedTestData = (String)ncube.metaProperties[NCube.METAPROPERTY_TEST_DATA]
            if ((updatedTestData || testData) && updatedTestData != testData)
            {
                ncube.setMetaProperty(NCube.METAPROPERTY_TEST_UPDATED, UniqueIdGenerator.uniqueId)
                testData = updatedTestData
            }
        }

        if (cubeActive && StringUtilities.equalsIgnoreCase(oldSha1, ncube.sha1()))
        {
            // SHA-1's are equal and both revision values are positive.  No need for new revision of record.
            return
        }

        boolean changed = !StringUtilities.equalsIgnoreCase(ncube.sha1(), headSha1)

        Timestamp now = nowAsTimestamp()
        String mlFormat = """{
"tenant": "${appId.tenant}",
"app": "${appId.app}",
"version": "${appId.version}",
"status": "${appId.status}",
"branch": "${appId.branch}",
"name": "${ncube.name}",
"sha1": "${ncube.sha1()}",
"headSha1": ${headSha1 ? "\"${headSha1}\"" : null},
"revision": ${(long)jsonObj.revision + 1L},
"active": true,
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, 'updated')}",
"changed": ${changed},
"json" : "",
"testData": ${testData}
}"""

        writeDocument(mlFormat, ncube, prevDoc.uri)
    }

    static boolean duplicateCube(ApplicationID oldAppId, ApplicationID newAppId, String oldName, String newName, String username)
    {
        Map<String, Object> oldProperties = [
                tenant: oldAppId.tenant,
                app: oldAppId.app,
                version: oldAppId.version,
                status: oldAppId.status,
                branch: oldAppId.branch,
                tip: true,
                name: oldName
        ] as Map

        String oldQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(oldProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage oldDocs = searchDocuments(oldQuery)
        if (oldDocs.size() == 0)
        {   // not found
            throw new IllegalArgumentException("Could not duplicate cube because cube does not exist, app: ${oldAppId}, name: ${oldName}")
        }
        DocumentRecord oldDoc = oldDocs.next()
        StringHandle oldHandle = oldDoc.getContent(new StringHandle())
        Map oldObj = JsonReader.jsonToMaps(oldHandle.get())
        boolean oldActive = oldObj.active
        String sha1 = oldObj.sha1
        String oldTestData = JsonWriter.objectToJson(oldObj.testData)
        InputStream is = getCubeStreamFromUri(oldDoc.uri)
        NCube ncube = NCube.createCubeFromStream(is)

        if (!oldActive)
        {
            throw new IllegalArgumentException("Unable to duplicate deleted cube, app: ${oldAppId}, name: ${oldName}")
        }

        Map newProperties = [
                tenant: newAppId.tenant,
                app: newAppId.app,
                version: newAppId.version,
                status: newAppId.status,
                branch: newAppId.branch,
                tip: true,
                name: newName
        ] as Map
        String newQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(newProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage newDocs = searchDocuments(newQuery)
        boolean active = false
        Long newRevision = null
        String headSha1 = null
        String newDocUri = null
        while (newDocs.hasNext())
        {
            DocumentRecord newDoc = newDocs.next()
            newDocUri = newDoc.uri
            StringHandle newHandle = newDoc.getContent(new StringHandle())
            Map newObj = JsonReader.jsonToMaps(newHandle.get())
            active = newObj.active
            newRevision = (long)newObj.revision
            headSha1 = newObj.headSha1
        }

        if (newRevision != null && active)
        {
            throw new IllegalArgumentException("Unable to duplicate cube, cube already exists with the new name, app:  ${newAppId}, name: ${newName}")
        }

        boolean nameChanged = !StringUtilities.equalsIgnoreCase(oldName, newName)
        boolean sameExceptBranch = oldAppId.equalsNotIncludingBranch(newAppId)

        // If names are different we need to recalculate the sha-1
        if (nameChanged)
        {

            ncube.name = newName
            ncube.applicationID = newAppId
            sha1 = ncube.sha1()
        }

        String notes = "Cube duplicated from app: ${oldAppId}, name: ${oldName}"
        Long rev = newRevision == null ? 0L : Math.abs(newRevision as long) + 1L

        Timestamp now = nowAsTimestamp()
        String mlFormat = """{
"tenant": "${newAppId.tenant}",
"app": "${newAppId.app}",
"version": "${newAppId.version}",
"status": "${newAppId.status}",
"branch": "${newAppId.branch}",
"name": "${newName}",
"sha1": "${sha1}",
"headSha1": ${sameExceptBranch ? "\"${headSha1}\"" : null},
"revision": ${rev},
"active": true,
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${notes}",
"changed": true,
"json" : "",
"testData": ${oldTestData}
}"""

        writeDocument(mlFormat, ncube, newDocUri)
        createSysInfoCube(newAppId, username)
        return true
    }

    static boolean renameCube(ApplicationID appId, String oldName, String newName, String username)
    {
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true,
                name: oldName
        ] as Map

        String oldQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage oldDocs = searchDocuments(oldQuery)
        if (oldDocs.size() == 0)
        {   // not found
            throw new IllegalArgumentException("Could not rename cube because cube does not exist, app: ${appId}, name: ${oldName}")
        }

        DocumentRecord oldDoc = oldDocs.next()
        StringHandle oldHandle = oldDoc.getContent(new StringHandle())
        Map oldObj = JsonReader.jsonToMaps(oldHandle.get())
        boolean oldActive = oldObj.active
        String testData = JsonWriter.objectToJson(oldObj.testData)
        InputStream is = getCubeStreamFromUri(oldDoc.uri)
        byte[] jsonBytes = IOUtilities.inputStreamToBytes(is)

        if (!oldActive)
        {
            throw new IllegalArgumentException("Unable to duplicate deleted cube, app: ${appId}, name: ${oldName}")
        }

        jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true,
                name: newName
        ] as Map
        String newQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        Long newRevision = null
        String newHeadSha1 = null
        DocumentPage newDocs = searchDocuments(newQuery)
        while (newDocs.hasNext())
        {
            DocumentRecord newDoc = newDocs.next()
            StringHandle newHandle = newDoc.getContent(new StringHandle())
            Map newObj = JsonReader.jsonToMaps(newHandle.get())
            newRevision = (long)newObj.revision
            newHeadSha1 = newObj.headSha1
        }
        NCube ncube = NCube.createCubeFromBytes(jsonBytes)
        ncube.name = newName
        String notes = "renamed: ${oldName} -> ${newName}"
        Long rev = newRevision == null ? 0L : newRevision + 1L

        Timestamp now = nowAsTimestamp()
        String mlFormat = """{
"tenant": "${appId.tenant}",
"app": "${appId.app}",
"version": "${appId.version}",
"status": "${appId.status}",
"branch": "${appId.branch}",
"name": "${newName}",
"sha1": "${ncube.sha1()}",
"headSha1": ${newHeadSha1 ? "\"${newHeadSha1}\"": null},
"revision": ${rev},
"active": true,
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${notes}",
"changed": true,
"json" : "",
"testData": ${testData}
}"""

        writeDocument(mlFormat, ncube)

        if (!oldName.equalsIgnoreCase(newName))
        {
            deleteCubes(appId, [oldName] as Object[], true, username)
        }

        return true
    }

    static boolean deleteCubes(ApplicationID appId, Object[] cubeNames, boolean allowDelete, String username)
    {
        long txId = UniqueIdGenerator.uniqueId
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true
        ] as Map
        Set names = Sets.newHashSet(cubeNames)
        names.remove(SYS_INFO)
        String query = """{
"search": {
    "query": {
        "queries": [
            ${createAndQueries(jsonProperties)},
            {"or-query": {
                "queries": [${createOrQueries('string', 'name', names)}]
            }}
        ]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)

        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())
            String headSha1 = (String)jsonObj.headSha1

            Timestamp now = nowAsTimestamp() // TODO move Timestamp out of while loop?
            String mlFormat = """{
"tenant": "${jsonObj.tenant}",
"app": "${jsonObj.app}",
"version": "${jsonObj.version}",
"status": "${jsonObj.status}",
"branch": "${jsonObj.branch}",
"name": "${jsonObj.name}",
"sha1": "${jsonObj.sha1}",
"headSha1": ${headSha1 ? "\"${headSha1}\"": null},
"revision": ${(long)jsonObj.revision + 1L},
"active": false,
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, "deleted, txId: [${txId}]")}",
"changed": true,
"json" : "",
"testData": ${JsonWriter.objectToJson(jsonObj.testData)}
}"""

            writeDocument(mlFormat, document.uri, true)
        }

        return documents.size() > 0
    }

    static List<NCubeInfoDto> commitCubes(ApplicationID appId, Object[] cubeIds, String username, String requestUser, String txId, String notes)
    {
        List<NCubeInfoDto> infoRecs = []
        if (ArrayUtilities.isEmpty(cubeIds))
        {
            return infoRecs
        }

        ApplicationID headAppId = appId.asHead()
        String noteText = "merged pull request from [${requestUser}], txId: [${txId}], ${PR_NOTES_PREFIX}${notes}"

        for (String cubeId : cubeIds)
        {
            String uri = "/${cubeId}.json"

            String query = """{
"search": {
    "query": {
        "queries": [{"document-query": {"uri": ["${uri}"]}}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}} 
}
}"""

            DocumentPage documents = searchDocuments(query)
            if (documents.size() == 0)
            {
                throw new IllegalArgumentException("Unable to find cube with id: " + cubeId)
            }
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())

            Map<String, Object> jsonProperties = [
                    tenant: headAppId.tenant,
                    app: headAppId.app,
                    version: headAppId.version,
                    status: headAppId.status,
                    branch: headAppId.branch,
                    tip: true
            ] as Map

            String headQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
            DocumentPage headDocs = searchDocuments(headQuery)
            Map headObj = null
            if (headDocs.size() > 0)
            {
                DocumentRecord headDoc = headDocs.next()
                StringHandle headHandle = headDoc.getContent(new StringHandle())
                headObj = JsonReader.jsonToMaps(headHandle.get())
            }

            Boolean headActive = true
            Long headRevision = null
            String changeType = null
            if (!headObj)
            {
                if (!jsonObj.active)
                {   // User created then deleted cube, but it has no HEAD corresponding cube, don't promote it
                }
                else
                {
                    changeType = ChangeType.CREATED.code
                    headRevision = 0L
                }
            }
            else if (!jsonObj.active)
            {
                if (!headObj.active)
                {   // Deleted in both, don't promote it
                }
                else
                {
                    changeType = ChangeType.DELETED.code
                    headRevision = (long)headObj.revision + 1
                    headActive = false
                }
            }
            else
            {
                if (!headObj.active)
                {
                    changeType = ChangeType.RESTORED.code
                }
                else
                {
                    changeType = ChangeType.UPDATED.code
                }
                headRevision = (long)headObj.revision + 1
            }

            if (changeType)
            {
                Timestamp now = nowAsTimestamp()

                // update branch ncube
                DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
                patchBuilder.replaceValue('/headSha1', jsonObj.sha1)
                patchBuilder.replaceValue('/createDate', DATE_TIME_FORMAT.format(now)) // TODO should we move the createDate up like this?
                patchBuilder.replaceValue('/changed', false)
                documentManager.patch(uri, patchBuilder.build(), transaction)

                // insert new head ncube
                String mlFormat = """{
"tenant": "${headAppId.tenant}",
"app": "${headAppId.app}",
"version": "${headAppId.version}",
"status": "${headAppId.status}",
"branch": "${headAppId.branch}",
"name": "${jsonObj.name}",
"sha1": "${jsonObj.sha1}",
"headSha1": null,
"revision": ${headRevision},
"active": ${headActive},
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${noteText}",
"changed": false,
"json" : "",
"testData": ${JsonWriter.objectToJson(jsonObj.testData)}
}"""

                writeDocument(mlFormat, uri)
                NCubeInfoDto dto = new NCubeInfoDto()
                dto.id = cubeId
                dto.name = jsonObj.name
                dto.sha1 = jsonObj.sha1
                dto.headSha1 = jsonObj.sha1
                dto.changed = false
                dto.tenant = headAppId.tenant
                dto.app = headAppId.app
                dto.version = headAppId.version
                dto.status = headAppId.status
                dto.branch = headAppId.branch
                dto.createDate = now
                dto.createHid = username
                dto.notes = noteText
                dto.revision = headRevision
                dto.changeType = changeType
                infoRecs.add(dto)
            }
        }

        createSysInfoCube(headAppId, username)
        return infoRecs
    }

    static NCubeInfoDto loadCubeRecordById(long cubeId, Map options)
    {
        if (!options)
        {
            options = [:]
        }
        if (!options.containsKey(SEARCH_INCLUDE_CUBE_DATA))
        {
            options[SEARCH_INCLUDE_CUBE_DATA] = true
        }

        String uri = "/${cubeId}.json"
        String query = """{
"search": {
    "query": {
        "queries": [{"document-query": {"uri": ["${uri}"]}}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}} 
}
}"""
        DocumentPage documents = searchDocuments(query)
        if (documents.size() == 0)
        {
            throw new IllegalArgumentException("Unable to find cube with id: " + cubeId)
        }

        DocumentRecord document = documents.next()
        StringHandle sHandle = document.getContent(new StringHandle())
        Map jsonObj = JsonReader.jsonToMaps(sHandle.get())
        NCubeInfoDto dto = new NCubeInfoDto()
        dto.id = cubeId.toString()
        dto.tenant = jsonObj.tenant
        dto.app = jsonObj.app
        dto.version = jsonObj.version
        dto.status = jsonObj.status
        dto.branch = jsonObj.branch
        dto.name = jsonObj.name
        dto.sha1 = jsonObj.sha1
        dto.headSha1 = jsonObj.headSha1
        dto.revision = jsonObj.revision
        dto.createDate = (Date)Converter.convert(jsonObj.createDate, Date.class)
        dto.createHid = jsonObj.createHid
        if (options[SEARCH_INCLUDE_NOTES])
        {
            dto.notes = jsonObj.notes
        }
        dto.changed = jsonObj.changed
        if (options[SEARCH_INCLUDE_TEST_DATA])
        {
            dto.testData = JsonWriter.objectToJson(jsonObj.testData)
        }
        if (options[SEARCH_INCLUDE_CUBE_DATA])
        {
            // TODO there might be a better way to do this just using search
            InputStream is = getCubeStreamFromUri(uri)
            dto.bytes = IOUtilities.inputStreamToBytes(is)
        }
        return dto
    }

    static NCube loadCubeBySha1(ApplicationID appId, String cubeName, String sha1)
    {
        NCube ncube = null
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                name: cubeName,
                sha1: sha1
        ] as Map
        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "include", "extract-path" : ["/sha1", "/testData"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            InputStream is = getCubeStreamFromUri(document.uri)
            ncube = NCube.createCubeFromStream(is)

            JacksonHandle jHandle = document.getContent(new JacksonHandle())
            Iterator iterator = jHandle.get().get('extracted').iterator()
            StringBuilder testData = new StringBuilder('[')
            boolean first = true
            boolean hasTests = true
            while (iterator.hasNext())
            {
                JsonNode node = iterator.next()
                if (first)
                {
                    ncube.sha1 = node.get('sha1').textValue()
                    first = false
                }
                else
                {
                    if  (node.get('testData').null)
                    {
                        hasTests = false
                        break
                    }
                    testData.append(node.get('testData').toString())
                    if (iterator.hasNext())
                    {
                        testData.append(',')
                    }
                }
            }
            testData.append(']')
            if (hasTests)
            {
                ncube.testData = NCubeTestReader.convert(testData.toString()).toArray()
            }
        }

        if (ncube)
        {
            return ncube
        }
        throw new IllegalArgumentException("Unable to find cube: ${cubeName}, app: ${appId} with SHA-1: ${sha1}")
    }

    static boolean deleteBranch(ApplicationID appId)
    {
        // delete passed in ApplicationID
        List<String> docsToDelete = []
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch
        ] as Map

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            docsToDelete.add(document.uri)
        }
        documentManager.delete(transaction, docsToDelete.toArray(new String[documents.size()]))

        // check to see if any version other than 0.0.0 exists
        jsonProperties.remove('version')
        query = """{
"search": {
    "query": {
        "queries": [
            {"not-query": {"value-query": {"type": "string", "json-property": "version", "text": "0.0.0"}}},
            ${createAndQueries(jsonProperties)}
        ]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        documents = searchDocuments(query)

        // if no other versions exists, delete the 0.0.0 version
        if (documents.size() == 0)
        {
            query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
            docsToDelete = []
            jsonProperties.version = '0.0.0'
            documents = searchDocuments(query)
            while (documents.hasNext())
            {
                DocumentRecord document = documents.next()
                docsToDelete.add(document.uri)
            }
            documentManager.delete(transaction, docsToDelete.toArray(new String[documents.size()]))
        }

        return true
    }

    static boolean deleteApp(ApplicationID appId)
    {
        Map<String, List<String>> versions = getVersions(appId.tenant, appId.app)
        if (!versions[ReleaseStatus.RELEASE.name()].empty)
        {
            throw new IllegalArgumentException("Only applications without a released version can be deleted, app: ${appId}")
        }

        List<String> docsToDelete = []
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app
        ] as Map

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            docsToDelete.add(document.uri)
        }
        documentManager.delete(transaction, docsToDelete.toArray(new String[documents.size()]))

        return true
    }

    static boolean updateBranchCubeHeadSha1(Long cubeId, String branchSha1, String headSha1)
    {
        if (cubeId == null)
        {
            throw new IllegalArgumentException("Update branch cube's HEAD SHA-1, cube id cannot be empty")
        }

        if (StringUtilities.isEmpty(branchSha1))
        {
            throw new IllegalArgumentException("Update branch cube's SHA-1 cannot be empty")
        }

        if (StringUtilities.isEmpty(headSha1))
        {
            throw new IllegalArgumentException("Update branch cube's HEAD SHA-1, SHA-1 cannot be empty")
        }

        String uri = "/${cubeId}.json"
        String query = """{
"search": {
    "query": {
        "queries": [{"document-query": {"uri": ["/${cubeId}.json"]}}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}} 
}
}"""

        DocumentPage documents = searchDocuments(query)
        if (documents.size() == 0)
        {
            throw new IllegalArgumentException("error updating branch cube: ${cubeId}, to HEAD SHA-1: ${headSha1}, no record found.")
        }

        boolean changed = !StringUtilities.equalsIgnoreCase(branchSha1, headSha1)
        DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
        patchBuilder.replaceValue('/headSha1', headSha1)
        patchBuilder.replaceValue('/changed', changed)
        documentManager.patch(uri, patchBuilder.build(), transaction)
        return true
    }

    static int moveBranch(ApplicationID appId, String newSnapVer)
    {
        if (ApplicationID.HEAD == appId.branch)
        {
            throw new IllegalArgumentException('Cannot use moveBranch() API on HEAD branch')
        }

        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                branch: appId.branch
        ] as Map

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
            patchBuilder.replaceValue('/version', newSnapVer)
            documentManager.patch(document.uri, patchBuilder.build(), transaction)
        }

        return documents.size()
    }

    static int releaseCubes(ApplicationID appId)
    {
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: ReleaseStatus.SNAPSHOT.name(),
                branch: ApplicationID.HEAD
        ] as Map

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
            patchBuilder.replaceValue('/status', ReleaseStatus.RELEASE.name())
            documentManager.patch(document.uri, patchBuilder.build(), transaction)
        }

        return documents.size()
    }

    static int changeVersionValue(ApplicationID appId, String newVersion)
    {
        ApplicationID newSnapshot = appId.createNewSnapshotId(newVersion)
        if (doCubesExist(newSnapshot, true, 'changeVersionValue'))
        {
            throw new IllegalStateException("Cannot change version value to ${newVersion} because cubes with this version already exists.  Choose a different version number, app: ${appId}")
        }

        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: ReleaseStatus.SNAPSHOT.name(),
                branch: appId.branch
        ] as Map

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
            patchBuilder.replaceValue('/version', newVersion)
            documentManager.patch(document.uri, patchBuilder.build(), transaction)
        }

        if (documents.size() < 1)
        {
            throw new IllegalArgumentException("No SNAPSHOT n-cubes found with version ${appId.version}, therefore no versions updated, app: ${appId}")
        }

        return documents.size()
    }

    static boolean restoreCubes(ApplicationID appId, Object[] names, String username)
    {
        int count = 0
        names.each { String cubeName
            if (cubeName == null)
            {
                throw new IllegalArgumentException("Cannot restore cube: ${cubeName} as it is not deleted in app: ${appId}")
            }
            Map<String, Object> jsonProperties = [
                    tenant: appId.tenant,
                    app: appId.app,
                    version: appId.version,
                    status: appId.status,
                    branch: appId.branch,
                    tip: true,
                    active: false,
                    name: cubeName
            ] as Map

            String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
            DocumentPage documents = searchDocuments(query)
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())

            long txId = UniqueIdGenerator.uniqueId
            final String msg = "restored, txId: [${txId}]"
            String headSha1 = jsonObj.headSha1 ? "\"${jsonObj.headSha1}\"" : null
            Timestamp now = nowAsTimestamp()
            String mlFormat = """{
"tenant": "${jsonObj.tenant}",
"app": "${jsonObj.app}",
"version": "${jsonObj.version}",
"status": "${jsonObj.status}",
"branch": "${jsonObj.branch}",
"name": "${jsonObj.name}",
"sha1": "${jsonObj.sha1}",
"headSha1": ${headSha1},
"revision": ${(long)jsonObj.revision + 1L},
"active": true,
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, msg)}",
"changed": ${jsonObj.changed},
"json" : "",
"testData": ${JsonWriter.objectToJson(jsonObj.testData)}
}"""
            writeDocument(mlFormat, document.uri, true)
            count++
        }
        return count > 0
    }

    static int rollbackCubes(ApplicationID appId, Object[] names, String username)
    {
        int count = 0
        long txId = UniqueIdGenerator.uniqueId
        Timestamp now = nowAsTimestamp()
        String note = createNote(username, now, "rolled back, txId: [${txId}]")

        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch
        ] as Map

        names.each { String cubeName ->
            jsonProperties.name = cubeName
            String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
            DocumentPage documents = searchDocuments(query)
            if (documents.size() == 0)
            {
                LOG.info("Attempt to rollback non-existing cube: ${cubeName}, app: ${appId}")
            }
            else
            {
                Map<Long, Map> revToDoc = [:]
                long maxRev = 0L
                Map rollback = null
                while (documents.hasNext())
                {
                    DocumentRecord document = documents.next()
                    StringHandle handle = document.getContent(new StringHandle())
                    Map jsonObj = JsonReader.jsonToMaps(handle.get())
                    jsonObj.uri = document.uri
                    long revision = (long)jsonObj.revision
                    boolean active = (boolean)jsonObj.active
                    boolean sha1Match = jsonObj.sha1 == jsonObj.headSha1
                    boolean revisionIsGreater = revision > (Long)rollback?.revision
                    if (revision > maxRev)
                    {
                        maxRev = revision
                    }
                    if (active && sha1Match && revisionIsGreater)
                    {
                        rollback = jsonObj
                    }
                    revToDoc[revision] = jsonObj
                }
                Long rollbackRev = (Long)rollback?.revision
                boolean mustDelete = rollbackRev == null
                Long searchRev = mustDelete ? maxRev : rollbackRev
                Map searchDoc = revToDoc[searchRev]
                Map maxDoc = revToDoc[maxRev]
                boolean rollbackStatusActive = false

                if (rollback)
                {
                    Date branchDate = Converter.convertToDate(rollback.createDate)
                    Map<String, Object> headProperties = [
                            tenant: appId.tenant,
                            app: appId.app,
                            version: appId.version,
                            status: appId.status,
                            branch: ApplicationID.HEAD,
                            sha1: rollback.sha1,
                            name: cubeName
                    ] as Map

                    String headQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(headProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
                    Long headMaxRev = null
                    DocumentPage headDocs = searchDocuments(headQuery)
                    while (headDocs.hasNext())
                    {
                        DocumentRecord headDoc = headDocs.next()
                        StringHandle headHandle = headDoc.getContent(new StringHandle())
                        Map headObj = JsonReader.jsonToMaps(headHandle.get())
                        boolean headActive = (boolean)headObj.active
                        long headRev = (long)headObj.revision
                        Date headDate = Converter.convertToDate(headObj.createDate)
                        if (headRev > headMaxRev && headDate <= branchDate)
                        {
                            headMaxRev = headRev
                            rollbackStatusActive = headActive
                        }
                    }
                }
                boolean active = !(mustDelete || !rollbackStatusActive)

                String mlFormat = """{
"tenant": "${appId.tenant}",
"app": "${appId.app}",
"version": "${appId.version}",
"status": "${appId.status}",
"branch": "${appId.branch}",
"name": "${cubeName}",
"sha1": "${searchDoc.sha1}",
"headSha1": "${searchDoc.headSha1}",
"revision": ${maxRev + 1L},
"active": ${active},
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${note}",
"changed": false,
"json" : "",
"testData": ${JsonWriter.objectToJson(searchDoc.testData)}
}"""
                writeDocument(mlFormat, (String)searchDoc.uri)
                updatePreviousTipFalse((String)maxDoc.uri)
                count++
            }
        }
        return count
    }

    static List<NCubeInfoDto> pullToBranch(ApplicationID appId, Object[] cubeIds, String username, long txId)
    {
        List<NCubeInfoDto> infoRecs = []
        if (ArrayUtilities.isEmpty(cubeIds))
        {
            return infoRecs
        }
        createSysInfoCube(appId, username)

        for (int i = 0; i < cubeIds.length; i++)
        {
            String



            String uri = "/${cubeIds[i]}.json"
            String query = """{
"search": {
    "query": {
        "queries": [{"document-query": {"uri": ["${uri}"]}}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}} 
}
}"""
            DocumentPage documents = searchDocuments(query)
            DocumentRecord document = documents.next()
            StringHandle handle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(handle.get())
            String cubeName = (String)jsonObj.name

            Map<String, Object> branchProperties = [
                    tenant: appId.tenant,
                    app: appId.app,
                    version: appId.version,
                    status: appId.status,
                    branch: appId.branch,
                    tip: true,
                    name: cubeName
            ] as Map
            String branchQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(branchProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
            long branchRevision = 0L
            String branchUri = null
            DocumentPage branchDocs = searchDocuments(branchQuery)
            if (branchDocs.size() != 0)
            {
                DocumentRecord branchDoc = branchDocs.next()
                branchUri = branchDoc.uri
                StringHandle branchHandle = branchDoc.getContent(new StringHandle())
                Map branchObj = JsonReader.jsonToMaps(branchHandle.get())
                branchRevision = (long)branchObj.revision
            }

            Timestamp now = nowAsTimestamp()
            String notes = (String)jsonObj.notes
            String newNotes = "updated from ${jsonObj.branch}, txId: [${txId}]"
            if (notes.contains(PR_NOTES_PREFIX))
            {
                newNotes += ", ${notes.substring(notes.indexOf(PR_NOTES_PREFIX))}"
            }

            String mlFormat = """{
"tenant": "${appId.tenant}",
"app": "${appId.app}",
"version": "${appId.version}",
"status": "${appId.status}",
"branch": "${appId.branch}",
"name": "${cubeName}",
"sha1": "${jsonObj.sha1}",
"headSha1": "${jsonObj.sha1}",
"revision": ${branchRevision + 1L},
"active": ${jsonObj.active},
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, newNotes)}",
"changed": false,
"json" : "",
"testData": ${JsonWriter.objectToJson(jsonObj.testData)}
}"""
            writeDocument(mlFormat, uri)
            if (branchUri)
            {
                updatePreviousTipFalse(branchUri)
            }
        }
        return []
    }

    static boolean mergeAcceptTheirs(ApplicationID appId, String cubeName, String sourceBranch, String username)
    {
        ApplicationID sourceId = appId.asBranch(sourceBranch)
        ApplicationID headId = appId.asHead()

        Map<String, Object> sourceProperties = [
                tenant: sourceId.tenant,
                app: sourceId.app,
                version: sourceId.version,
                status: sourceId.status,
                branch: sourceId.branch,
                tip: true,
                name: cubeName
        ] as Map
        String sourceQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(sourceProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage sourceDocs = searchDocuments(sourceQuery)
        if (sourceDocs.size() == 0)
        {
            throw new IllegalStateException("Failed to overwrite cube in your branch, because ${cubeName} does not exist in ${sourceId}")
        }
        DocumentRecord sourceDoc = sourceDocs.next()
        StringHandle sourceHandle = sourceDoc.getContent(new StringHandle())
        Map sourceObj = JsonReader.jsonToMaps(sourceHandle.get())
        String sourceTestData = JsonWriter.objectToJson(sourceObj.testData)
        String sourceSha1 = (String)sourceObj.sha1
        String sourceHeadSha1 = (String)sourceObj.headSha1
        boolean sourceActive = (boolean)sourceObj.active

        Map<String, Object> branchProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true,
                name: cubeName
        ] as Map
        String branchQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(branchProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage branchDocs = searchDocuments(branchQuery)
        String branchUri = null
        Long newRevision = null
        String targetHeadSha1 = null
        if (branchDocs.size() != 0)
        {
            DocumentRecord branchDoc = branchDocs.next()
            branchUri = branchDoc.uri
            StringHandle branchHandle = branchDoc.getContent(new StringHandle())
            Map branchObj = JsonReader.jsonToMaps(branchHandle.get())
            newRevision = (long)branchObj.revision
            targetHeadSha1 = (String)branchObj.sha1
        }

        Map<String, Object> headProperties = [
                tenant: headId.tenant,
                app: headId.app,
                version: headId.version,
                status: headId.status,
                branch: headId.branch,
                tip: true,
                name: cubeName
        ] as Map
        String headQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(headProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage headDocs = searchDocuments(headQuery)
        String actualHeadSha1 = null
        if (headDocs.size() != 0)
        {
            DocumentRecord headDoc = headDocs.next()
            StringHandle headHandle = headDoc.getContent(new StringHandle())
            Map headObj = JsonReader.jsonToMaps(headHandle.get())
            actualHeadSha1 = (String)headObj.sha1
        }

        Timestamp now = nowAsTimestamp()
        String notes = "merge: ${sourceBranch} accepted over branch"
        long rev = newRevision == null ? 0L : newRevision + 1L
        boolean active = sourceActive

        String headSha1
        boolean changed = false
        if (sourceBranch == ApplicationID.HEAD)
        {
            headSha1 = sourceSha1
        }
        else if (StringUtilities.equalsIgnoreCase(sourceSha1, actualHeadSha1))
        {
            headSha1 = actualHeadSha1
        }
        else
        {
            headSha1 = targetHeadSha1
            changed = true
        }

        if (StringUtilities.equalsIgnoreCase(sourceSha1, sourceHeadSha1))
        {
            changed = true
        }

        String mlFormat = """{
"tenant": "${appId.tenant}",
"app": "${appId.app}",
"version": "${appId.version}",
"status": "${appId.status}",
"branch": "${appId.branch}",
"name": "${cubeName}",
"sha1": "${sourceSha1}",
"headSha1": "${headSha1}",
"revision": ${rev},
"active": ${active},
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, notes)}",
"changed": ${changed},
"json" : "",
"testData": ${sourceTestData}
}"""
        writeDocument(mlFormat, sourceDoc.uri)
        if (branchUri)
        {
            updatePreviousTipFalse(branchUri)
        }
        return false
    }

    static boolean mergeAcceptMine(ApplicationID appId, String cubeName, String username)
    {
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: ApplicationID.HEAD,
                tip: true,
                name: cubeName
        ] as Map

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage headDocs = searchDocuments(query)
        if (headDocs.size() == 0)
        {
            throw new IllegalStateException("failed to update branch cube because HEAD cube does not exist: ${cubeName}, app: ${appId}")
        }
        DocumentRecord headDoc = headDocs.next()
        StringHandle headHandle = headDoc.getContent(new StringHandle())
        Map headObj = JsonReader.jsonToMaps(headHandle.get())
        String headSha1 = (String)headObj.sha1

        jsonProperties.branch = appId.branch
        query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}
}
}"""
        DocumentPage documents = searchDocuments(query)
        if (documents.size() == 0)
        {
            throw new IllegalStateException("failed to update branch cube because branch cube does not exist: ${cubeName}, app: ${appId}")
        }
        DocumentRecord document = documents.next()
        StringHandle handle = document.getContent(new StringHandle())
        Map jsonObj = JsonReader.jsonToMaps(handle.get())

        String notes = 'merge: branch accepted over head'
        Timestamp now = nowAsTimestamp()
        String mlFormat = """{
"tenant": "${jsonObj.tenant}",
"app": "${jsonObj.app}",
"version": "${jsonObj.version}",
"status": "${jsonObj.status}",
"branch": "${jsonObj.branch}",
"name": "${jsonObj.name}",
"sha1": "${jsonObj.sha1}",
"headSha1": "${headSha1}",
"revision": ${(long)jsonObj.revision + 1L},
"active": ${jsonObj.active},
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, notes)}",
"changed": ${jsonObj.changed},
"json" : "",
"testData": ${JsonWriter.objectToJson(jsonObj.testData)}
}"""
        writeDocument(mlFormat, document.uri, true)
        return false
    }

    static NCubeInfoDto commitMergedCubeToHead(ApplicationID appId, NCube cube, String username, String requestUser, String txId, String notes)
    {
        ApplicationID headId = appId.asHead()
        Map<String, Object> branchProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true,
                name: cube.name
        ] as Map
        String branchQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(branchProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage branchDocs = searchDocuments(branchQuery)
        if (branchDocs.size() == 0)
        {
            return null
        }
        DocumentRecord branchDoc = branchDocs.next()
        String branchUri = branchDoc.uri
        StringHandle branchHandle = branchDoc.getContent(new StringHandle())
        Map branchObj = JsonReader.jsonToMaps(branchHandle.get())
        long branchRevision = (Long)branchObj.revision
        long newBranchRevision = branchRevision + 1L
        boolean branchActive = (boolean)branchObj.active

        Map<String, Object> headProperties = [
                tenant: headId.tenant,
                app: headId.app,
                version: headId.version,
                status: headId.status,
                branch: headId.branch,
                tip: true,
                name: cube.name
        ] as Map
        String headQuery = """{
"search": {
    "query": {
        "queries": [${createAndQueries(headProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""

        long newHeadRevision = branchActive ? 0L : -1L
        boolean headAcive = branchActive
        String headUri = null
        DocumentPage headDocs = searchDocuments(headQuery)
        if (headDocs.size() != 0)
        {
            DocumentRecord headDoc = headDocs.next()
            headUri = headDoc.uri
            StringHandle headHandle = headDoc.getContent(new StringHandle())
            Map headObj = JsonReader.jsonToMaps(headHandle.get())
            long headRevision = (long)headObj.revision
            newHeadRevision = headRevision + 1L
        }

        String sha1 = cube.sha1()
        Timestamp now = nowAsTimestamp()
        String noteText = "merged-committed from [${requestUser}], txId: [${txId}], ${PR_NOTES_PREFIX}${notes}"
        String note = createNote(username, now, noteText)

        String headFormat = """{
"tenant": "${headId.tenant}",
"app": "${headId.app}",
"version": "${headId.version}",
"status": "${headId.status}",
"branch": "${headId.branch}",
"name": "${cube.name}",
"sha1": "${sha1}",
"headSha1": null,
"revision": ${newHeadRevision},
"active": ${headAcive},
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${note}",
"changed": false,
"json" : "",
"testData": ${JsonWriter.objectToJson(branchObj.testData)}
}"""
        writeDocument(headFormat, cube, headUri)

        String branchFormat = """{
"tenant": "${appId.tenant}",
"app": "${appId.app}",
"version": "${appId.version}",
"status": "${appId.status}",
"branch": "${appId.branch}",
"name": "${cube.name}",
"sha1": "${sha1}",
"headSha1": "${sha1}",
"revision": ${newBranchRevision},
"active": ${branchActive},
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${note}",
"changed": false,
"json" : "",
"testData": ${JsonWriter.objectToJson(branchObj.testData)}
}"""
        String id = writeDocument(branchFormat, cube, branchUri)

        NCubeInfoDto dto = new NCubeInfoDto()
        dto.id = id
        dto.name = cube.name
        dto.sha1 = sha1
        dto.headSha1 = sha1
        dto.changed = false
        dto.tenant = appId.tenant
        dto.app = appId.app
        dto.version = appId.version
        dto.status = appId.status
        dto.branch = appId.branch
        dto.createDate = now
        dto.createHid = username
        dto.notes = note
        dto.revision = newBranchRevision.toString()
        return dto
    }

    static NCubeInfoDto commitMergedCubeToBranch(ApplicationID appId, NCube cube, String headSha1, String username, long txId)
    {
        NCubeInfoDto dto = null
        boolean changed = !StringUtilities.equalsIgnoreCase(cube.sha1(), headSha1)
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true,
                name: cube.name
        ] as Map
        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        if (documents.size() != 0)
        {
            DocumentRecord document = documents.next()
            StringHandle handle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(handle.get())
            long revision = (long)jsonObj.revision + 1L

            String testData = JsonWriter.objectToJson(jsonObj.testData)
            if (cube.metaProperties.containsKey(NCube.METAPROPERTY_TEST_DATA))
            {
                String updatedTestData = (String)cube.metaProperties[NCube.METAPROPERTY_TEST_DATA]
                if ((updatedTestData || testData) && updatedTestData != testData)
                {
                    testData = updatedTestData
                }
            }

            String notes = (String)jsonObj.notes
            String newNotes = "merged to branch, txId: [${txId}]"
            if (notes.contains(PR_NOTES_PREFIX))
            {
                newNotes += ", ${notes.substring(notes.indexOf(PR_NOTES_PREFIX))}"
            }

            String sha1 = cube.sha1()
            Timestamp now = nowAsTimestamp()
            String note = createNote(username, now, newNotes)

            String mlFormat = """{
"tenant": "${appId.tenant}",
"app": "${appId.app}",
"version": "${appId.version}",
"status": "${appId.status}",
"branch": "${appId.branch}",
"name": "${cube.name}",
"sha1": "${sha1}",
"headSha1": "${headSha1}",
"revision": ${revision},
"active": ${jsonObj.active},
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${note}",
"changed": ${changed},
"json" : "",
"testData": ${testData}
}"""
            String id = writeDocument(mlFormat, cube, document.uri)

            dto = new NCubeInfoDto()
            dto.id = id
            dto.name = cube.name
            dto.sha1 = sha1
            dto.headSha1 = headSha1
            dto.changed = changed
            dto.tenant = appId.tenant
            dto.app = appId.app
            dto.version = appId.version
            dto.status = appId.status
            dto.branch = appId.branch
            dto.createDate = now
            dto.createHid = username
            dto.notes = note
            dto.revision = revision.toString()
        }
        return dto
    }

    static List<String> getAppNames(String tenant)
    {
        if (StringUtilities.isEmpty(tenant))
        {
            throw new IllegalArgumentException("error calling getAppVersions(), tenant cannot be null or empty")
        }

        List<String> appNames = (List)getValuesForIndex('app', [tenant: tenant] as Map)
        return appNames
    }

    static Map<String, List<String>> getVersions(String tenant, String app)
    {
        if (StringUtilities.isEmpty(tenant) || StringUtilities.isEmpty(app))
        {
            throw new IllegalArgumentException("error calling getAppVersions() tenant: ${tenant} or app: ${app} cannot be null or empty")
        }

        Map<String, List<String>> versions = [:]

        Map jsonProperties = [tenant: tenant, app: app, status: ReleaseStatus.SNAPSHOT.name()] as Map
        versions[ReleaseStatus.SNAPSHOT.name()] = (List)getValuesForIndex('version', jsonProperties)

        jsonProperties['status'] = ReleaseStatus.RELEASE.name()
        versions[ReleaseStatus.RELEASE.name()] = (List)getValuesForIndex('version', jsonProperties)

        return versions
    }

    static Set<String> getBranches(ApplicationID appId)
    {
        Map jsonProperties = [tenant: appId.tenant, app: appId.app, version: appId.version, status: appId.status] as Map
        Set<String> branches = (Set)getValuesForIndex('branch', jsonProperties)
        return branches
    }

    private static Collection<String> getValuesForIndex(String indexName, Map<String, Object> jsonProperties)
    {
        List values = []
        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {
        "values": {
            "name": "${indexName}",
            "range": {"type": "xs:string", "element": {"ns": "", "name": "${indexName}"}}
        }
    }  
}
}"""
        StringHandle queryHandle = new StringHandle(query).withFormat(Format.JSON)
        RawCombinedQueryDefinition queryDef = queryManager.newRawCombinedQueryDefinition(queryHandle)
        ValuesDefinition valuesDefinition = queryManager.newValuesDefinition(indexName)
        valuesDefinition.queryDefinition = queryDef
        ValuesHandle handle = queryManager.values(valuesDefinition, new ValuesHandle(), transaction)
        CountedDistinctValue[] indexValues = handle.values
        indexValues.each { CountedDistinctValue value -> values.add(value.get('xs:string', String.class)) }
        return values
    }

    /**
     * Check for existence of a cube with this appId.  You can ignoreStatus if you want to check for existence of
     * a SNAPSHOT or RELEASE cube.
     * @param ignoreStatus - If you want to ignore status (check for both SNAPSHOT and RELEASE cubes in existence) pass
     *                     in true.
     * @return true if any cubes exist for the given AppId, false otherwise.
     */
    static boolean doCubesExist(ApplicationID appId, boolean ignoreStatus, String methodName)
    {
        Map jsonProperties = [tenant: appId.tenant, app: appId.app, version: appId.version, branch: appId.branch, name: SYS_INFO] as Map
        if (!ignoreStatus)
        {
            jsonProperties['status'] = appId.status
        }
        String queries = createAndQueries(jsonProperties)

        String query = """{
"search": {
    "query": {
        "queries": [${queries}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)

        return documents.size() > 0
    }

    static int copyBranch(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    {
        if (doCubesExist(targetAppId, true, 'copyBranch'))
        {
            throw new IllegalArgumentException("Branch '${targetAppId.branch}' already exists, app: ${targetAppId}")
        }

        Map<String, Object> jsonProperties = [tenant: srcAppId.tenant, app: srcAppId.app, version: srcAppId.version, status: srcAppId.status, branch: srcAppId.branch] as Map
        String queries = createAndQueries(jsonProperties)
        String query = """{
"search": {
    "query": {
        "queries": [${queries}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)

        int count = 0
        Map<String, List<Map>> ncubesByName = [:]
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())
            jsonObj.uri = document.uri
            String name = jsonObj.name

            if (ncubesByName[name])
            {
                (ncubesByName[name]).add(jsonObj)
            }
            else
            {
                ncubesByName[name] = [jsonObj]
            }
        }

        for (Entry<String, List<Map>> entry : ncubesByName)
        {
            List<Map> revisions = entry.value
            Collections.sort(revisions, new Comparator<Map>() {
                int compare(Map o1, Map o2)
                {
                    return ((long) o2.revision) <=> (long) o1.revision
                }
            })
            for (Map jsonObj : revisions) // iterate through revisions starting with highest revision
            {
                String headSha1 = (String)jsonObj.headSha1
                if (headSha1 == jsonObj.head || (jsonObj.tip && !headSha1))
                {
                    Timestamp now = nowAsTimestamp()
                    String oldNotes = removePreviousNotesCopyMessage((String)jsonObj.notes)
                    String notes = "target ${targetAppId} copied from ${srcAppId} - ${oldNotes}"
                    String mlFormat = """{
"tenant": "${targetAppId.tenant}",
"app": "${targetAppId.app}",
"version": "${targetAppId.version}",
"status": "${targetAppId.status}",
"branch": "${targetAppId.branch}",
"name": "${jsonObj.name}",
"sha1": "${jsonObj.sha1}",
"headSha1": ${headSha1 ? "\"${headSha1}\"" : null},
"revision": ${jsonObj.revision},
"active": ${jsonObj.active},
"tip": ${jsonObj.tip},
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${notes}",
"changed": ${jsonObj.changed},
"json" : "",
"testData": ${JsonWriter.objectToJson(jsonObj.testData)}
}"""

                    writeDocument(mlFormat, (String)jsonObj.uri)
                    count++
                    break
                }
            }
        }
        return count
    }

    static int copyBranchWithHistory(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    {
        if (doCubesExist(targetAppId, true, 'copyBranch'))
        {
            throw new IllegalStateException("Branch '${targetAppId.branch}' already exists, app: ${targetAppId}")
        }

        Map<String, Object> jsonProperties = [tenant: srcAppId.tenant, app: srcAppId.app, version: srcAppId.version, status: srcAppId.status, branch: srcAppId.branch] as Map
        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""

        DocumentPage documents = searchDocuments(query)
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())

            boolean changed = targetAppId.head ? false : jsonObj.changed
            String headSha1 = null
            if (!targetAppId.head)
            {
                headSha1 = srcAppId.head ? jsonObj.sha1 : jsonObj.headSha1
            }

            Timestamp now = nowAsTimestamp()
        String mlFormat = """{
"tenant": "${targetAppId.tenant}",
"app": "${targetAppId.app}",
"version": "${targetAppId.version}",
"status": "${targetAppId.status}",
"branch": "${targetAppId.branch}",
"name": "${jsonObj.name}",
"sha1": "${jsonObj.sha1}",
"headSha1": ${headSha1 ? "\"${headSha1}\"" : null},
"revision": ${jsonObj.revision},
"active": ${jsonObj.active},
"tip": ${jsonObj.tip},
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${"target ${targetAppId} full copied from ${srcAppId} - ${jsonObj.notes}"}",
"changed": ${changed},
"json" : "",
"testData": ${JsonWriter.objectToJson(jsonObj.testData)}
}"""

            writeDocument(mlFormat, document.uri)
        }
        documents.size()
    }

    static String getTestData(ApplicationID appId, String cubeName)
    {
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true,
                name: cubeName
        ] as Map
        Map appTests = getTestsPerName(jsonProperties)
        if (appTests.size() == 0 )
        {
            throw new IllegalArgumentException("Could not fetch test data, ncube: ${cubeName} does not exist in app: ${appId}")
        }
        return appTests[cubeName]
    }

    static Map getAppTestData(ApplicationID appId)
    {
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true
        ] as Map
        Map appTests = getTestsPerName(jsonProperties)
        return appTests
    }

    private static Map getTestsPerName(Map jsonProperties)
    {
        Map appTests = [:]
        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "include", "extract-path" : ["/name", "/testData"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            JacksonHandle jHandle = document.getContent(new JacksonHandle())
            Iterator iterator = jHandle.get().get('extracted').iterator()
            String name = null
            StringBuilder testData = new StringBuilder('[')
            boolean first = true
            boolean nullTest = false
            while (iterator.hasNext())
            {
                JsonNode node = iterator.next()
                if (first)
                {
                    name = node.get('name').textValue()
                    first = false
                }
                else
                {
                    if (node.get('testData').null)
                    {
                        nullTest = true
                        break
                    }
                    testData.append(node.get('testData').toString())
                    if (iterator.hasNext())
                    {
                        testData.append(',')
                    }
                }
            }
            testData.append(']')
            if (name && 'sys.info' != name)
            {
                appTests[name] = nullTest ? '': testData.toString()
            }
        }
        return appTests
    }

    static List<NCubeInfoDto> getRevisions(ApplicationID appId, String cubeName, boolean ignoreVersion)
    {
        List<NCubeInfoDto> records = []
        Map<String, Object> jsonProperties = [tenant: appId.tenant, app: appId.app, branch: appId.branch, name: cubeName] as Map
        if (!ignoreVersion)
        {
            jsonProperties.version = appId.version
            jsonProperties.status = appId.status
        }

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json", "/testData"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        if (documents.size() == 0)
        {
            throw new IllegalArgumentException("Cannot fetch revision history for cube: ${cubeName} as it does not exist in app: ${appId}")
        }
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())
            NCubeInfoDto dto = new NCubeInfoDto()
            String uri = document.uri
            dto.id = getIdFromUri(uri)
            dto.tenant = jsonObj.tenant
            dto.app = jsonObj.app
            dto.version = jsonObj.version
            dto.status = jsonObj.status
            dto.branch = jsonObj.branch
            dto.name = jsonObj.name
            dto.sha1 = jsonObj.sha1
            dto.headSha1 = jsonObj.headSha1
            dto.revision = convertToDtoRev((boolean)jsonObj.active, (long)jsonObj.revision)
            dto.createDate = (Date)Converter.convert(jsonObj.createDate, Date.class)
            dto.createHid = jsonObj.createHid
            dto.changed = jsonObj.changed
            dto.notes = jsonObj.notes
            records.add(dto)
        }
        // if ignoreVersion sort by version then revision, else sort by revision
        if (ignoreVersion)
        {
            Collections.sort(records, new Comparator<NCubeInfoDto>() {
                int compare(NCubeInfoDto o1, NCubeInfoDto o2)
                {
                    long v1 = ApplicationID.getVersionValue(o1.version)
                    long v2 = ApplicationID.getVersionValue(o2.version)
                    long diff = v2 - v1
                    if (diff != 0)
                    {
                        return diff
                    }
                    return Long.valueOf(o2.revision) <=> Long.valueOf(o1.revision)
                }
            })
        }
        else
        {
            Collections.sort(records, new Comparator<NCubeInfoDto>() {
                int compare(NCubeInfoDto o1, NCubeInfoDto o2)
                {
                    return Long.valueOf(o2.revision) <=> Long.valueOf(o1.revision)
                }
            })
        }
        return records
    }

    /**
     * First, write non-json portion of the document and then write json portion representing the NCube.
     * @param mlFormat String representation of MarkLogic JSON format
     * @param copyFrom URI to copy /json property from
     */
    private static String writeDocument(String mlFormat, String copyFrom, boolean newTip = false)
    {
        // write non-json portion of document
        String id = UniqueIdGenerator.uniqueId
        String docId = "/${id}.json"
        documentManager.write(docId, new StringHandle(mlFormat).withFormat(Format.JSON), transaction)

        // write json portion of document (so NCube is not created in memory as a String)
        String xquery = """fn:doc("${copyFrom}")/json"""
        ServerEvaluationCall call = client.newServerEval().transaction(transaction).xquery(xquery)
        DocumentPatchHandle jsonHandle = call.eval(new InputStreamHandle().withFormat(Format.JSON))
        DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
        patchBuilder.replaceFragment('/json', jsonHandle)
        jsonHandle.close()
        documentManager.patch(docId, patchBuilder.build(), transaction)

        if (newTip)
        {
            updatePreviousTipFalse(copyFrom)
        }
        return id
    }

    /**
     * First, write non-json portion of the document and then write json portion representing the NCube.
     * @param mlFormat String representation of MarkLogic JSON format
     * @param ncube NCube to write
     */
    private static String writeDocument(String mlFormat, NCube ncube, String prevUri = null)
    {
        // write non-json portion of document
        String id = UniqueIdGenerator.uniqueId
        String docId = "/${id}.json"
        documentManager.write(docId, new StringHandle(mlFormat).withFormat(Format.JSON), transaction)

        // write json portion of document (so NCube is not created in memory as a String)
        InputStream json = new GZIPInputStream(new ByteArrayInputStream(ncube.cubeAsGzipJsonBytes))
        DocumentPatchHandle patchHandle = new InputStreamHandle(json).withFormat(Format.JSON)
        DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
        patchBuilder.replaceFragment('/json', patchHandle)
        documentManager.patch(docId, patchBuilder.build(), transaction)

        if (prevUri)
        {
            updatePreviousTipFalse(prevUri)
        }
        return id
    }

    private static void updatePreviousTipFalse(String uri)
    {
        DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
        patchBuilder.replaceFragment('/tip', false)
        documentManager.patch(uri, patchBuilder.build(), transaction)
    }

    private static InputStream getCubeStreamFromUri(String uri)
    {
        String xquery = """fn:doc("${uri}")/json"""
        ServerEvaluationCall call = client.newServerEval().transaction(transaction).xquery(xquery)
        InputStreamHandle handle = call.eval(new InputStreamHandle().withFormat(Format.JSON))
        InputStream is = handle.get()
        return is
    }

    private static DocumentPage searchDocuments(String query)
    {
        StringHandle queryHandle = new StringHandle(query).withFormat(Format.JSON)
        RawCombinedQueryDefinition queryDef = queryManager.newRawCombinedQueryDefinition(queryHandle)
        DocumentPage documents = documentManager.search(queryDef, 1, transaction)
        return documents
    }

    private static String createAndQueries(Map<String, Object> jsonProperties)
    {
        StringBuilder sb = new StringBuilder()
        Iterator jsonEntries = jsonProperties.entrySet().iterator()
        while (jsonEntries.hasNext())
        {
            Entry entry = jsonEntries.next()
            def value = entry.value
            String type
            if (value instanceof String)
            {
                type = 'string'
            }
            else if (value instanceof Boolean)
            {
                type = 'boolean'
            }
            else
            {
                type = 'number'
            }
            sb.append("""{"value-query": {"type": "${type}", "json-property": "${entry.key}", "text" : "${entry.value}"}}""")
            if (jsonEntries.hasNext())
            {
                sb.append(',')
            }
        }
        return sb.toString()
    }

    private static createOrQueries(String type, String propertyName, Set properties)
    {
        StringBuilder sb = new StringBuilder()
        Iterator jsonEntries = properties.iterator()
        while (jsonEntries.hasNext())
        {
            def entry = jsonEntries.next()
            sb.append("""{"value-query": {"type": "${type}", "json-property": "${propertyName}", "text" : "${entry}"}}""")
            if (jsonEntries.hasNext())
            {
                sb.append(',')
            }
        }
        return sb.toString()
    }

    static void clearTestDatabase()
    {
        List<String> uris = []
        ServerEvaluationCall call = client.newServerEval().transaction(transaction).xquery('cts:uris()')
        EvalResultIterator i = call.eval()
        while (i.hasNext())
        {
            EvalResult result = i.next()
            uris.add(result.string)
        }
        if (uris)
        {
            documentManager.delete(transaction, uris.toArray(new String[0]))
        }
        i.close()
    }

    static boolean updateNotes(ApplicationID appId, String cubeName, String notes)
    {
        Map<String, Object> jsonProperties = [
                tenant: appId.tenant,
                app: appId.app,
                version: appId.version,
                status: appId.status,
                branch: appId.branch,
                tip: true,
                name: cubeName
        ] as Map

        String query = """{
"search": {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {"extract-document-data": {"selected": "exclude", "extract-path" : ["/json"]}}  
}
}"""
        DocumentPage documents = searchDocuments(query)
        if (documents.size() == 0)
        {
            throw new IllegalArgumentException("Cannot update notes, cube: ${cubeName} does not exist in app: ${appId}")
        }
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
            patchBuilder.replaceValue('/notes', notes)
            documentManager.patch(document.uri, patchBuilder.build(), transaction)
        }
        return documents.size() == 1
    }

    /**
     * Create sys.info if it doesn't exist.
     */
    private static void createSysInfoCube(ApplicationID appId, String username)
    {
        List<NCubeInfoDto> records = search(appId, SYS_INFO, null,
                [(SEARCH_INCLUDE_CUBE_DATA): false,
                 (SEARCH_EXACT_MATCH_NAME): true,
                 (SEARCH_ACTIVE_RECORDS_ONLY):false,
                 (SEARCH_DELETED_RECORDS_ONLY):false,
                 (SEARCH_ALLOW_SYS_INFO):true
                ] as Map)

        if (!records.empty)
        {
            return
        }
        NCube sysInfo = new NCube(SYS_INFO)
        Axis attribute = new Axis(AXIS_ATTRIBUTE, AxisType.DISCRETE, AxisValueType.CISTRING, true)
        sysInfo.addAxis(attribute)
        sysInfo.applicationID = appId
        createCube(sysInfo, username)
    }

    private static String convertToDtoRev(boolean active, long revision)
    {
        String number = Long.toString(revision)
        return active ? number : "-${number}"
    }

    private static String getIdFromUri(String uri)
    {
        return uri.substring(uri.indexOf('/')+1, uri.indexOf('.json'))
    }

    private static Transaction getTransaction()
    {
        NCubeManagerAdvice managerAdvice = (NCubeManagerAdvice)NCubeAppContext.getBean('ncubeManagerAdvice')
        return managerAdvice.transaction
    }

    private static String removePreviousNotesCopyMessage(String oldNotes)
    {
        if (oldNotes)
        {
            int copyMsgIdx = oldNotes.lastIndexOf('copied from')
            return copyMsgIdx > -1 ? oldNotes.substring(oldNotes.indexOf(' - ', copyMsgIdx) + 3) : oldNotes
        }
        else
        {
            return ''
        }
    }

    // ----- Methods belows were copied from NCubeJdbcPersister ----- //
    protected static String createNote(String user, Date date, String notes)
    {
        return "${DATE_TIME_FORMAT.format(date)} [${user}] ${notes}"
    }

    protected static boolean toBoolean(Object value)
    {
        if (value == null)
        {
            return false
        }
        return ((Boolean)value).booleanValue()
    }

    private static Timestamp nowAsTimestamp()
    {
        return new Timestamp(System.currentTimeMillis())
    }

    private static String buildName(String name)
    {
        return name?.toLowerCase()
    }

    private static String convertPattern(String pattern)
    {
        if (pattern.contains('%'))
        {
            pattern = pattern.replace('%', '')
        }
        return pattern
    }

}
