package com.couchbase.cblite.testapp.tests;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.CBLView;
import com.couchbase.cblite.CBLViewMapBlock;
import com.couchbase.cblite.CBLViewMapEmitBlock;
import com.couchbase.cblite.router.CBLRouter;
import com.couchbase.cblite.router.CBLURLConnection;

import org.apache.commons.io.IOUtils;

public class Router extends CBLiteTestCase {

    public static final String TAG = "Router";

    public void testServer() {
        Map<String,Object> responseBody = new HashMap<String,Object>();
        responseBody.put("CBLite", "Welcome");
        responseBody.put("couchdb", "Welcome");
        responseBody.put("version", CBLRouter.getVersionString());
        send(server, "GET", "/", CBLStatus.OK, responseBody);

        Map<String,Object> session = new HashMap<String,Object>();
        Map<String,Object> userCtx = new HashMap<String,Object>();
        List<String> roles = new ArrayList<String>();
        roles.add("_admin");
        session.put("ok", true);
        userCtx.put("name", null);
        userCtx.put("roles", roles);
        session.put("userCtx", userCtx);
        send(server, "GET", "/_session", CBLStatus.OK, session);

        List<String> allDbs = new ArrayList<String>();
        allDbs.add("cblite-test");
        send(server, "GET", "/_all_dbs", CBLStatus.OK, allDbs);

        send(server, "GET", "/non-existant", CBLStatus.NOT_FOUND, null);
        send(server, "GET", "/BadName", CBLStatus.NOT_FOUND, null);
        send(server, "PUT", "/", CBLStatus.BAD_REQUEST, null);
        send(server, "POST", "/", CBLStatus.BAD_REQUEST, null);
    }

    public void testDatabase() {
        send(server, "PUT", "/database", CBLStatus.CREATED, null);

        Map<String,Object> dbInfo = (Map<String,Object>)send(server, "GET", "/database", CBLStatus.OK, null);
        Assert.assertEquals(0, dbInfo.get("doc_count"));
        Assert.assertEquals(0, dbInfo.get("update_seq"));
        Assert.assertTrue((Integer)dbInfo.get("disk_size") > 8000);

        send(server, "PUT", "/database", CBLStatus.PRECONDITION_FAILED, null);
        send(server, "PUT", "/database2", CBLStatus.CREATED, null);

        List<String> allDbs = new ArrayList<String>();
        allDbs.add("cblite-test");
        allDbs.add("database");
        allDbs.add("database2");
        send(server, "GET", "/_all_dbs", CBLStatus.OK, allDbs);
        dbInfo = (Map<String,Object>)send(server, "GET", "/database2", CBLStatus.OK, null);
        Assert.assertEquals("database2", dbInfo.get("db_name"));

        send(server, "DELETE", "/database2", CBLStatus.OK, null);
        allDbs.remove("database2");
        send(server, "GET", "/_all_dbs", CBLStatus.OK, allDbs);

        send(server, "PUT", "/database%2Fwith%2Fslashes", CBLStatus.CREATED, null);
        dbInfo = (Map<String,Object>)send(server, "GET", "/database%2Fwith%2Fslashes", CBLStatus.OK, null);
        Assert.assertEquals("database/with/slashes", dbInfo.get("db_name"));
    }

    public void testDocWithAttachment() throws IOException {

        String inlineTextString = "Inline text string created by cblite functional test";

        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        Map<String,Object> attachment = new HashMap<String,Object>();
        attachment.put("content-type", "text/plain");
        attachment.put("data", "SW5saW5lIHRleHQgc3RyaW5nIGNyZWF0ZWQgYnkgY2JsaXRlIGZ1bmN0aW9uYWwgdGVzdA==");

        Map<String,Object> attachments = new HashMap<String,Object>();
        attachments.put("inline.txt", attachment);

        Map<String,Object> docWithAttachment = new HashMap<String,Object>();
        docWithAttachment.put("_id", "docWithAttachment");
        docWithAttachment.put("text", inlineTextString);
        docWithAttachment.put("_attachments", attachments);

        Map<String,Object> result = (Map<String,Object>)sendBody(server, "PUT", "/db/docWithAttachment", docWithAttachment, CBLStatus.CREATED, null);

        result = (Map<String,Object>)send(server, "GET", "/db/docWithAttachment", CBLStatus.OK, null);
        Map<String,Object> attachmentsResult = (Map<String,Object>) result.get("_attachments");
        Map<String,Object> attachmentResult = (Map<String,Object>) attachmentsResult.get("inline.txt");

        // there should be either a content_type or content-type field.
        String contentTypeField = (String) attachmentResult.get("content_type");;
        Assert.assertTrue(attachmentResult.containsKey("content_type"));
        Assert.assertNotNull(contentTypeField);

        CBLURLConnection conn = sendRequest(server, "GET", "/db/docWithAttachment/inline.txt", null, null);
        String contentType = conn.getHeaderField("Content-Type");
        Assert.assertNotNull(contentType);
        Assert.assertTrue(contentType.contains("text/plain"));

        StringWriter writer = new StringWriter();
        IOUtils.copy(conn.getInputStream(), writer, "UTF-8");
        String responseString = writer.toString();
        Assert.assertTrue(responseString.contains(inlineTextString));


    }

    public void testDocs() {
        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        // PUT:
        Map<String,Object> doc1 = new HashMap<String,Object>();
        doc1.put("message", "hello");
        Map<String,Object> result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc1", doc1, CBLStatus.CREATED, null);
        String revID = (String)result.get("rev");
        Assert.assertTrue(revID.startsWith("1-"));

        // PUT to update:
        doc1.put("message", "goodbye");
        doc1.put("_rev", revID);
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc1", doc1, CBLStatus.CREATED, null);
        Log.v(TAG, String.format("PUT returned %s", result));
        revID = (String)result.get("rev");
        Assert.assertTrue(revID.startsWith("2-"));

        doc1.put("_id", "doc1");
        doc1.put("_rev", revID);
        result = (Map<String,Object>)send(server, "GET", "/db/doc1", CBLStatus.OK, doc1);

        // Add more docs:
        Map<String,Object> docX = new HashMap<String,Object>();
        docX.put("message", "hello");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc3", docX, CBLStatus.CREATED, null);
        String revID3 = (String)result.get("rev");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc2", docX, CBLStatus.CREATED, null);
        String revID2 = (String)result.get("rev");

        // _all_docs:
        result = (Map<String,Object>)send(server, "GET", "/db/_all_docs", CBLStatus.OK, null);
        Assert.assertEquals(3, result.get("total_rows"));
        Assert.assertEquals(0, result.get("offset"));

        Map<String,Object> value1 = new HashMap<String,Object>();
        value1.put("rev", revID);
        Map<String,Object> value2 = new HashMap<String,Object>();
        value2.put("rev", revID2);
        Map<String,Object> value3 = new HashMap<String,Object>();
        value3.put("rev", revID3);

        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("id", "doc1");
        row1.put("key", "doc1");
        row1.put("value", value1);
        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("id", "doc2");
        row2.put("key", "doc2");
        row2.put("value", value2);
        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("id", "doc3");
        row3.put("key", "doc3");
        row3.put("value", value3);

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        expectedRows.add(row1);
        expectedRows.add(row2);
        expectedRows.add(row3);

        List<Map<String,Object>> rows = (List<Map<String,Object>>)result.get("rows");
        Assert.assertEquals(expectedRows, rows);

        // DELETE:
        result = (Map<String,Object>)send(server, "DELETE", String.format("/db/doc1?rev=%s", revID), CBLStatus.OK, null);
        revID = (String)result.get("rev");
        Assert.assertTrue(revID.startsWith("3-"));

        send(server, "GET", "/db/doc1", CBLStatus.NOT_FOUND, null);

        // _changes:
        value1.put("rev", revID);
        List<Object> changes1 = new ArrayList<Object>();
        changes1.add(value1);
        List<Object> changes2 = new ArrayList<Object>();
        changes2.add(value2);
        List<Object> changes3 = new ArrayList<Object>();
        changes3.add(value3);

        Map<String,Object> result1 = new HashMap<String,Object>();
        result1.put("id", "doc1");
        result1.put("seq", 5);
        result1.put("deleted", true);
        result1.put("changes", changes1);
        Map<String,Object> result2 = new HashMap<String,Object>();
        result2.put("id", "doc2");
        result2.put("seq", 4);
        result2.put("changes", changes2);
        Map<String,Object> result3 = new HashMap<String,Object>();
        result3.put("id", "doc3");
        result3.put("seq", 3);
        result3.put("changes", changes3);

        List<Object> results = new ArrayList<Object>();
        results.add(result3);
        results.add(result2);
        results.add(result1);

        Map<String,Object> expectedChanges = new HashMap<String,Object>();
        expectedChanges.put("last_seq", 5);
        expectedChanges.put("results", results);

        send(server, "GET", "/db/_changes", CBLStatus.OK, expectedChanges);

        // _changes with ?since:
        results.remove(result3);
        results.remove(result2);
        expectedChanges.put("results", results);
        send(server, "GET", "/db/_changes?since=4", CBLStatus.OK, expectedChanges);

        results.remove(result1);
        expectedChanges.put("results", results);
        send(server, "GET", "/db/_changes?since=5", CBLStatus.OK, expectedChanges);

        // Put with _deleted to delete a doc:
        Log.d(TAG, "Put with _deleted to delete a doc");
        send(server, "GET", "/db/doc5", CBLStatus.NOT_FOUND, null);
        Map<String,Object> doc5 = new HashMap<String,Object>();
        doc5.put("message", "hello5");
        Map<String,Object> resultDoc5 = (Map<String,Object>)sendBody(server, "PUT", "/db/doc5", doc5, CBLStatus.CREATED, null);
        String revIdDoc5 = (String)resultDoc5.get("rev");
        Assert.assertTrue(revIdDoc5.startsWith("1-"));
        doc5.put("_deleted", true);
        doc5.put("_rev", revIdDoc5);
        doc5.put("_id", "doc5");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc5", doc5, CBLStatus.OK, null);
        send(server, "GET", "/db/doc5", CBLStatus.NOT_FOUND, null);
        Log.d(TAG, "Finished put with _deleted to delete a doc");
    }

    public void testLocalDocs() {
        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        // PUT a local doc:
        Map<String,Object> doc1 = new HashMap<String,Object>();
        doc1.put("message", "hello");
        Map<String,Object> result = (Map<String,Object>)sendBody(server, "PUT", "/db/_local/doc1", doc1, CBLStatus.CREATED, null);
        String revID = (String)result.get("rev");
        Assert.assertTrue(revID.startsWith("1-"));

        // GET it:
        doc1.put("_id", "_local/doc1");
        doc1.put("_rev", revID);
        result = (Map<String,Object>)send(server, "GET", "/db/_local/doc1", CBLStatus.OK, doc1);

        // Local doc should not appear in _changes feed:
        Map<String,Object> expectedChanges = new HashMap<String,Object>();
        expectedChanges.put("last_seq", 0);
        expectedChanges.put("results", new ArrayList<Object>());
        send(server, "GET", "/db/_changes", CBLStatus.OK, expectedChanges);
    }

    public void testAllDocs() {
        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        Map<String,Object> result;
        Map<String,Object> doc1 = new HashMap<String,Object>();
        doc1.put("message", "hello");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc1", doc1, CBLStatus.CREATED, null);
        String revID = (String)result.get("rev");
        Map<String,Object> doc3 = new HashMap<String,Object>();
        doc3.put("message", "bonjour");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc3", doc3, CBLStatus.CREATED, null);
        String revID3 = (String)result.get("rev");
        Map<String,Object> doc2 = new HashMap<String,Object>();
        doc2.put("message", "guten tag");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc2", doc2, CBLStatus.CREATED, null);
        String revID2 = (String)result.get("rev");

        // _all_docs:
        result = (Map<String,Object>)send(server, "GET", "/db/_all_docs", CBLStatus.OK, null);
        Assert.assertEquals(3, result.get("total_rows"));
        Assert.assertEquals(0, result.get("offset"));

        Map<String,Object> value1 = new HashMap<String,Object>();
        value1.put("rev", revID);
        Map<String,Object> value2 = new HashMap<String,Object>();
        value2.put("rev", revID2);
        Map<String,Object> value3 = new HashMap<String,Object>();
        value3.put("rev", revID3);

        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("id", "doc1");
        row1.put("key", "doc1");
        row1.put("value", value1);
        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("id", "doc2");
        row2.put("key", "doc2");
        row2.put("value", value2);
        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("id", "doc3");
        row3.put("key", "doc3");
        row3.put("value", value3);

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        expectedRows.add(row1);
        expectedRows.add(row2);
        expectedRows.add(row3);

        List<Map<String,Object>> rows = (List<Map<String,Object>>)result.get("rows");
        Assert.assertEquals(expectedRows, rows);

        // ?include_docs:
        result = (Map<String,Object>)send(server, "GET", "/db/_all_docs?include_docs=true", CBLStatus.OK, null);
        Assert.assertEquals(3, result.get("total_rows"));
        Assert.assertEquals(0, result.get("offset"));

        doc1.put("_id", "doc1");
        doc1.put("_rev", revID);
        row1.put("doc", doc1);

        doc2.put("_id", "doc2");
        doc2.put("_rev", revID2);
        row2.put("doc", doc2);

        doc3.put("_id", "doc3");
        doc3.put("_rev", revID3);
        row3.put("doc", doc3);

        List<Map<String,Object>> expectedRowsWithDocs = new ArrayList<Map<String,Object>>();
        expectedRowsWithDocs.add(row1);
        expectedRowsWithDocs.add(row2);
        expectedRowsWithDocs.add(row3);

        rows = (List<Map<String,Object>>)result.get("rows");
        Assert.assertEquals(expectedRowsWithDocs, rows);
    }

    public void testViews() {
        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        Map<String,Object> result;
        Map<String,Object> doc1 = new HashMap<String,Object>();
        doc1.put("message", "hello");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc1", doc1, CBLStatus.CREATED, null);
        String revID = (String)result.get("rev");
        Map<String,Object> doc3 = new HashMap<String,Object>();
        doc3.put("message", "bonjour");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc3", doc3, CBLStatus.CREATED, null);
        String revID3 = (String)result.get("rev");
        Map<String,Object> doc2 = new HashMap<String,Object>();
        doc2.put("message", "guten tag");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc2", doc2, CBLStatus.CREATED, null);
        String revID2 = (String)result.get("rev");

        CBLDatabase db = server.getDatabaseNamed("db");
        CBLView view = db.getViewNamed("design/view");
        view.setMapReduceBlocks(new CBLViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, CBLViewMapEmitBlock emitter) {
                emitter.emit(document.get("message"), null);
            }
        }, null, "1");

        // Build up our expected result

        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("id", "doc1");
        row1.put("key", "hello");
        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("id", "doc2");
        row2.put("key", "guten tag");
        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("id", "doc3");
        row3.put("key", "bonjour");

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        expectedRows.add(row3);
        expectedRows.add(row2);
        expectedRows.add(row1);

        Map<String,Object> expectedResult = new HashMap<String,Object>();
        expectedResult.put("offset", 0);
        expectedResult.put("total_rows", 3);
        expectedResult.put("rows", expectedRows);

        // Query the view and check the result:
        send(server, "GET", "/db/_design/design/_view/view", CBLStatus.OK, expectedResult);

        // Check the ETag:
        CBLURLConnection conn = sendRequest(server, "GET", "/db/_design/design/_view/view", null, null);
        String etag = conn.getHeaderField("Etag");
        Assert.assertEquals(String.format("\"%d\"", view.getLastSequenceIndexed()), etag);

        // Try a conditional GET:
        Map<String,String> headers = new HashMap<String,String>();
        headers.put("If-None-Match", etag);
        conn = sendRequest(server, "GET", "/db/_design/design/_view/view", headers, null);
        Assert.assertEquals(CBLStatus.NOT_MODIFIED, conn.getResponseCode());

        // Update the database:
        Map<String,Object> doc4 = new HashMap<String,Object>();
        doc4.put("message", "aloha");
        result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc4", doc4, CBLStatus.CREATED, null);

        // Try a conditional GET:
        conn = sendRequest(server, "GET", "/db/_design/design/_view/view", headers, null);
        Assert.assertEquals(CBLStatus.OK, conn.getResponseCode());
        result = (Map<String,Object>)parseJSONResponse(conn);
        Assert.assertEquals(4, result.get("total_rows"));
    }

    public void testPostBulkDocs() {
        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        Map<String,Object> bulk_doc1 = new HashMap<String,Object>();
        bulk_doc1.put("_id", "bulk_message1");
        bulk_doc1.put("baz", "hello");

        Map<String,Object> bulk_doc2 = new HashMap<String,Object>();
        bulk_doc2.put("_id", "bulk_message2");
        bulk_doc2.put("baz", "hi");

        List<Map<String,Object>> list = new ArrayList<Map<String,Object>>();
        list.add(bulk_doc1);
        list.add(bulk_doc2);

        Map<String,Object> bodyObj = new HashMap<String,Object>();
        bodyObj.put("docs", list);

        List<Map<String,Object>> bulk_result  =
                (ArrayList<Map<String,Object>>)sendBody(server, "POST", "/db/_bulk_docs", bodyObj, CBLStatus.CREATED, null);

        Assert.assertEquals(2, bulk_result.size());
        Assert.assertEquals(bulk_result.get(0).get("id"),  bulk_doc1.get("_id"));
        Assert.assertNotNull(bulk_result.get(0).get("rev"));
        Assert.assertEquals(bulk_result.get(1).get("id"),  bulk_doc2.get("_id"));
        Assert.assertNotNull(bulk_result.get(1).get("rev"));
    }

    public void testPostKeysView() {
    	send(server, "PUT", "/db", CBLStatus.CREATED, null);

    	Map<String,Object> result;

    	CBLDatabase db = server.getDatabaseNamed("db");
    	CBLView view = db.getViewNamed("design/view");
    	view.setMapReduceBlocks(new CBLViewMapBlock() {

    		@Override
    		public void map(Map<String, Object> document, CBLViewMapEmitBlock emitter) {
    			emitter.emit(document.get("message"), null);
    		}
    	}, null, "1");

    	Map<String,Object> key_doc1 = new HashMap<String,Object>();
    	key_doc1.put("parentId", "12345");
    	result = (Map<String,Object>)sendBody(server, "PUT", "/db/key_doc1", key_doc1, CBLStatus.CREATED, null);
    	view = db.getViewNamed("design/view");
    	view.setMapReduceBlocks(new CBLViewMapBlock() {
    		@Override
    		public void map(Map<String, Object> document, CBLViewMapEmitBlock emitter) {
    			if (document.get("parentId").equals("12345")) {
    				emitter.emit(document.get("parentId"), document);
    			}
    		}
    	}, null, "1");

    	List<Object> keys = new ArrayList<Object>();
    	keys.add("12345");
    	Map<String,Object> bodyObj = new HashMap<String,Object>();
    	bodyObj.put("keys", keys);
        CBLURLConnection conn = sendRequest(server, "POST", "/db/_design/design/_view/view", null, bodyObj);
        result = (Map<String, Object>) parseJSONResponse(conn);
    	Assert.assertEquals(1, result.get("total_rows"));
    }

    public void testRevsDiff() {

        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        Map<String,Object> doc = new HashMap<String,Object>();
        Map<String,Object> doc1r1 = (Map<String,Object>)sendBody(server, "PUT", "/db/11111", doc, CBLStatus.CREATED, null);
        String doc1r1ID = (String)doc1r1.get("rev");

        Map<String,Object> doc2r1 = (Map<String,Object>)sendBody(server, "PUT", "/db/22222", doc, CBLStatus.CREATED, null);
        String doc2r1ID = (String)doc2r1.get("rev");

        Map<String,Object> doc3r1 = (Map<String,Object>)sendBody(server, "PUT", "/db/33333", doc, CBLStatus.CREATED, null);
        String doc3r1ID = (String)doc3r1.get("rev");


        Map<String, Object> doc1v2 = new HashMap<String, Object>();
        doc1v2.put("_rev", doc1r1ID);
        Map<String,Object> doc1r2 = (Map<String,Object>)sendBody(server, "PUT", "/db/11111", doc1v2, CBLStatus.CREATED, null);
        String doc1r2ID = (String)doc1r2.get("rev");

        Map<String, Object> doc2v2 = new HashMap<String, Object>();
        doc2v2.put("_rev", doc2r1ID);
        sendBody(server, "PUT", "/db/22222", doc2v2, CBLStatus.CREATED, null);

        Map<String, Object> doc1v3 = new HashMap<String, Object>();
        doc1v3.put("_rev", doc1r2ID);
        Map<String,Object> doc1r3 = (Map<String,Object>)sendBody(server, "PUT", "/db/11111", doc1v3, CBLStatus.CREATED, null);
        String doc1r3ID = (String)doc1r1.get("rev");

        //now build up the request
        List<String> doc1Revs = new ArrayList<String>();
        doc1Revs.add(doc1r2ID);
        doc1Revs.add("3-foo");

        List<String> doc2Revs = new ArrayList<String>();
        doc2Revs.add(doc2r1ID);

        List<String> doc3Revs = new ArrayList<String>();
        doc3Revs.add("10-bar");

        List<String> doc9Revs = new ArrayList<String>();
        doc9Revs.add("6-six");

        Map<String, Object> revsDiffRequest = new HashMap<String, Object>();
        revsDiffRequest.put("11111", doc1Revs);
        revsDiffRequest.put("22222", doc2Revs);
        revsDiffRequest.put("33333", doc3Revs);
        revsDiffRequest.put("99999", doc9Revs);

        //now build up the expected response
        List<String> doc1missing = new ArrayList<String>();
        doc1missing.add("3-foo");

        List<String> doc3missing = new ArrayList<String>();
        doc3missing.add("10-bar");

        List<String> doc9missing = new ArrayList<String>();
        doc9missing.add("6-six");

        Map<String, Object> doc1missingMap = new HashMap<String, Object>();
        doc1missingMap.put("missing", doc1missing);

        Map<String, Object> doc3missingMap = new HashMap<String, Object>();
        doc3missingMap.put("missing", doc3missing);

        Map<String, Object> doc9missingMap = new HashMap<String, Object>();
        doc9missingMap.put("missing", doc9missing);

        Map<String, Object> revsDiffResponse = new HashMap<String, Object>();
        revsDiffResponse.put("11111", doc1missingMap);
        revsDiffResponse.put("33333", doc3missingMap);
        revsDiffResponse.put("99999", doc9missingMap);

        sendBody(server, "POST", "/db/_revs_diff", revsDiffRequest, CBLStatus.OK, revsDiffResponse);
    }

    public void testFacebookToken() {
        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        Map<String,Object> doc1 = new HashMap<String,Object>();
        doc1.put("email", "foo@bar.com");
        doc1.put("remote_url", getReplicationURL().toExternalForm() );
        doc1.put("access_token", "fake_access_token" );

        Map<String,Object> result = (Map<String,Object>)sendBody(server, "POST", "/_facebook_token", doc1, CBLStatus.OK, null);
        Log.v(TAG, String.format("result %s", result));

    }

    public void testPersonaAssertion() {
        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        Map<String,Object> doc1 = new HashMap<String,Object>();
        String sampleAssertion = "eyJhbGciOiJSUzI1NiJ9.eyJwdWJsaWMta2V5Ijp7ImFsZ29yaXRobSI6IkRTIiwieSI6ImNhNWJiYTYzZmI4MDQ2OGE0MjFjZjgxYTIzN2VlMDcwYTJlOTM4NTY0ODhiYTYzNTM0ZTU4NzJjZjllMGUwMDk0ZWQ2NDBlOGNhYmEwMjNkYjc5ODU3YjkxMzBlZGNmZGZiNmJiNTUwMWNjNTk3MTI1Y2NiMWQ1ZWQzOTVjZTMyNThlYjEwN2FjZTM1ODRiOWIwN2I4MWU5MDQ4NzhhYzBhMjFlOWZkYmRjYzNhNzNjOTg3MDAwYjk4YWUwMmZmMDQ4ODFiZDNiOTBmNzllYzVlNDU1YzliZjM3NzFkYjEzMTcxYjNkMTA2ZjM1ZDQyZmZmZjQ2ZWZiZDcwNjgyNWQiLCJwIjoiZmY2MDA0ODNkYjZhYmZjNWI0NWVhYjc4NTk0YjM1MzNkNTUwZDlmMWJmMmE5OTJhN2E4ZGFhNmRjMzRmODA0NWFkNGU2ZTBjNDI5ZDMzNGVlZWFhZWZkN2UyM2Q0ODEwYmUwMGU0Y2MxNDkyY2JhMzI1YmE4MWZmMmQ1YTViMzA1YThkMTdlYjNiZjRhMDZhMzQ5ZDM5MmUwMGQzMjk3NDRhNTE3OTM4MDM0NGU4MmExOGM0NzkzMzQzOGY4OTFlMjJhZWVmODEyZDY5YzhmNzVlMzI2Y2I3MGVhMDAwYzNmNzc2ZGZkYmQ2MDQ2MzhjMmVmNzE3ZmMyNmQwMmUxNyIsInEiOiJlMjFlMDRmOTExZDFlZDc5OTEwMDhlY2FhYjNiZjc3NTk4NDMwOWMzIiwiZyI6ImM1MmE0YTBmZjNiN2U2MWZkZjE4NjdjZTg0MTM4MzY5YTYxNTRmNGFmYTkyOTY2ZTNjODI3ZTI1Y2ZhNmNmNTA4YjkwZTVkZTQxOWUxMzM3ZTA3YTJlOWUyYTNjZDVkZWE3MDRkMTc1ZjhlYmY2YWYzOTdkNjllMTEwYjk2YWZiMTdjN2EwMzI1OTMyOWU0ODI5YjBkMDNiYmM3ODk2YjE1YjRhZGU1M2UxMzA4NThjYzM0ZDk2MjY5YWE4OTA0MWY0MDkxMzZjNzI0MmEzODg5NWM5ZDViY2NhZDRmMzg5YWYxZDdhNGJkMTM5OGJkMDcyZGZmYTg5NjIzMzM5N2EifSwicHJpbmNpcGFsIjp7ImVtYWlsIjoiamVuc0Btb29zZXlhcmQuY29tIn0sImlhdCI6MTM1ODI5NjIzNzU3NywiZXhwIjoxMzU4MzgyNjM3NTc3LCJpc3MiOiJsb2dpbi5wZXJzb25hLm9yZyJ9.RnDK118nqL2wzpLCVRzw1MI4IThgeWpul9jPl6ypyyxRMMTurlJbjFfs-BXoPaOem878G8-4D2eGWS6wd307k7xlPysevYPogfFWxK_eDHwkTq3Ts91qEDqrdV_JtgULC8c1LvX65E0TwW_GL_TM94g3CvqoQnGVxxoaMVye4ggvR7eOZjimWMzUuu4Lo9Z-VBHBj7XM0UMBie57CpGwH4_Wkv0V_LHZRRHKdnl9ISp_aGwfBObTcHG9v0P3BW9vRrCjihIn0SqOJQ9obl52rMf84GD4Lcy9NIktzfyka70xR9Sh7ALotW7rWywsTzMTu3t8AzMz2MJgGjvQmx49QA~eyJhbGciOiJEUzEyOCJ9.eyJleHAiOjEzNTgyOTY0Mzg0OTUsImF1ZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDk4NC8ifQ.4FV2TrUQffDya0MOxOQlzJQbDNvCPF2sfTIJN7KOLvvlSFPknuIo5g";
        doc1.put("assertion", sampleAssertion);

        Map<String,Object> result = (Map<String,Object>)sendBody(server, "POST", "/_persona_assertion", doc1, CBLStatus.OK, null);
        Log.v(TAG, String.format("result %s", result));
        String email = (String) result.get("email");
        Assert.assertEquals(email, "jens@mooseyard.com");



    }

    public void testPushReplicate() throws Exception {

        send(server, "PUT", "/db", CBLStatus.CREATED, null);


        Map<String, Object> replicateJsonMap = getPushReplicationParsedJson();


        Log.v(TAG, "map: " + replicateJsonMap);
        Map<String,Object> result = (Map<String,Object>)sendBody(server, "POST", "/_replicate", replicateJsonMap, CBLStatus.OK, null);
        Log.v(TAG, "result: " + result);
        Assert.assertNotNull(result.get("session_id"));

    }

    public void testPullReplicate() throws Exception {

        send(server, "PUT", "/db", CBLStatus.CREATED, null);

        Map<String, Object> replicateJsonMap = getPullReplicationParsedJson();

        Log.v(TAG, "map: " + replicateJsonMap);
        Map<String,Object> result = (Map<String,Object>)sendBody(server, "POST", "/_replicate", replicateJsonMap, CBLStatus.OK, null);
        Log.v(TAG, "result: " + result);
        Assert.assertNotNull(result.get("session_id"));



    }



}
