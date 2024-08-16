package com.ibm.webmethods.observability;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.softwareag.is.interceptor.HttpInterceptorException;
import com.softwareag.is.interceptor.HttpInterceptorIFC;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

public class OtelHttpInterceptor implements HttpInterceptorIFC {

	private final Logger logger = LoggerFactory.getLogger(OtelHttpInterceptor.class);

	private final ThreadLocal<Scope> scopeThreadLocal;
	private final ThreadLocal<Scope> scopeExtractedContextThreadLocal;
	private ThreadLocal<Boolean>  skipURL;

	public OtelHttpInterceptor() {
		this.scopeThreadLocal = new ThreadLocal<>();
		this.scopeExtractedContextThreadLocal = new ThreadLocal<>();
		this.skipURL = new ThreadLocal<Boolean>();
	}

	@Override
	public void postProcess(int responseCode, String responseMessage, Map<String, String> headers, byte[] bytes,
			String correlationID) {
		if (Helper.isOtelAgentDisabled)
			return;

		if (skipURL.get())
			return;
		Helper.postProcess(logger, responseCode, responseMessage, headers, correlationID, this.scopeThreadLocal,
				this.scopeExtractedContextThreadLocal);

	}

	@SuppressWarnings("rawtypes")
	@Override
	public void preProcess(String requestURL, String requestType, String httpVersion, Map headers, byte[] streamBytes,
			String correlationID) throws HttpInterceptorException {

		if (Helper.isOtelAgentDisabled) {
			if (logger.isDebugEnabled())
				logger.debug("TID={} OTEL Java Agent is deactivated. Check env. variable OTEL_JAVAAGENT_ENABLED ",
						Thread.currentThread().getId());
			return;
		}
		if (logger.isDebugEnabled())
			logger.debug(
					"TID={} preProcess: requestType={},requestURL={}, httpVersion={}, headers=[{}], correlationID={}",
					Thread.currentThread().getId(), requestType, requestURL, httpVersion, headers, correlationID);

		skipURL.set(Helper.isSkipURL(logger, requestURL));
		if (skipURL.get()) {
			if (logger.isDebugEnabled())
				logger.debug("TID={}, requestURL {} does not match context: {}", Thread.currentThread().getId(),
						requestURL, Helper.context);
			return;
		}

		checkPropagatedContext(headers);

		String spanName = "wm-http-inbound";
		if (requestURL != null && requestURL.length() > 0) {
			int i = requestURL.indexOf('?');
			// Do not include query parametes in span name.
			spanName = i > 0 ? String.format("%s %s", requestType, requestURL.substring(0, i))
					: String.format("%s %s", requestType, requestURL);
		}

		Span span = Helper.tracer.spanBuilder(spanName).startSpan();

		// put the span into the current Context
		Scope scope = span.makeCurrent();
		this.scopeThreadLocal.set(scope);

		// do something
		span.setAttribute("http.request.method", requestType);
		span.setAttribute("http.request.url", requestURL);

		if (logger.isDebugEnabled())
			logger.debug("TID={}, span={}, scop={}", Thread.currentThread().getId(), span.getSpanContext().getTraceId(),
					span.getSpanContext().getSpanId(), span, scope);

	}

	/**
	 * 
	 * @param headers
	 */
	@SuppressWarnings("rawtypes")
	private void checkPropagatedContext(Map headers) {
		TextMapGetter<Map> getter = new TextMapGetter<Map>() {
			@Override
			public String get(Map carrier, String key) {
				if (carrier != null && carrier.containsKey(key)) {
					return Optional.ofNullable(carrier.get(key)).map(Object::toString).orElse(null);
				}
				return null;
			}

			@SuppressWarnings("unchecked")
			@Override
			public Iterable<String> keys(Map carrier) {
				return carrier.keySet();
			}
		};

		// Extract the SpanContext and other elements from the request.
		Context extractedContext = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
				.extract(Context.current(), headers, getter);
		Scope serverScope = extractedContext.makeCurrent();

		this.scopeExtractedContextThreadLocal.set(serverScope);

	}
}