package com.appearnetworks.aiq.multitenant.integration;

import com.appearnetworks.aiq.multitenant.ProtocolConstants;
import com.appearnetworks.aiq.multitenant.persistence.PersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Implement the AIQ 8 integration protocol.
 */
@Controller
@RequestMapping(value = "/aiq/integration/{orgId}/{solutionId}")
public class IntegrationProtocol {

    private static Logger LOGGER = Logger.getLogger(IntegrationProtocol.class.getName());

    private static final String ORG_ID = "orgId";
    private static final String SOLUTION_ID = "solutionId";
    private static final String USER_ID = "userId";
    private static final String DOC_TYPE = "docType";
    private static final String DOC_ID = "docId";
    private static final String NAME = "name";

    @Autowired
    private PersistenceService persistenceService;

    private ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void configureLogging() throws IOException {
        InputStream loggingConfiguration = getClass().getResourceAsStream("/logging.properties");
        LogManager.getLogManager().readConfiguration(loggingConfiguration);
        loggingConfiguration.close();
    }

    @RequestMapping(value = "/datasync",
                    method = RequestMethod.GET,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    public
    @ResponseBody
    ListDocumentsResponse listDocuments(@PathVariable(ORG_ID) String orgId,
                                        @PathVariable(SOLUTION_ID) String solutionId,
                                        @RequestParam(value = USER_ID, required = false) String userId) {
        LOGGER.fine("Listing documents in organization " + orgId);
        return new ListDocumentsResponse(persistenceService.list(orgId, solutionId));
    }

    @RequestMapping(value = "/datasync/{docType}/{docId:.*}",
                    method = RequestMethod.GET,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> getDocument(@PathVariable(ORG_ID) String orgId,
                                                  @PathVariable(SOLUTION_ID) String solutionId,
                                                  @PathVariable(DOC_TYPE) String docType,
                                                  @PathVariable(DOC_ID) String docId) {
        LOGGER.fine("Getting document " + docId + " in organization " + orgId);
        Object document = persistenceService.retrieve(orgId, solutionId, docId);
        if (document == null) {
            LOGGER.warning("Document " + docId + " not found in organization " + orgId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            ObjectNode json;
            json = (ObjectNode)document;

            long revision = json.get("_rev").asLong();
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setETag(makeETag(revision));
            return new ResponseEntity<>(json, responseHeaders, HttpStatus.OK);
        }
    }

    @RequestMapping(value = "/datasync/{docType}/{docId}/{name:.*}", method = RequestMethod.GET)
    public void getAttachment(@PathVariable(ORG_ID) String orgId,
                              @PathVariable(SOLUTION_ID) String solutionId,
                              @PathVariable(DOC_TYPE) String docType,
                              @PathVariable(DOC_ID) String docId,
                              @PathVariable(NAME) String name,
                              HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    @RequestMapping(value = "/datasync/{docType}/{docId:.*}",
                    method = RequestMethod.PUT,
                    consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> insertDocument(@RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                            @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                            @PathVariable(ORG_ID) String orgId,
                                            @PathVariable(SOLUTION_ID) String solutionId,
                                            @PathVariable(DOC_TYPE) String docType,
                                            @PathVariable(DOC_ID) String docId,
                                            @RequestBody ObjectNode doc) {
        LOGGER.info("Inserting document " + docId + " in organization " + orgId);
        try {
            HttpHeaders responseHeaders = new HttpHeaders();
            long revision = persistenceService.insert(
                    orgId,
                    solutionId,
                    new DocumentReference(docId, docType, 0),
                    doc);
            responseHeaders.setETag(makeETag(revision));
            return new ResponseEntity<>(responseHeaders, HttpStatus.CREATED);

        } catch (UpdateException e) {
            LOGGER.log(Level.WARNING, "Could not insert document " + docId + " in organization " + orgId, e);
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    @RequestMapping(value = "/datasync/{docType}/{docId:.*}",
                    method = RequestMethod.PUT,
                    headers = {ProtocolConstants.IF_MATCH},
                    consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateDocument(@RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                            @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                            @RequestHeader(ProtocolConstants.IF_MATCH) String ifMatch,
                                            @PathVariable(ORG_ID) String orgId,
                                            @PathVariable(SOLUTION_ID) String solutionId,
                                            @PathVariable(DOC_TYPE) String docType,
                                            @PathVariable(DOC_ID) String docId,
                                            @RequestBody ObjectNode doc) {
        LOGGER.info("Updating document " + docId + " in organization " + orgId);
        try {
            long currentRevision = parseRevision(ifMatch);
            HttpHeaders responseHeaders = new HttpHeaders();
            long revision = persistenceService.update(
                    orgId,
                    solutionId,
                    new DocumentReference(docId, docType, currentRevision),
                    doc);
            responseHeaders.setETag(makeETag(revision));
            return new ResponseEntity<>(responseHeaders, HttpStatus.NO_CONTENT);
        } catch (UpdateException e) {
            LOGGER.log(Level.WARNING, "Could not update document " + docId + " in organization " + orgId, e);
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    @RequestMapping(value = "/datasync/{docType}/{docId:.*}",
                    method = RequestMethod.DELETE,
                    headers = {ProtocolConstants.IF_MATCH})
    public ResponseEntity<?> deleteDocument(@RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                            @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                            @RequestHeader(ProtocolConstants.IF_MATCH) String ifMatch,
                                            @PathVariable(ORG_ID) String orgId,
                                            @PathVariable(SOLUTION_ID) String solutionId,
                                            @PathVariable(DOC_TYPE) String docType,
                                            @PathVariable(DOC_ID) String docId) {
        LOGGER.info("Deleting document " + docId + " in organization " + orgId);
        try {
            long currentRevision = parseRevision(ifMatch);
            persistenceService.delete(
                    orgId,
                    solutionId,
                    new DocumentReference(docId, docType, currentRevision));
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (UpdateException e) {
            LOGGER.log(Level.WARNING, "Could not delete document " + docId + " in organization " + orgId, e);
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    @RequestMapping(value = "/datasync/{docType}/{docId}/{name:.*}", method = RequestMethod.PUT)
    public ResponseEntity<Object> insertAttachment(@RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                                   @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                                   @RequestHeader(ProtocolConstants.CONTENT_TYPE) String contentType,
                                                   @RequestHeader(value = ProtocolConstants.CONTENT_LENGTH,
                                                                  required = false,
                                                                  defaultValue = "-1") long contentLength,
                                                   @PathVariable(ORG_ID) String orgId,
                                                   @PathVariable(SOLUTION_ID) String solutionId,
                                                   @PathVariable(DOC_TYPE) String docType,
                                                   @PathVariable(DOC_ID) String docId,
                                                   @PathVariable(NAME) String name,
                                                   InputStream body) throws IOException {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(value = "/datasync/{docType}/{docId}/{name:.*}",
                    method = RequestMethod.PUT,
                    headers = {ProtocolConstants.IF_MATCH})
    public ResponseEntity<Object> updateAttachment(@RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                                   @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                                   @RequestHeader(ProtocolConstants.IF_MATCH) String ifMatch,
                                                   @RequestHeader(ProtocolConstants.CONTENT_TYPE) String contentType,
                                                   @RequestHeader(value = ProtocolConstants.CONTENT_LENGTH,
                                                                  required = false,
                                                                  defaultValue = "-1") long contentLength,
                                                   @PathVariable(ORG_ID) String orgId,
                                                   @PathVariable(SOLUTION_ID) String solutionId,
                                                   @PathVariable(DOC_TYPE) String docType,
                                                   @PathVariable(DOC_ID) String docId,
                                                   @PathVariable(NAME) String name,
                                                   InputStream body) throws IOException {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(value = "/datasync/{docType}/{docId}/{name:.*}",
                    method = RequestMethod.DELETE,
                    headers = {ProtocolConstants.IF_MATCH})
    public ResponseEntity<Object> deleteAttachment(@RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                                   @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                                   @RequestHeader(ProtocolConstants.IF_MATCH) String ifMatch,
                                                   @PathVariable(ORG_ID) String orgId,
                                                   @PathVariable(SOLUTION_ID) String solutionId,
                                                   @PathVariable(DOC_TYPE) String docType,
                                                   @PathVariable(DOC_ID) String docId,
                                                   @PathVariable(NAME) String name) {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public ResponseEntity<Object> logout(@PathVariable(ORG_ID) String orgId,
                                         @PathVariable(SOLUTION_ID) String solutionId,
                                         @RequestBody LogoutRequest request) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(value = "/clientsessions", method = RequestMethod.POST, headers = {ProtocolConstants.SLUG}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createClientSession(@RequestHeader(ProtocolConstants.SLUG) String sessionId,
                                                 @RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                                 @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                                 @RequestBody ObjectNode doc) {
        return new ResponseEntity<Object>(HttpStatus.CREATED);
    }

    @RequestMapping(value = "/clientsessions/{sessionId:.*}", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateClientSession(@PathVariable("sessionId") String sessionId,
                                                 @RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                                 @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                                 @RequestBody ObjectNode doc) {
        return new ResponseEntity<Object>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(value = "/clientsessions/{sessionId:.*}", method = RequestMethod.DELETE)
    public ResponseEntity<?> removeClientSession(@PathVariable("sessionId") String sessionId,
                                                 @RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                                 @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId) {
        return new ResponseEntity<Object>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(value = "/heartbeat", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> heartbeat() {
        return new ResponseEntity<>(mapper.createObjectNode(), HttpStatus.OK);
    }

    @RequestMapping(value = "/comessage/{destination}",
                    method = RequestMethod.POST,
                    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> coMessage(@PathVariable(ORG_ID) String orgId,
                                                @PathVariable(SOLUTION_ID) String solutionId,
                                                @PathVariable("destination") String destination,
                                                @RequestHeader(ProtocolConstants.X_AIQ_USER_ID) String userId,
                                                @RequestHeader(ProtocolConstants.X_AIQ_DEVICE_ID) String deviceId,
                                                @RequestHeader(ProtocolConstants.X_AIQ_MESSAGE_ID) String messageId,
                                                @RequestHeader(ProtocolConstants.X_AIQ_CREATED) long created,
                                                HttpServletRequest request) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private String makeETag(long rev) {
        return '\"' + String.valueOf(rev) + '\"';
    }

    private long parseRevision(String etag) {
        return Long.parseLong(etag.substring(1, etag.length() - 1));
    }
}
