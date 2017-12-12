package com.cedarsoftware.ncube

import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.UniqueIdGenerator
import com.cedarsoftware.util.io.JsonReader
import com.google.common.collect.Sets
import com.google.common.io.ByteStreams
import com.marklogic.client.DatabaseClient
import com.marklogic.client.Transaction
import com.marklogic.client.admin.ServerConfigurationManager
import com.marklogic.client.document.DocumentPage
import com.marklogic.client.document.DocumentPatchBuilder
import com.marklogic.client.document.DocumentRecord
import com.marklogic.client.document.JSONDocumentManager
import com.marklogic.client.eval.EvalResult
import com.marklogic.client.eval.EvalResultIterator
import com.marklogic.client.eval.ServerEvaluationCall
import com.marklogic.client.io.Format
import com.marklogic.client.io.InputStreamHandle
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
import java.util.zip.GZIPInputStream

import static com.cedarsoftware.ncube.NCubeConstants.*

/**
 * SQL Persister for n-cubes.  Manages all reads and writes of n-cubes to an SQL database.
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
    private static ServerConfigurationManager serverConfigurationManager

    MarkLogicPersister(DatabaseClient client)
    {
        this.client = client
        queryManager = client.newQueryManager()
        documentManager = client.newJSONDocumentManager()
        serverConfigurationManager = client.newServerConfigManager()
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

        String termQuery = StringUtilities.hasContent(searchContent) ? """{"term-query": {"json-property": "json", "text" : "${searchContent}"}},""" : ''
        StringBuilder sb = new StringBuilder(termQuery)

        Map<String, Object> jsonProperties = [tenant: appId.tenant, app: appId.app, version: appId.version, status: appId.status, branch: appId.branch, tip: true] as Map
        if (activeRecordsOnly)
        {
            jsonProperties['active'] = true
        }
        else if (deletedRecordsOnly)
        {
            jsonProperties['active'] = false
        }

        if (StringUtilities.hasContent(cubeNamePattern))
        {
            jsonProperties['ncube'] = cubeNamePattern
        }
        String searchQueries = createAndQueries(jsonProperties)
        sb.append(searchQueries)
        String queries = sb.toString()

        String query = """{
"search" : {
    "query": {
        "queries": [${queries}]
    },
    "options": {
        "extract-document-data": {
            "selected": "exclude",
            "extract-path" : ["/json"]
        }
    }  
}
}"""

        StringHandle queryHandle = new StringHandle(query).withFormat(Format.JSON)
        RawCombinedQueryDefinition queryDef = queryManager.newRawCombinedQueryDefinition(queryHandle)
        DocumentPage documents = documentManager.search(queryDef, 1, transaction)

        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())
            NCubeInfoDto dto = new NCubeInfoDto()
            String uri = document.uri
            dto.id = uri.substring(uri.indexOf('/')+1, uri.indexOf('.json'))
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
                String xquery = """fn:doc("${uri}")/json"""
                ServerEvaluationCall call = client.newServerEval().transaction(transaction).xquery(xquery)
                InputStreamHandle result = call.eval(new InputStreamHandle())
                dto.bytes = ByteStreams.toByteArray(result.get())
                result.close()
            }
            if (options[SEARCH_INCLUDE_TEST_DATA])
            {
                dto.testData = jsonObj.testData
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
"headSha1": ${null},
"revision": ${0L},
"active": true,
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, 'created')}",
"changed": "${username}",
"json" : "",
"testData": ${ncube.metaProperties[NCube.METAPROPERTY_TEST_DATA] ?: null}
}"""

        String docId = "/${UniqueIdGenerator.uniqueId}.json"

        // write non-json portion of document
        documentManager.write(docId, new StringHandle(mlFormat).withFormat(Format.JSON), transaction)

        // write json portion of document (so NCube is not created in memory as a String)
        InputStream json = new GZIPInputStream(new ByteArrayInputStream(ncube.cubeAsGzipJsonBytes))
        DocumentPatchHandle jsonHandle = new InputStreamHandle(json).withFormat(Format.JSON)

        DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
        patchBuilder.replaceFragment('/json', jsonHandle)
        documentManager.patch(docId, patchBuilder.build(), transaction)

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
                ncube: ncube.name
        ] as Map

        String query = """{
"search" : {
    "query": {
        "queries": [${createAndQueries(jsonProperties)}]
    },
    "options": {
        "extract-document-data": {
            "selected": "exclude",
            "extract-path" : ["/json"]
        }
    }  
}
}"""

        StringHandle queryHandle = new StringHandle(query).withFormat(Format.JSON)
        RawCombinedQueryDefinition queryDef = queryManager.newRawCombinedQueryDefinition(queryHandle)
        DocumentPage documents = documentManager.search(queryDef, 1, transaction)

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
        boolean cubeActive = jsonObj.active
        String headSha1 = jsonObj.headSha1
        String oldSha1 = jsonObj.sha1
        byte[] testData = StringUtilities.getUTF8Bytes((String)jsonObj.testData)

        if (ncube.metaProperties.containsKey(NCube.METAPROPERTY_TEST_DATA))
        {
            byte[] updatedTestData = StringUtilities.getUTF8Bytes(ncube.metaProperties[NCube.METAPROPERTY_TEST_DATA] as String)
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
"headSha1": ${null},
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

        String docId = "/${UniqueIdGenerator.uniqueId}.json"

        // write non-json portion of document
        documentManager.write(docId, new StringHandle(mlFormat).withFormat(Format.JSON), transaction)

        // write json portion of document (so NCube is not created in memory as a String)
        InputStream json = new GZIPInputStream(new ByteArrayInputStream(ncube.cubeAsGzipJsonBytes))
        DocumentPatchHandle jsonHandle = new InputStreamHandle(json).withFormat(Format.JSON)

        DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
        patchBuilder.replaceFragment('/json', jsonHandle)
        documentManager.patch(docId, patchBuilder.build(), transaction)

        // update previous document tip = false
        DocumentPatchBuilder tipPb = documentManager.newPatchBuilder()
        tipPb.replaceFragment('/tip', new StringHandle('false'))
        documentManager.patch(prevDoc.uri, tipPb.build(), transaction)
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

        String query = """{
"search" : {
    "query": {
        "queries": [
            ${createAndQueries(jsonProperties)},
            {"or-query": {
                "queries": [${createOrQueries('string', 'ncube', Sets.newHashSet(cubeNames))}]
            }}
        ]
    },
    "options": {
        "extract-document-data": {
            "selected": "exclude",
            "extract-path" : ["/json"]
        }
    }  
}
}"""

        StringHandle queryHandle = new StringHandle(query).withFormat(Format.JSON)
        RawCombinedQueryDefinition queryDef = queryManager.newRawCombinedQueryDefinition(queryHandle)
        DocumentPage documents = documentManager.search(queryDef, 1, transaction)

        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())

            Timestamp now = nowAsTimestamp() // TODO move Timestamp out of while loop?
            String mlFormat = """{
"tenant": "${jsonObj.tenant}",
"app": "${jsonObj.app}",
"version": "${jsonObj.version}",
"status": "${jsonObj.status}",
"branch": "${jsonObj.branch}",
"name": "${jsonObj.name}",
"sha1": "${jsonObj.sha1}",
"headSha1": "${jsonObj.headSha1}",
"revision": ${(long)jsonObj.revision + 1L},
"active": false,
"tip": true,
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${createNote(username, now, "deleted, txId: [${txId}]")}",
"changed": true,
"json" : "",
"testData": ${jsonObj.testData}
}"""

            String docId = "/${UniqueIdGenerator.uniqueId}.json"

            // write non-json portion of document
            documentManager.write(docId, new StringHandle(mlFormat).withFormat(Format.JSON), transaction)

            // write json portion of document (so NCube is not created in memory as a String)
            String xquery = """fn:doc("${document.uri}")/json"""
            ServerEvaluationCall call = client.newServerEval().transaction(transaction).xquery(xquery)
            DocumentPatchHandle result = call.eval(new InputStreamHandle().withFormat(Format.JSON))
            DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
            patchBuilder.replaceFragment('/json', result)
            documentManager.patch(docId, patchBuilder.build(), transaction)

            // update previous document tip = false
            DocumentPatchBuilder tipPb = documentManager.newPatchBuilder()
            tipPb.replaceFragment('/tip', new StringHandle('false'))
            documentManager.patch(document.uri, tipPb.build(), transaction)
        }

        return documents.size() > 0
    }

    static List<NCubeInfoDto> commitCubes(ApplicationID appId, Object[] cubeIds, String username, String requestUser, String txId, String notes)
    {
        return null
    }

    static NCubeInfoDto loadCubeRecordById(long cubeId, Map options)
    {
        String uri = "/${cubeId}.json"
        StringHandle handle = documentManager.read(uri, new StringHandle(), transaction)
        Map jsonObj = JsonReader.jsonToMaps(handle.get())
        NCubeInfoDto dto = new NCubeInfoDto()
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
        dto.notes = jsonObj.notes
        dto.changed = jsonObj.changed
        // TODO handle options - probably extract and combine with search logic
        if (options[SEARCH_INCLUDE_CUBE_DATA])
        {
            // TODO there might be a better way to do this just using search
            String xquery = """fn:doc("${uri}")/json"""
            ServerEvaluationCall call = client.newServerEval().transaction(transaction).xquery(xquery)
            InputStreamHandle result = call.eval(new InputStreamHandle())
            dto.bytes = ByteStreams.toByteArray(result.get())
            result.close()
        }
        dto.testData = jsonObj.testData
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
"search" : {
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
        Map jsonProperties = [tenant: appId.tenant, app: appId.app, version: appId.version, branch: appId.branch, ncube: SYS_INFO] as Map
        if (!ignoreStatus)
        {
            jsonProperties['status'] = appId.status
        }
        String queries = createAndQueries(jsonProperties)

        String query = """{
"search" : {
    "query": {
        "queries": [${queries}]
    },
    "options": {
        "extract-document-data": {
            "selected": "exclude",
            "extract-path" : ["/json"]
        }
    }  
}
}"""
        StringHandle queryHandle = new StringHandle(query).withFormat(Format.JSON)
        RawCombinedQueryDefinition queryDef = queryManager.newRawCombinedQueryDefinition(queryHandle)
        DocumentPage documents = documentManager.search(queryDef, 1, transaction)

        return documents.size() > 0
    }

    static int copyBranch(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    {
        if (doCubesExist(targetAppId, true, 'copyBranch'))
        {
            throw new IllegalArgumentException("Branch '${targetAppId.branch}' already exists, app: ${targetAppId}")
        }

        int headCount = srcAppId.head ? 0 : copyBranchInitialRevisions(srcAppId, targetAppId, username)

        return 0
    }

    static int copyBranchWithHistory(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    {
        if (doCubesExist(targetAppId, true, 'copyBranch'))
        {
            throw new IllegalStateException("Branch '${targetAppId.branch}' already exists, app: ${targetAppId}")
        }

        Map<String, Object> jsonProperties = [tenant: srcAppId.tenant, app: srcAppId.app, version: srcAppId.version, status: srcAppId.status, branch: srcAppId.branch] as Map
        String queries = createAndQueries(jsonProperties)
        String query = """{
"search" : {
    "query": {
        "queries": [${queries}]
    },
    "options": {
        "extract-document-data": {
            "selected": "exclude",
            "extract-path" : ["/json"]
        }
    }  
}
}"""

        StringHandle queryHandle = new StringHandle(query).withFormat(Format.JSON)
        RawCombinedQueryDefinition queryDef = queryManager.newRawCombinedQueryDefinition(queryHandle)
        DocumentPage documents = documentManager.search(queryDef, 1, transaction)

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
"headSha1": ${headSha1},
"revision": ${jsonObj.revision},
"active": ${jsonObj.active},
"tip": ${jsonObj.tip},
"createDate": "${DATE_TIME_FORMAT.format(now)}",
"createHid": "${username}",
"notes": "${"target ${targetAppId} full copied from ${srcAppId} - ${jsonObj.notes}"}",
"changed": ${changed},
"json" : "",
"testData": ${jsonObj.testData}
}"""

            String docId = "/${UniqueIdGenerator.uniqueId}.json"

            // write non-json portion of document
            documentManager.write(docId, new StringHandle(mlFormat).withFormat(Format.JSON), transaction)

            // write json portion of document (so NCube is not created in memory as a String)
            // TODO there might be a better way to do this just using search
            String xquery = """fn:doc("${document.uri}")/json"""
            ServerEvaluationCall call = client.newServerEval().transaction(transaction).xquery(xquery)
            DocumentPatchHandle jsonHandle = call.eval(new InputStreamHandle().withFormat(Format.JSON))
            DocumentPatchBuilder patchBuilder = documentManager.newPatchBuilder()
            patchBuilder.replaceFragment('/json', jsonHandle)
            jsonHandle.close()
            documentManager.patch(docId, patchBuilder.build(), transaction)
        }
        documents.size()
    }

    private static int copyBranchInitialRevisions(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    {
        Map<String, Object> jsonProperties = [tenant: srcAppId.tenant, app: srcAppId.app, version: srcAppId.version, status: srcAppId.status, branch: srcAppId.branch] as Map
        String queries = createAndQueries(jsonProperties)
        String query = """{
"search" : {
    "query": {
        "queries": [${queries}]
    },
    "options": {
        "extract-document-data": {
            "selected": "exclude",
            "extract-path" : ["/json"]
        }
    }  
}
}"""

        StringHandle queryHandle = new StringHandle(query).withFormat(Format.JSON)
        RawCombinedQueryDefinition queryDef = queryManager.newRawCombinedQueryDefinition(queryHandle)
        DocumentPage documents = documentManager.search(queryDef, 1, transaction)

        List<Map> docsToCopy = []
        Map<String, List<Map>> changed = [:]
        while (documents.hasNext())
        {
            DocumentRecord document = documents.next()
            StringHandle sHandle = document.getContent(new StringHandle())
            Map jsonObj = JsonReader.jsonToMaps(sHandle.get())
            if (jsonObj.tip && jsonObj.sha1 == jsonObj.headSha1) // unchanged
            {
                docsToCopy.add(jsonObj)
            }
            else
            {
                String name = jsonObj.name
                if (changed.containsKey(name))
                {
                    ((List)changed[name]).add(jsonObj)
                }
                else
                {
                    changed[name] = [jsonObj]
                }
            }
        }
        for (Map.Entry<String, List<Map>>entry : changed)
        {
            List<Map> allRevisions = entry.value
            Collections.sort(allRevisions, new Comparator<Map>() {
                int compare(Map o1, Map o2)
                {
                    return ((long) o2.revision) <=> (long) o1.revision
                }
            })
            for (Map revision : allRevisions)
            {
                if (revision.sha1 == revision.headSha1)
                {
                    docsToCopy.add(revision)
                }
                break
            }
        }




        return 0
    }

    private static String createAndQueries(Map<String, Object> jsonProperties)
    {
        StringBuilder sb = new StringBuilder()
        Iterator jsonEntries = jsonProperties.entrySet().iterator()
        while (jsonEntries.hasNext())
        {
            Map.Entry entry = jsonEntries.next()
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

    private static Transaction getTransaction()
    {
        NCubeManagerAdvice managerAdvice = (NCubeManagerAdvice)NCubeAppContext.getBean('ncubeManagerAdvice')
        return managerAdvice.transaction
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

}
