package com.appearnetworks.aiq.multitenant.persistence;

import com.appearnetworks.aiq.multitenant.integration.BusinessDocument;
import com.appearnetworks.aiq.multitenant.integration.DocumentReference;
import com.appearnetworks.aiq.multitenant.integration.UpdateException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryPersistenceService implements PersistenceService {

    private static final String REV = "_rev";

    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, BusinessDocument>>> organizations = new ConcurrentHashMap<>();

    @Override
    public Collection<DocumentReference> list(String orgId, String solutionId) {
        ConcurrentMap<String, ConcurrentMap<String, BusinessDocument>> solutions = organizations.get(orgId);
        if (solutions == null) {
            return Collections.emptyList();
        }

        ConcurrentMap<String, BusinessDocument> documents = solutions.get(solutionId);
        if (documents == null) {
            return Collections.emptyList();
        }

        return documents.values().stream().map(DocumentReference::new).collect(Collectors.toList());
    }

    @Override
    public ObjectNode retrieve(String orgId, String solutionId, String docId) {
        ConcurrentMap<String, ConcurrentMap<String, BusinessDocument>> solutions = organizations.get(orgId);
        if (solutions == null) {
            return null;
        }

        ConcurrentMap<String, BusinessDocument> documents = solutions.get(solutionId);
        if (documents == null) {
            return null;
        }

        BusinessDocument doc = documents.get(docId);
        return (doc != null) ? doc.getBody() : null;
    }

    @Override
    public long insert(String orgId, String solutionId, DocumentReference docRef, ObjectNode body) throws UpdateException {
        ConcurrentMap<String, ConcurrentMap<String, BusinessDocument>> solutions = organizations.get(orgId);
        boolean hasOrganization = (solutions != null);
        if (! hasOrganization) {
            solutions = new ConcurrentHashMap<>();
        }

        ConcurrentMap<String, BusinessDocument> documents = solutions.get(solutionId);
        boolean hasSolution = (documents != null);
        if (! hasSolution) {
            documents = new ConcurrentHashMap<>();
        }

        long initialRevision = 1;

        body.put(REV, initialRevision);

        if (documents.putIfAbsent(docRef._id, new BusinessDocument(docRef._id, docRef._type, initialRevision, body)) != null) {
            throw new UpdateException(HttpStatus.CONFLICT);
        } else {
            if (! hasSolution) {
                solutions.put(solutionId, documents);
            }
            if (! hasOrganization) {
                organizations.put(orgId, solutions);
            }
        }

        return initialRevision;
    }

    @Override
    public long update(String orgId, String solutionId, DocumentReference docRef, ObjectNode body) throws UpdateException {
        ConcurrentMap<String, ConcurrentMap<String, BusinessDocument>> solutions = organizations.get(orgId);
        if (solutions == null) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        ConcurrentMap<String, BusinessDocument> documents = solutions.get(solutionId);
        if (documents == null) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        long updatedRevision = docRef._rev + 1;

        body.put(REV, updatedRevision);

        if (! documents.replace(docRef._id,
                                new BusinessDocument(docRef._id, docRef._type, docRef._rev, null),
                                  new BusinessDocument(docRef._id, docRef._type, updatedRevision, body))) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        return updatedRevision;
    }

    @Override
    public void delete(String orgId, String solutionId, DocumentReference docRef) throws UpdateException {
        ConcurrentMap<String, ConcurrentMap<String, BusinessDocument>> solutions = organizations.get(orgId);
        if (solutions == null) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        ConcurrentMap<String, BusinessDocument> documents = solutions.get(solutionId);
        if (documents == null) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        if (! documents.remove(docRef._id, new BusinessDocument(docRef._id, docRef._type, docRef._rev, null))) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        if (documents.size() == 0) {
            solutions.remove(solutionId);
        }

        if (solutions.size() == 0) {
            organizations.remove(orgId);
        }
    }
}
