package com.appearnetworks.aiq.persistence;

import com.appearnetworks.aiq.integrationframework.integration.DocumentReference;
import com.appearnetworks.aiq.integrationframework.integration.IntegrationAdapterBase;
import com.appearnetworks.aiq.integrationframework.integration.UpdateException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class IntegrationAdapterImpl extends IntegrationAdapterBase {

    @Autowired
    private PersistenceService persistenceService;

    @Override
    public Collection<DocumentReference> findByUser(String userId) {
        return persistenceService.list();
    }

    @Override
    public Object retrieveDocument(String docType, String docId) {
        return persistenceService.retrieve(docId);
    }

    @Override
    public long insertDocument(String userId, String deviceId, DocumentReference docRef, ObjectNode doc) throws UpdateException {
        return persistenceService.insert(docRef, doc);
    }

    @Override
    public long updateDocument(String userId, String deviceId, DocumentReference docRef, ObjectNode doc) throws UpdateException {
        return persistenceService.update(docRef, doc);
    }

    @Override
    public void deleteDocument(String userId, String deviceId, DocumentReference docRef) throws UpdateException {
        persistenceService.delete(docRef);
    }

}
