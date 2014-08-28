package com.appearnetworks.aiq.persistence;

import com.appearnetworks.aiq.integrationframework.integration.DocumentReference;
import com.appearnetworks.aiq.integrationframework.integration.UpdateException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryPersistenceService implements PersistenceService {

    private static final String REV = "_rev";

    private final ConcurrentMap<String, Document> documents = new ConcurrentHashMap<>();

    @Override
    public Collection<DocumentReference> list() {
        ArrayList<DocumentReference> documentReferences = new ArrayList<>(documents.size());
        for (Document document : documents.values()) {
            documentReferences.add(new DocumentReference(document));
        }
        return documentReferences;
    }

    @Override
    public ObjectNode retrieve(String docId) {
        Document doc = documents.get(docId);
        return (doc != null)
            ? doc.getBody()
            : null;
    }

    @Override
    public long insert(DocumentReference docRef, ObjectNode body) throws UpdateException {
        long initialRevision = 1;

        body.put(REV, initialRevision);

        Document existingDocument = documents.putIfAbsent(
            docRef._id,
            new Document(docRef._id, docRef._type, initialRevision, body));

        if (existingDocument == null) {
            return initialRevision;
        } else {
            throw new UpdateException(HttpStatus.CONFLICT);
        }
    }

    @Override
    public long update(DocumentReference docRef, ObjectNode body) throws UpdateException {
        long updatedRevision = docRef._rev + 1;

        body.put(REV, updatedRevision);

        boolean wasReplaced = documents.replace(
            docRef._id,
            new Document(docRef._id, docRef._type, docRef._rev, null),
            new Document(docRef._id, docRef._type, updatedRevision, body));

        if (wasReplaced)
            return updatedRevision;
        else
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
    }

    @Override
    public void delete(DocumentReference docRef) throws UpdateException {
        boolean wasRemoved = documents.remove(
            docRef._id,
            new Document(docRef._id, docRef._type, docRef._rev, null));

        if (wasRemoved)
            return;
        else
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
    }
}
