/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.web.rest.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sitewhere.batch.BatchUtils;
import com.sitewhere.batch.marshaling.BatchElementMarshalHelper;
import com.sitewhere.batch.marshaling.BatchOperationMarshalHelper;
import com.sitewhere.instance.spi.microservice.IInstanceManagementMicroservice;
import com.sitewhere.rest.model.batch.request.BatchCommandInvocationRequest;
import com.sitewhere.rest.model.batch.request.InvocationByAssignmentCriteriaRequest;
import com.sitewhere.rest.model.batch.request.InvocationByDeviceCriteriaRequest;
import com.sitewhere.rest.model.search.SearchResults;
import com.sitewhere.rest.model.search.batch.BatchOperationSearchCriteria;
import com.sitewhere.rest.model.search.device.BatchElementSearchCriteria;
import com.sitewhere.schedule.ScheduledJobHelper;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.SiteWhereSystemException;
import com.sitewhere.spi.asset.IAssetManagement;
import com.sitewhere.spi.batch.IBatchElement;
import com.sitewhere.spi.batch.IBatchManagement;
import com.sitewhere.spi.batch.IBatchOperation;
import com.sitewhere.spi.device.IDeviceManagement;
import com.sitewhere.spi.device.command.IDeviceCommand;
import com.sitewhere.spi.error.ErrorCode;
import com.sitewhere.spi.error.ErrorLevel;
import com.sitewhere.spi.scheduling.IScheduleManagement;
import com.sitewhere.spi.scheduling.request.IScheduledJobCreateRequest;
import com.sitewhere.spi.search.ISearchResults;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.parameters.RequestBody;

/**
 * Controller for batch operations.
 * 
 * @author Derek Adams
 */
@Path("/batch")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api(value = "batch")
public class BatchOperations {

    @Inject
    private IInstanceManagementMicroservice<?> microservice;

    /** Static logger instance */
    @SuppressWarnings("unused")
    private static Log LOGGER = LogFactory.getLog(BatchOperations.class);

    /**
     * Get batch operation by token.
     * 
     * @param batchToken
     * @return
     * @throws SiteWhereException
     */
    @GET
    @Path("/{batchToken}")
    @ApiOperation(value = "Get batch operation by unique token")
    public Response getBatchOperationByToken(
	    @ApiParam(value = "Unique token that identifies batch operation", required = true) @PathParam("batchToken") String batchToken)
	    throws SiteWhereException {
	IBatchOperation batch = getBatchManagement().getBatchOperationByToken(batchToken);
	if (batch == null) {
	    throw new SiteWhereSystemException(ErrorCode.InvalidBatchOperationToken, ErrorLevel.ERROR);
	}
	BatchOperationMarshalHelper helper = new BatchOperationMarshalHelper();
	return Response.ok(helper.convert(batch)).build();
    }

    /**
     * List batch operations that match the given criteria.
     * 
     * @param page
     * @param pageSize
     * @return
     * @throws SiteWhereException
     */
    @GET
    @ApiOperation(value = "List batch operations")
    public Response listBatchOperations(
	    @ApiParam(value = "Page number", required = false) @QueryParam("page") @DefaultValue("1") int page,
	    @ApiParam(value = "Page size", required = false) @QueryParam("pageSize") @DefaultValue("100") int pageSize)
	    throws SiteWhereException {
	BatchOperationSearchCriteria criteria = new BatchOperationSearchCriteria(page, pageSize);

	ISearchResults<IBatchOperation> results = getBatchManagement().listBatchOperations(criteria);
	BatchOperationMarshalHelper helper = new BatchOperationMarshalHelper();
	List<IBatchOperation> converted = new ArrayList<IBatchOperation>();
	for (IBatchOperation op : results.getResults()) {
	    converted.add(helper.convert(op));
	}
	return Response.ok(new SearchResults<IBatchOperation>(converted, results.getNumResults())).build();
    }

    /**
     * List batch operation elements that match criteria.
     * 
     * @param operationToken
     * @param includeDevice
     * @param page
     * @param pageSize
     * @return
     * @throws SiteWhereException
     */
    @GET
    @Path("/{operationToken}/elements")
    @ApiOperation(value = "List batch operation elements")
    public Response listBatchOperationElements(
	    @ApiParam(value = "Unique batch operation token", required = true) @PathParam("operationToken") String operationToken,
	    @ApiParam(value = "Include device information", required = false) @QueryParam("includeDevice") @DefaultValue("false") boolean includeDevice,
	    @ApiParam(value = "Page number", required = false) @QueryParam("page") @DefaultValue("1") int page,
	    @ApiParam(value = "Page size", required = false) @QueryParam("pageSize") @DefaultValue("100") int pageSize)
	    throws SiteWhereException {
	IBatchOperation batchOperation = assureBatchOperation(operationToken);
	BatchElementSearchCriteria criteria = new BatchElementSearchCriteria(page, pageSize);
	ISearchResults<IBatchElement> results = getBatchManagement().listBatchElements(batchOperation.getId(),
		criteria);

	BatchElementMarshalHelper helper = new BatchElementMarshalHelper();
	helper.setIncludeDevice(includeDevice);
	List<IBatchElement> converted = new ArrayList<IBatchElement>();
	for (IBatchElement element : results.getResults()) {
	    converted.add(helper.convert(element, getDeviceManagement()));
	}
	return Response.ok(new SearchResults<IBatchElement>(converted, results.getNumResults())).build();
    }

    /**
     * Create a batch command invocation.
     * 
     * @param request
     * @return
     * @throws SiteWhereException
     */
    @POST
    @Path("/command")
    @ApiOperation(value = "Create new batch command invocation")
    public Response createBatchCommandInvocation(@RequestBody BatchCommandInvocationRequest request)
	    throws SiteWhereException {
	IBatchOperation result = getBatchManagement().createBatchCommandInvocation(request);
	BatchOperationMarshalHelper helper = new BatchOperationMarshalHelper();
	return Response.ok(helper.convert(result)).build();
    }

    /**
     * Create a batch operation that invokes a command for all devices that match
     * the given criteria.
     * 
     * @param request
     * @param scheduleToken
     * @return
     * @throws SiteWhereException
     */
    @POST
    @Path("/command/criteria/device")
    @ApiOperation(value = "Create batch command operation based on device criteria")
    public Response createInvocationsByDeviceCriteria(@RequestBody InvocationByDeviceCriteriaRequest request,
	    @ApiParam(value = "Schedule token", required = false) @QueryParam("scheduleToken") String scheduleToken)
	    throws SiteWhereException {
	if (scheduleToken != null) {
	    IScheduledJobCreateRequest job = ScheduledJobHelper
		    .createBatchCommandInvocationJobForDeviceCriteria(request, scheduleToken);
	    return Response.ok(getScheduleManagement().createScheduledJob(job)).build();
	} else {
	    // Resolve tokens for devices matching criteria.
	    List<String> deviceTokens = BatchUtils.resolveDeviceTokensForDeviceCriteria(request, getDeviceManagement(),
		    getAssetManagement());

	    // Create batch command invocation.
	    BatchCommandInvocationRequest invoke = new BatchCommandInvocationRequest();
	    invoke.setToken(request.getToken());
	    invoke.setCommandToken(request.getCommandToken());
	    invoke.setParameterValues(request.getParameterValues());
	    invoke.setDeviceTokens(deviceTokens);

	    IBatchOperation result = getBatchManagement().createBatchCommandInvocation(invoke);
	    BatchOperationMarshalHelper helper = new BatchOperationMarshalHelper();
	    return Response.ok(helper.convert(result)).build();
	}
    }

    /**
     * Create batch command invocation based on device assignment criteria.
     * 
     * @param request
     * @param scheduleToken
     * @return
     * @throws SiteWhereException
     */
    @POST
    @Path("/command/criteria/assignment")
    @ApiOperation(value = "Create batch command invocation based on device assignment criteria")
    public Response createInvocationsByAssignmentCriteria(@RequestBody InvocationByAssignmentCriteriaRequest request,
	    @ApiParam(value = "Schedule token", required = false) @QueryParam("scheduleToken") String scheduleToken)
	    throws SiteWhereException {
	if (scheduleToken != null) {
	    IScheduledJobCreateRequest job = ScheduledJobHelper
		    .createBatchCommandInvocationJobForAssignmentCriteria(request, scheduleToken);
	    return Response.ok(getScheduleManagement().createScheduledJob(job)).build();
	} else {
	    // Resolve tokens for devices matching criteria.
	    List<String> deviceTokens = BatchUtils.resolveDeviceTokensForAssignmentCriteria(request,
		    getDeviceManagement(), getAssetManagement());

	    // Create batch command invocation.
	    BatchCommandInvocationRequest invoke = new BatchCommandInvocationRequest();
	    invoke.setToken(request.getToken());
	    invoke.setCommandToken(request.getCommandToken());
	    invoke.setParameterValues(request.getParameterValues());
	    invoke.setDeviceTokens(deviceTokens);

	    IBatchOperation result = getBatchManagement().createBatchCommandInvocation(invoke);
	    BatchOperationMarshalHelper helper = new BatchOperationMarshalHelper();
	    return Response.ok(helper.convert(result)).build();
	}
    }

    /**
     * Get a device command by unique id. Throw an exception if not found.
     * 
     * @param deviceCommandId
     * @return
     * @throws SiteWhereException
     */
    protected IDeviceCommand assureDeviceCommand(UUID deviceCommandId) throws SiteWhereException {
	IDeviceCommand command = getDeviceManagement().getDeviceCommand(deviceCommandId);
	if (command == null) {
	    throw new SiteWhereSystemException(ErrorCode.InvalidDeviceCommandToken, ErrorLevel.ERROR);
	}
	return command;
    }

    /**
     * Verify that the batch operation associated with the given token exists.
     * 
     * @param token
     * @return
     * @throws SiteWhereException
     */
    protected IBatchOperation assureBatchOperation(String token) throws SiteWhereException {
	IBatchOperation batchOperation = getBatchManagement().getBatchOperationByToken(token);
	if (batchOperation == null) {
	    throw new SiteWhereSystemException(ErrorCode.InvalidBatchOperationToken, ErrorLevel.ERROR);
	}
	return batchOperation;
    }

    private IDeviceManagement getDeviceManagement() {
	return getMicroservice().getDeviceManagementApiChannel();
    }

    private IAssetManagement getAssetManagement() {
	return getMicroservice().getAssetManagementApiChannel();
    }

    protected IBatchManagement getBatchManagement() {
	return getMicroservice().getBatchManagementApiChannel();
    }

    protected IScheduleManagement getScheduleManagement() {
	return getMicroservice().getScheduleManagementApiChannel();
    }

    protected IInstanceManagementMicroservice<?> getMicroservice() {
	return microservice;
    }
}