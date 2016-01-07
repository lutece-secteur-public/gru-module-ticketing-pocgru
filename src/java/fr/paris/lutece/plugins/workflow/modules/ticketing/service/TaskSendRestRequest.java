/*
 * Copyright (c) 2002-2015, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.workflow.modules.ticketing.service;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.apache.xerces.impl.dv.util.Base64;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.Field;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.ticketing.business.Ticket;
import fr.paris.lutece.plugins.ticketing.business.TicketHome;
import fr.paris.lutece.plugins.workflowcore.business.resource.ResourceHistory;
import fr.paris.lutece.plugins.workflowcore.service.resource.IResourceHistoryService;
import fr.paris.lutece.plugins.workflowcore.service.task.SimpleTask;
import fr.paris.lutece.portal.business.file.File;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;


/**
 * This class represents a task to send a ticket in JSON format to a REST
 * endpoint
 *
 */
public class TaskSendRestRequest extends SimpleTask
{
    // Constants for JSON message
    private static final String EMPTY_STRING = "";
    private static final String KEY_TICKET = "ticket";
    private static final String KEY_TICKET_ID = "id";
    private static final String KEY_TICKET_REFERENCE = "ref";
    private static final String KEY_TICKET_TYPE = "type";
    private static final String KEY_TICKET_DOMAIN = "domain";
    private static final String KEY_TICKET_CATEGORY = "category";
    private static final String KEY_TICKET_CONTACT_MODE = "contact_mode";
    private static final String KEY_TICKET_STATUS = "status";
    private static final String KEY_TICKET_COMMENT = "comment";
    private static final String KEY_USER = "user";
    private static final String KEY_USER_ID = "guid";
    private static final String KEY_USER_TITLE = "title";
    private static final String KEY_USER_FIRST_NAME = "first_name";
    private static final String KEY_USER_LAST_NAME = "last_name";
    private static final String KEY_USER_EMAIL = "email";
    private static final String KEY_USER_FIXED_PHONE_NUMBER = "fixed_phone_number";
    private static final String KEY_USER_MOBILE_PHONE_NUMBER = "mobile_phone_number";
    private static final String KEY_EXTRA_FIELDS = "extra_fields";
    private static final String KEY_EXTRA_FIELDS_FIELD = "field";
    private static final String KEY_EXTRA_FIELDS_VALUE = "value";
    private static final String KEY_EXTRA_FIELDS_METADATA = "metadata";
    private static final String KEY_EXTRA_FIELDS_METADATA_NAME = "name";
    private static final String KEY_EXTRA_FIELDS_METADATA_VALUE = "value";
    private static final String KEY_EXTRA_FIELDS_METADATA_MIMETYPE = "mimetype";
    private static final String KEY_EXTRA_FIELDS_METADATA_FILENAME = "filename";

    // Messages
    private static final String MESSAGE_SEND_TICKET = "module.workflow.ticketing.task_send_rest_request.labelSendTicket";
    private static final String MESSAGE_STATUS_SENT_OK = "module.workflow.ticketing.task_send_rest_request.labelStatusSentOk";
    private static final String MESSAGE_STATUS_SENT_KO = "module.workflow.ticketing.task_send_rest_request.labelStatusSentKo";

    // Properties
    private static final String PROPERTY_REST_COMPANIES = "workflow-ticketing.rest.companies";
    private static final String PROPERTY_REST_ENDPOINT_COMPANY = "workflow-ticketing.rest.endpoint.company.";
    private static final String PROPERTY_REST_USERS_COMPANY = "workflow-ticketing.rest.users.company.";

    // Errors
    private static final String ERROR_SENDING_TICKET = "Problem when sending the ticket {0} : {1}";
    private static final String ERROR_HTTP = "HTTP ";

    // Other constants
    private static final String SEPARATOR = ",";
    private static final int STATUS_SENT_KO = 0;
    private static final int STATUS_SENT_OK = 1;

    // Services
    @Inject
    private IResourceHistoryService _resourceHistoryService;
    private Client _client;
    private boolean _bInit;

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(  )
    {
        if ( !_bInit )
        {
            _client = Client.create(  );
            _bInit = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processTask( int nIdResourceHistory, HttpServletRequest request, Locale locale )
    {
        init(  );

        ResourceHistory resourceHistory = _resourceHistoryService.findByPrimaryKey( nIdResourceHistory );

        if ( ( resourceHistory != null ) && Ticket.TICKET_RESOURCE_TYPE.equals( resourceHistory.getResourceType(  ) ) )
        {
            // We get the appointment to send
            Ticket ticket = TicketHome.findByPrimaryKey( resourceHistory.getIdResource(  ) );

            if ( ticket != null )
            {
                String strGuid = ticket.getGuid(  );
                String company = getCompany( strGuid );

                if ( company != null )
                {
                    String strRestEndpoint = getEndpoint( company );
                    WebResource webResource = _client.resource( strRestEndpoint );
                    JSONObject json = new JSONObject(  );
                    addTicketJson( json, ticket );

                    ClientResponse response = null;
                    int nSendStatusCode = STATUS_SENT_OK;
                    String strSendStatusText = I18nService.getLocalizedString( MESSAGE_STATUS_SENT_OK, Locale.FRANCE ) +
                        company;

                    try
                    {
                        response = webResource.type( MediaType.APPLICATION_JSON ).accept( MediaType.APPLICATION_JSON )
                                              .put( ClientResponse.class, json.toString(  ) );
                    }
                    catch ( final Throwable t )
                    {
                        AppLogService.error( buildErrorMessage( ticket, t.getMessage(  ) ) );
                        nSendStatusCode = STATUS_SENT_KO;
                        strSendStatusText = I18nService.getLocalizedString( MESSAGE_STATUS_SENT_KO, Locale.FRANCE );
                    }

                    if ( ( response.getStatus(  ) != 200 ) && ( response.getStatus(  ) != 201 ) )
                    {
                        AppLogService.error( buildErrorMessage( ticket, ERROR_HTTP + response.getStatus(  ) ) );
                        nSendStatusCode = STATUS_SENT_KO;
                        strSendStatusText = I18nService.getLocalizedString( MESSAGE_STATUS_SENT_KO, Locale.FRANCE );
                    }

                    ResponseContent responseContent = new ResponseContent( response.getEntity( String.class ) );

                    if ( !ResponseContent.STATUS_CORRECT.equals( responseContent.getStatus(  ) ) )
                    {
                        AppLogService.error( buildErrorMessage( ticket, responseContent.getMessage(  ) ) );
                        nSendStatusCode = STATUS_SENT_KO;
                        strSendStatusText = I18nService.getLocalizedString( MESSAGE_STATUS_SENT_KO, Locale.FRANCE );
                    }

                    changeTicketStatus( ticket, nSendStatusCode, strSendStatusText );
                }
            }
        }
    }

    @Override
    public String getTitle( Locale locale )
    {
        return I18nService.getLocalizedString( MESSAGE_SEND_TICKET, locale );
    }

    /**
     * Retrieves the company depending on the user GUID
     *
     * @param strGuid
     *            the user GUID
     * @return the company if it exists, {@code null} otherwise
     */
    private static String getCompany( String strGuid )
    {
        String strCompany = null;

        if ( strGuid != null )
        {
            String[] companies = AppPropertiesService.getProperty( PROPERTY_REST_COMPANIES ).split( SEPARATOR );

            for ( String strCompanyFromProperties : companies )
            {
                String[] users = AppPropertiesService.getProperty( PROPERTY_REST_USERS_COMPANY +
                        strCompanyFromProperties ).split( SEPARATOR );

                for ( String user : users )
                {
                    if ( strGuid.equals( user ) )
                    {
                        strCompany = strCompanyFromProperties;

                        break;
                    }
                }

                if ( strCompany != null )
                {
                    break;
                }
            }
        }

        return strCompany;
    }

    /**
     * Retrieves the end point depending on the specified company
     *
     * @param strCompany
     *            the company
     * @return the end point
     */
    private static String getEndpoint( String strCompany )
    {
        return AppPropertiesService.getProperty( PROPERTY_REST_ENDPOINT_COMPANY + strCompany );
    }

    /**
     * Builds the error message used in exceptions
     *
     * @param ticket
     *            the ticket in error
     * @param strMessage
     *            the message to print
     * @return the error message
     */
    private static String buildErrorMessage( Ticket ticket, String strMessage )
    {
        // TODO : replace ticket id with ticket reference
        return MessageFormat.format( ERROR_SENDING_TICKET, ticket.getId(  ), strMessage );
    }

    /**
     * Changes the ticket status
     * 
     * @param ticket
     *            the ticket to update
     * @param nStatusCode
     *            the status code
     * @param strStatusText
     *            the status text
     */
    private void changeTicketStatus( Ticket ticket, int nStatusCode, String strStatusText )
    {
        ticket.setTicketStatus( nStatusCode );
        ticket.setTicketStatusText( strStatusText );
        TicketHome.update( ticket );
    }

    /**
     * Write a ticket into a JSON Object
     *
     * @param json
     *            The JSON Object
     * @param ticket
     *            The ticket
     */
    private static void addTicketJson( JSONObject json, Ticket ticket )
    {
        JSONObject jsonTicket = new JSONObject(  );
        jsonTicket.accumulate( KEY_TICKET_ID, ticket.getId(  ) );
        // TODO : replace the empty String with ticket reference
        jsonTicket.accumulate( KEY_TICKET_REFERENCE, EMPTY_STRING );

        JSONObject jsonUser = new JSONObject(  );
        jsonUser.accumulate( KEY_USER_ID, ticket.getGuid(  ) );
        jsonUser.accumulate( KEY_USER_TITLE, ticket.getIdUserTitle(  ) );
        jsonUser.accumulate( KEY_USER_FIRST_NAME, ticket.getFirstname(  ) );
        jsonUser.accumulate( KEY_USER_LAST_NAME, ticket.getLastname(  ) );
        jsonUser.accumulate( KEY_USER_EMAIL, ticket.getEmail(  ) );
        jsonUser.accumulate( KEY_USER_FIXED_PHONE_NUMBER, ticket.getFixedPhoneNumber(  ) );
        jsonUser.accumulate( KEY_USER_MOBILE_PHONE_NUMBER, ticket.getMobilePhoneNumber(  ) );
        jsonTicket.accumulate( KEY_USER, jsonUser );

        jsonTicket.accumulate( KEY_TICKET_TYPE, ticket.getIdTicketType(  ) );
        jsonTicket.accumulate( KEY_TICKET_DOMAIN, ticket.getIdTicketDomain(  ) );
        jsonTicket.accumulate( KEY_TICKET_CATEGORY, ticket.getIdTicketCategory(  ) );
        jsonTicket.accumulate( KEY_TICKET_CONTACT_MODE, ticket.getIdContactMode(  ) );
        jsonTicket.accumulate( KEY_TICKET_STATUS, ticket.getTicketStatus(  ) );
        jsonTicket.accumulate( KEY_TICKET_COMMENT, ticket.getTicketComment(  ) );

        JSONArray jsonExtraFields = new JSONArray(  );

        List<Response> listExtraFields = ticket.getListResponse(  );

        if ( listExtraFields != null )
        {
            for ( Response extraField : listExtraFields )
            {
                JSONObject jsonExtraField = new JSONObject(  );
                File file = extraField.getFile(  );

                Entry entry = extraField.getEntry(  );

                if ( entry != null )
                {
                    jsonExtraField.accumulate( KEY_EXTRA_FIELDS_FIELD, entry.getCode(  ) );
                }
                else
                {
                    jsonExtraField.accumulate( KEY_EXTRA_FIELDS_FIELD, EMPTY_STRING );
                }

                Field field = extraField.getField(  );

                if ( field != null )
                {
                    jsonExtraField.accumulate( KEY_EXTRA_FIELDS_VALUE, field.getCode(  ) );
                }
                else
                {
                    if ( file != null )
                    {
                        jsonExtraField.accumulate( KEY_EXTRA_FIELDS_VALUE,
                            Base64.encode( file.getPhysicalFile(  ).getValue(  ) ) );
                    }
                    else
                    {
                        jsonExtraField.accumulate( KEY_EXTRA_FIELDS_VALUE, EMPTY_STRING );
                    }
                }

                if ( file != null )
                {
                    JSONArray metadata = new JSONArray(  );

                    JSONObject mimeTypeMetadata = new JSONObject(  );
                    mimeTypeMetadata.accumulate( KEY_EXTRA_FIELDS_METADATA_NAME, KEY_EXTRA_FIELDS_METADATA_MIMETYPE );
                    mimeTypeMetadata.accumulate( KEY_EXTRA_FIELDS_METADATA_VALUE, file.getMimeType(  ) );
                    metadata.add( mimeTypeMetadata );

                    JSONObject fileNameMetadata = new JSONObject(  );
                    fileNameMetadata.accumulate( KEY_EXTRA_FIELDS_METADATA_NAME, KEY_EXTRA_FIELDS_METADATA_FILENAME );
                    fileNameMetadata.accumulate( KEY_EXTRA_FIELDS_METADATA_VALUE, file.getTitle(  ) );
                    metadata.add( fileNameMetadata );

                    jsonExtraField.accumulate( KEY_EXTRA_FIELDS_METADATA, metadata );
                }

                jsonExtraFields.add( jsonExtraField );
            }
        }

        jsonTicket.accumulate( KEY_EXTRA_FIELDS, jsonExtraFields );

        json.accumulate( KEY_TICKET, jsonTicket );
    }

    /**
     * This class represents the response of the request that contains the
     * ticket
     *
     */
    private class ResponseContent
    {
        // Constants for JSON message
        private static final String KEY_STATUS = "status";
        private static final String KEY_MESSAGE = "message";

        // Other constants
        public static final String STATUS_CORRECT = "OK";

        // Class attributes
        private final String _strStatus;
        private final String _strMessage;

        /**
         * Constructor
         *
         * @param response
         *            the String containing the response
         */
        ResponseContent( String response )
        {
            JSONObject jsonResponse = JSONObject.fromObject( response );
            _strStatus = jsonResponse.getString( KEY_STATUS );
            _strMessage = jsonResponse.getString( KEY_MESSAGE );
        }

        /**
         * Gives the status
         *
         * @return the status
         */
        public String getStatus(  )
        {
            return _strStatus;
        }

        /**
         * Give the message
         *
         * @return the message
         */
        public String getMessage(  )
        {
            return _strMessage;
        }
    }
}
