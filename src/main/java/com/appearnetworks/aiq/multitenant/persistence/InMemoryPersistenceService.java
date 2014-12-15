package com.appearnetworks.aiq.multitenant.persistence;

import com.appearnetworks.aiq.multitenant.integration.BusinessDocument;
import com.appearnetworks.aiq.multitenant.integration.DocumentReference;
import com.appearnetworks.aiq.multitenant.integration.UpdateException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class InMemoryPersistenceService implements PersistenceService {

    private static final String REV = "_rev";

    private final Map<String, Map<String, Map<String, BusinessDocument>>> organizations = new HashMap<>();

    @Override
    public synchronized Collection<DocumentReference> list(String orgId, String solutionId) {
        Map<String, Map<String, BusinessDocument>> solutions = organizations.get(orgId);
        if (solutions == null) {
            return Collections.emptyList();
        }

        Map<String, BusinessDocument> documents = solutions.get(solutionId);
        if (documents == null) {
            return Collections.emptyList();
        }

        return documents.values().stream().map(DocumentReference::new).collect(Collectors.toList());
    }

    @Override
    public synchronized ObjectNode retrieve(String orgId, String solutionId, String docId) {
        Map<String, Map<String, BusinessDocument>> solutions = organizations.get(orgId);
        if (solutions == null) {
            return null;
        }

        Map<String, BusinessDocument> documents = solutions.get(solutionId);
        if (documents == null) {
            return null;
        }

        BusinessDocument doc = documents.get(docId);
        return (doc != null) ? doc.getBody() : null;
    }

    @Override
    public synchronized long insert(String orgId, String solutionId, DocumentReference docRef, ObjectNode body) throws UpdateException {
        Map<String, Map<String, BusinessDocument>> solutions = organizations.get(orgId);
        boolean hasOrganization = (solutions != null);
        if (! hasOrganization) {
            solutions = new HashMap<>();
        }

        Map<String, BusinessDocument> documents = solutions.get(solutionId);
        boolean hasSolution = (documents != null);
        if (! hasSolution) {
            documents = new HashMap<>();
        }

        long initialRevision = 1;

        body.put(REV, initialRevision);

        if (documents.putIfAbsent(docRef._id, new BusinessDocument(docRef._id, docRef._type, initialRevision, body)) != null) {
            throw new UpdateException(HttpStatus.CONFLICT);
        }
        
        if (! hasSolution) {
            solutions.put(solutionId, documents);
        }
        if (! hasOrganization) {
            organizations.put(orgId, solutions);
        }

        return initialRevision;
    }

    @Override
    public synchronized long update(String orgId, String solutionId, DocumentReference docRef, ObjectNode body) throws UpdateException {
        Map<String, Map<String, BusinessDocument>> solutions = organizations.get(orgId);
        if (solutions == null) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        Map<String, BusinessDocument> documents = solutions.get(solutionId);
        if (documents == null) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        long updatedRevision = docRef._rev + 1;

        body.put(REV, updatedRevision);

        if (! documents.replace(
                docRef._id,
                new BusinessDocument(docRef._id, docRef._type, docRef._rev, null),
                new BusinessDocument(docRef._id, docRef._type, updatedRevision, body))) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        return updatedRevision;
    }

    @Override
    public synchronized void delete(String orgId, String solutionId, DocumentReference docRef) throws UpdateException {
        Map<String, Map<String, BusinessDocument>> solutions = organizations.get(orgId);
        if (solutions == null) {
            throw new UpdateException(HttpStatus.PRECONDITION_FAILED);
        }

        Map<String, BusinessDocument> documents = solutions.get(solutionId);
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
