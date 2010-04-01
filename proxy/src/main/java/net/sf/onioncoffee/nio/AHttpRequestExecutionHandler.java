package net.sf.onioncoffee.nio;

import java.io.IOException;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.protocol.HttpContext;

public class AHttpRequestExecutionHandler<T> implements HttpRequestExecutionHandler {
    private static final String PRIMARY_ATTRIBUTE_NAME = AHttpRequestExecutionHandler.class.getName()+ ".primary-attribute"; 

    @SuppressWarnings("unchecked")
    public T getPrimaryAttribute(HttpContext context) {
        return (T)context.getAttribute(PRIMARY_ATTRIBUTE_NAME);
    }

    @SuppressWarnings("unchecked")
    public void setPrimaryAttribute(T attribute, HttpContext context) {
        context.setAttribute(PRIMARY_ATTRIBUTE_NAME, attribute);
    }
    
    @Override
    public void finalizeContext(HttpContext context) {
    }

    @Override
    public void handleResponse(HttpResponse response, HttpContext context) throws IOException {
    }

    @Override
    public void initalizeContext(HttpContext context, Object attachment) {
    }

    @Override
    public HttpRequest submitRequest(HttpContext context) {
        return null;
    }

}
