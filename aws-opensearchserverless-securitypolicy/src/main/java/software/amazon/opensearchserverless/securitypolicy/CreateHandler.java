package software.amazon.opensearchserverless.securitypolicy;

import software.amazon.awssdk.services.opensearchserverless.OpenSearchServerlessClient;
import software.amazon.awssdk.services.opensearchserverless.model.ConflictException;
import software.amazon.awssdk.services.opensearchserverless.model.CreateSecurityPolicyRequest;
import software.amazon.awssdk.services.opensearchserverless.model.CreateSecurityPolicyResponse;
import software.amazon.awssdk.services.opensearchserverless.model.InternalServerException;
import software.amazon.awssdk.services.opensearchserverless.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import com.amazonaws.util.StringUtils;


public class CreateHandler extends BaseHandlerStd {

    private final ReadHandler readHandler;

    public CreateHandler() {
        super();
        readHandler = new ReadHandler(getOpenSearchServerlessClient());
    }

    public CreateHandler(OpenSearchServerlessClient openSearchServerlessClient) {
        super(openSearchServerlessClient);
        readHandler = new ReadHandler(getOpenSearchServerlessClient());
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<OpenSearchServerlessClient> proxyClient,
        final Logger logger) {

        ResourceModel model = request.getDesiredResourceState();
        if (StringUtils.isNullOrEmpty(model.getName())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Name cannot be empty");
        }
        if (StringUtils.isNullOrEmpty(model.getType())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Type cannot be empty");
        }
        if (StringUtils.isNullOrEmpty(model.getPolicy())) {
            return ProgressEvent.failed(model, callbackContext, HandlerErrorCode.InvalidRequest,
                "Policy cannot be empty");
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress ->
                proxy.initiate("AWS-OpenSearchServerless-SecurityPolicy::Create", proxyClient,
                        progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToCreateRequest)
                    .makeServiceCall((awsRequest, cbClient) ->
                        createSecurityPolicy(awsRequest, cbClient, logger))
                    .progress()
            )
            .then(progress ->
                readHandler.handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private CreateSecurityPolicyResponse createSecurityPolicy(
        final CreateSecurityPolicyRequest createSecurityPolicyRequest,
        final ProxyClient<OpenSearchServerlessClient> proxyClient,
        final Logger logger) {

        final CreateSecurityPolicyResponse createSecurityPolicyResponse;
        try {
            logger.log(String.format("Sending create security policy request: %s",createSecurityPolicyRequest));
            createSecurityPolicyResponse = proxyClient.injectCredentialsAndInvokeV2(createSecurityPolicyRequest,
                proxyClient.client()::createSecurityPolicy);
        } catch (ConflictException e) {
            throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, String.format("Name:%s, Type:%s",
                    createSecurityPolicyRequest.name(), createSecurityPolicyRequest.typeAsString()), e);
        } catch (ValidationException e) {
            throw new CfnInvalidRequestException(createSecurityPolicyRequest.toString() + ", " + e.getMessage(), e);
        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException("CreateSecurityPolicy", e);
        }
        logger.log(String.format("%s successfully created. response: %s", ResourceModel.TYPE_NAME,
            createSecurityPolicyResponse));
        return createSecurityPolicyResponse;
    }
}
