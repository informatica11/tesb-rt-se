package org.talend.esb.job.controller.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.handler.MessageContext;

import org.talend.esb.job.controller.internal.util.DOM4JMarshaller;

//@javax.jws.WebService(name = "TalendJobAsWebService", targetNamespace = "http://talend.org/esb/service/job")
//@javax.jws.soap.SOAPBinding(parameterStyle = javax.jws.soap.SOAPBinding.ParameterStyle.BARE, style = javax.jws.soap.SOAPBinding.Style.DOCUMENT, use = javax.jws.soap.SOAPBinding.Use.LITERAL)
@javax.xml.ws.ServiceMode(value = javax.xml.ws.Service.Mode.PAYLOAD)
@javax.xml.ws.WebServiceProvider()
public abstract class ESBProviderBase implements javax.xml.ws.Provider<javax.xml.transform.Source> {
    private static final Logger LOG = Logger.getLogger(ESBProviderBase.class.getName());
    
    public static final String REQUEST_PAYLOAD = "PAYLOAD";
    public static final String REQUEST_SAM_PROPS = "SAM-PROPS";
    public static final String REQUEST_SL_PROPS = "SL-PROPS";

    private final Map<String, RuntimeESBProviderCallback> callbacks =
            new ConcurrentHashMap<String, RuntimeESBProviderCallback>();

    @javax.annotation.Resource
    private javax.xml.ws.WebServiceContext context;

    //@javax.jws.WebMethod(exclude=true)
	public final Source invoke(Source request) {
        QName operationQName = (QName)context.getMessageContext().get(MessageContext.WSDL_OPERATION);
        LOG.info("Invoke operation '" + operationQName + "'");
        RuntimeESBProviderCallback esbProviderCallback =
            getESBProviderCallback(operationQName.getLocalPart());
        if (esbProviderCallback == null) {
            throw new RuntimeException("Handler for operation " + operationQName + " cannot be found");
        }
        try {
            Object result = esbProviderCallback.invoke(
                DOM4JMarshaller.sourceToDocument(request));

            // oneway
            if (result == null) {
                return null;
            }
            
            if (result instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>)result;

                @SuppressWarnings("unchecked")
                Map<String, String> samProps =
                    (Map<String, String>)map.get(REQUEST_SAM_PROPS);
                if (samProps != null) {
                    LOG.info("SAM custom properties received: " + samProps);
                    //System.out.println("Provider/" + "SAM custom properties received: " + samProps);
                    processCustomProp(samProps);
                }

                return processResult(map.get(REQUEST_PAYLOAD));
            } else {
                return processResult(result);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    protected abstract void processCustomProp(Map<String, String> samProps);

    private Source processResult(Object result) {
        if (result instanceof org.dom4j.Document) {
            return DOM4JMarshaller.documentToSource((org.dom4j.Document)result);
        } else if (result instanceof RuntimeException) {
            throw (RuntimeException)result;
        } else if (result instanceof Throwable) {
            throw new RuntimeException((Throwable)result);
        } else {
            throw new RuntimeException(
                "Provider return incompatible object: " + result.getClass().getName());
        }
    }

    public RuntimeESBProviderCallback createESBProviderCallback(String operationName, boolean isRequestResponse) {
        if(callbacks.get(operationName) != null) {
            throw new RuntimeException(
                "Operation '" + operationName + "' already registered");
        }
        RuntimeESBProviderCallback esbProviderCallback =
            new RuntimeESBProviderCallback(isRequestResponse);
        callbacks.put(operationName, esbProviderCallback);

        return esbProviderCallback;
    }

    public RuntimeESBProviderCallback getESBProviderCallback(String operationName) {
        return callbacks.get(operationName);
    }

    public boolean destroyESBProviderCallback(String operationName) {
        callbacks.remove(operationName);
        return callbacks.isEmpty();
    }

    protected boolean isOperationRequestResponse(String operationName) {
        // is better way to get communication style?
        return (null != context.getMessageContext().get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS));
    }

}