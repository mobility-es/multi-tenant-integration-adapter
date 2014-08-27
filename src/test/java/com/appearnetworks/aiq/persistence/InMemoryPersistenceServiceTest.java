package com.appearnetworks.aiq.persistence;

import com.appearnetworks.aiq.integrationframework.integration.DocumentReference;
import com.appearnetworks.aiq.integrationframework.integration.UpdateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;

import java.util.Collection;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static org.junit.Assert.*;

public class InMemoryPersistenceServiceTest {

    private static final String DOC_ID = "docId";
    private static final String DOC_TYPE = "docType";
    private static final String DATA = "FOO";
    private static final String NEW_DATA = "BAR";
    private static final String NOT_THERE = "foo";

    private ObjectMapper mapper = new ObjectMapper();

    private PersistenceService persistenceService;
    private ObjectNode document;

    @Before
    public void setup() {
        persistenceService = new InMemoryPersistenceService();
        document = mapper.createObjectNode();
        document.put("_id", DOC_ID);
        document.put("_type", DOC_TYPE);
        document.put("data", DATA);
    }

    @Test
    public void empty() {
        assertEquals(0, persistenceService.list().size());
        assertNull(persistenceService.retrieve(DOC_ID));
    }

    @Test
    public void insert() throws UpdateException {
        long revision = persistenceService.insert(new DocumentReference(DOC_ID, DOC_TYPE, 0), document);
        assertTrue(revision > 0);

        Collection<DocumentReference> documents = persistenceService.list();
        assertEquals(1, documents.size());
        DocumentReference documentReference = documents.iterator().next();
        assertEquals(new DocumentReference(DOC_ID, DOC_TYPE, revision), documentReference);
        document.put("_rev", revision);
        assertJsonEquals(document, persistenceService.retrieve(DOC_ID));

        assertNull(persistenceService.retrieve(NOT_THERE));
    }

    @Test
    public void insertConflict() throws UpdateException {
        persistenceService.insert(new DocumentReference(DOC_ID, DOC_TYPE, 0), document);

        try {
            persistenceService.insert(new DocumentReference(DOC_ID, DOC_TYPE, 0), document);
            fail("should throw UpdateException(CONFLICT)");
        } catch (UpdateException e) {
            assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        }
    }

    @Test
    public void update() throws UpdateException {
        long revision = persistenceService.insert(new DocumentReference(DOC_ID, DOC_TYPE, 0), document);

        document.put("data", NEW_DATA);

        long newRevision = persistenceService.update(new DocumentReference(DOC_ID, DOC_TYPE, revision), document);
        assertTrue(newRevision > revision);

        Collection<DocumentReference> documents = persistenceService.list();
        assertEquals(1, documents.size());
        DocumentReference documentReference = documents.iterator().next();
        assertEquals(new DocumentReference(DOC_ID, DOC_TYPE, newRevision), documentReference);
        document.put("_rev", newRevision);
        assertJsonEquals(document, persistenceService.retrieve(DOC_ID));
    }

    @Test
    public void updateConflict() throws UpdateException {
        long revision = persistenceService.insert(new DocumentReference(DOC_ID, DOC_TYPE, 0), document);

        document.put("data", NEW_DATA);

        try {
            persistenceService.update(new DocumentReference(DOC_ID, DOC_TYPE, revision-1), document);
            fail("should throw UpdateException(PRECONDITION_FAILED)");
        } catch (UpdateException e) {
            assertEquals(HttpStatus.PRECONDITION_FAILED, e.getStatusCode());
        }
    }

    @Test
    public void delete() throws UpdateException {
        long revision = persistenceService.insert(new DocumentReference(DOC_ID, DOC_TYPE, 0), document);

        persistenceService.delete(new DocumentReference(DOC_ID, DOC_TYPE, revision));

        Collection<DocumentReference> documents = persistenceService.list();
        assertEquals(0, documents.size());
        assertNull(persistenceService.retrieve(DOC_ID));
    }

    @Test
    public void deleteConflict() throws UpdateException {
        long revision = persistenceService.insert(new DocumentReference(DOC_ID, DOC_TYPE, 0), document);

        persistenceService.delete(new DocumentReference(DOC_ID, DOC_TYPE, revision));

        try {
            persistenceService.delete(new DocumentReference(DOC_ID, DOC_TYPE, revision-1));
            fail("should throw UpdateException(PRECONDITION_FAILED)");
        } catch (UpdateException e) {
            assertEquals(HttpStatus.PRECONDITION_FAILED, e.getStatusCode());
        }
    }
}
