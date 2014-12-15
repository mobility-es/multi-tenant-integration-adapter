package com.appearnetworks.aiq.multitenant.persistence;

import com.appearnetworks.aiq.multitenant.integration.DocumentReference;
import com.appearnetworks.aiq.multitenant.integration.UpdateException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;

public interface PersistenceService {

    Collection<DocumentReference> list(String orgId, String solutionId);

    ObjectNode retrieve(String orgId, String solutionId, String docId);

    long insert(String orgId, String solutionId, DocumentReference docRef, ObjectNode doc) throws UpdateException;

    long update(String orgId, String solutionId, DocumentReference docRef, ObjectNode doc) throws UpdateException;

    void delete(String orgId, String solutionId, DocumentReference docRef) throws UpdateException;

}
